package com.syang.placitum.defense;

import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.EntryType;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.store.SettlementMut;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * How a raid ends when nobody is watching.
 *
 * <p>No attempt is made to mimic spawning. There are no entities to spawn, and pretending
 * otherwise would cost far more than it is worth. The formula rolls the result instead, off the
 * same {@link DefenseRating} a real fight is scaled from, so the two cannot drift into
 * disagreeing about which villages are safe.
 */
public final class RaidResolver {

    /** What a raid did. */
    public record Outcome(boolean repelled, int casualties, int threat, int rating) {}

    private RaidResolver() {}

    /**
     * Threat for one step.
     *
     * <p>Lighting halves into this, which is what makes torches a defence rather than decoration.
     */
    public static int threatLevel(Settlement settlement, RandomSource rng) {
        int base = PlacitumConfig.BASE_BIOME_DANGER.get()
                - settlement.defense().lightingScore() / 2;
        return Math.max(1, base + Mth.nextInt(rng, -3, 3));
    }

    /**
     * Odds the settlement holds.
     *
     * <p>docs/defense.md writes this as {@code rng.nextDouble() > threat / rating}, which stops
     * being a probability the moment threat passes rating: at a ratio of 1.25 the comparison can
     * never be true and the village loses every single time. Measured over 500 trials it read
     * "repelled 0.0%", which is a verdict, not a roll.
     *
     * <p>Strength over total strength keeps the same ordering - stronger defences win more - but
     * leaves both outcomes reachable everywhere. An outmatched village usually falls and
     * occasionally holds, which is the difference between a simulation and a sentence.
     *
     * <p>Clamped away from certainty at both ends: no defence should make a settlement
     * untouchable, and none should make it doomed.
     */
    public static double chanceToHold(int rating, int threat) {
        double raw = rating / (double) Math.max(1, rating + threat);
        return Math.clamp(raw, 0.05, 0.95);
    }

    /**
     * Rolls a raid and applies it.
     *
     * <p>Casualties are proportional to how badly the settlement was outmatched, so a
     * well-defended village that loses still loses less. A wipe is not the only losing outcome -
     * see the rout check in ThreatModule.
     */
    public static Outcome resolve(SettlementMut settlement, int threat, RandomSource rng) {
        int rating = DefenseRating.of(settlement.freezeView());
        double ratio = threat / (double) Math.max(1, rating);
        int militia = Math.max(1, DefenseRating.eligibleCount(settlement.freezeView()));

        boolean repelled = rng.nextDouble() < chanceToHold(rating, threat);
        int casualties = (int) Math.round(Math.min(1.0, ratio) * militia
                * (repelled ? 0.3 : 0.7));
        casualties = Math.min(casualties, settlement.population());

        applyCasualties(settlement, casualties, rng);
        if (!repelled) {
            loot(settlement, rng);
        }
        settlement.record(repelled ? EntryType.RAID_REPELLED : EntryType.RAID_LOST,
                settlement.identity.name(),
                (repelled ? "Beat off" : "Overrun by") + " a raid of strength " + threat
                        + " against defences of " + rating
                        + (casualties > 0 ? ", " + casualties + " dead" : ", nobody hurt"));
        return new Outcome(repelled, casualties, threat, rating);
    }

    /**
     * Takes the dead off the roll, with a cause.
     *
     * <p>The cause is the whole point. The original complaint was never that villagers died - it
     * was never knowing why.
     */
    private static void applyCasualties(SettlementMut settlement, int count, RandomSource rng) {
        if (count <= 0) {
            return;
        }
        List<Resident> armed = new ArrayList<>();
        List<Resident> rest = new ArrayList<>();
        for (Resident r : settlement.residents) {
            if (!r.counts()) {
                continue;
            }
            (r.gear().armed() ? armed : rest).add(r);
        }
        // The armed stand in front. Losing the militia first is what makes gear a real cost.
        List<Resident> order = new ArrayList<>(armed);
        order.addAll(rest);

        int gameDay = (int) (settlement.lastSimTick() / 24000L);
        for (int i = 0; i < count && i < order.size(); i++) {
            Resident dead = order.get(i);
            settlement.residents.remove(dead);
            settlement.defense = settlement.defense.withCasualty(gameDay, 1);
            settlement.record(EntryType.DEATH, dead.lineage().fullName(), "killed defending the village");
            // Their weapon goes with them. This is why donated iron matters.
        }
    }

    /**
     * What a lost raid carries off.
     *
     * <p>A fraction rather than a flat amount, so the loss scales with what there was to take -
     * and it is config, because it is a balance number and CLAUDE.md is unambiguous that those
     * do not live in code. It was a hardcoded third until a test run showed a village losing
     * most of its granary to three raids it never saw.
     */
    private static void loot(SettlementMut settlement, RandomSource rng) {
        double fraction = PlacitumConfig.RAID_LOOT_FRACTION.get();
        if (fraction <= 0.0) {
            return;
        }
        List<net.minecraft.world.item.Item> items = new ArrayList<>(settlement.stock.keySet());
        for (net.minecraft.world.item.Item item : items) {
            int have = settlement.stockOf(item);
            settlement.takeStock(item, Math.max(1, (int) Math.round(have * fraction)));
        }
    }
}
