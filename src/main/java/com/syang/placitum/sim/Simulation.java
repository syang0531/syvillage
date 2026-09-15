package com.syang.placitum.sim;

import com.syang.placitum.data.EntryType;
import com.syang.placitum.Placitum;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.data.SimClock;
import com.syang.placitum.sim.module.ConsumptionModule;
import com.syang.placitum.sim.module.ProductionModule;
import com.syang.placitum.sim.module.ThreatModule;
import com.syang.placitum.store.SettlementMut;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.RandomSource;

/**
 * Deterministic catch-up.
 *
 * <p>The invariant the whole design leans on: one catch-up of 1000 ticks and ten catch-ups of
 * 100 ticks must produce identical state. That holds only if time is accounted in whole steps
 * and the remainder is carried, never discarded.
 */
public final class Simulation {

    private static final List<SimModule> MODULES = buildModules();

    private Simulation() {}

    private static List<SimModule> buildModules() {
        List<SimModule> modules = new ArrayList<>();
        modules.add(new ProductionModule());
        modules.add(new ConsumptionModule());
        modules.add(new ThreatModule());
        modules.sort(Comparator.comparingInt(SimModule::order));
        return List.copyOf(modules);
    }

    public static List<SimModule> modules() {
        return MODULES;
    }

    /** SplitMix64 finalizer. Mixes, unlike XOR, which merely overlaps. */
    public static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * The only source of randomness the simulation may use.
     *
     * <p>Both UUID words are mixed separately: {@code UUID.hashCode()} folds 128 bits into 32
     * and would collide across settlements. The step is mixed in with the golden-ratio
     * constant so consecutive steps do not produce adjacent streams.
     */
    public static RandomSource rngFor(long worldSeed, UUID settlementId, long step) {
        long h = mix64(worldSeed)
                ^ mix64(settlementId.getMostSignificantBits())
                ^ mix64(settlementId.getLeastSignificantBits() * 31L + 17L);
        return RandomSource.create(mix64(h + step * 0x9E3779B97F4A7C15L));
    }

    /**
     * Per-module stream, so that a module drawing one extra number does not shift every module
     * after it. Without this, any balance tweak rewrites a settlement's entire history.
     */
    private static RandomSource rngForModule(long worldSeed, UUID id, long step, SimModule module) {
        return rngFor(worldSeed, id, step * 100L + module.order());
    }

    public static void simStep(long worldSeed, SettlementMut settlement, SimParams params) {
        for (SimModule module : MODULES) {
            module.step(settlement, params,
                    rngForModule(worldSeed, settlement.id(), settlement.simStep(), module));
        }
    }

    /** Runs to completion. For commands and tests, where one long frame does not matter. */
    public static Settlement catchUp(long worldSeed, Settlement settlement, SimParams params, long now) {
        SettlementMut mut = SettlementMut.of(settlement);
        catchUp(worldSeed, mut, params, now, Long.MAX_VALUE);
        return mut.freeze();
    }

    /**
     * Advances the settlement towards {@code now}, stopping when the deadline passes.
     *
     * @return true when there is nothing left to catch up
     */
    public static boolean catchUp(long worldSeed, SettlementMut settlement, SimParams params,
            long now, long deadlineNanos) {
        int stepTicks = params.stepTicks();
        long maxCatchup = params.maxCatchupTicks();

        // Time a settlement spends embodied is not virtual time - the entities are living it,
        // in the world, and write-back collects the result. Simulating it anyway skips every
        // materialized resident in the production modules while settlement-wide consumption
        // still counts them, so the stock drains for no reason.
        //
        // The guard lives here rather than at the call sites because the call sites are exactly
        // what kept getting it wrong: promote had the ordering right from the start, and the
        // damage came from rebind, from chunk unload, and from /placitum info - three paths
        // nobody thought of as simulation entry points.
        if (settlement.anyMaterialized()) {
            skipWholeSteps(settlement, now, stepTicks);
            return true;
        }

        // A clock ahead of the world means something set it there - /placitum tick used to.
        // Left alone the settlement sleeps until game time catches up, which looks exactly like
        // the simulation having died.
        if (settlement.lastSimTick() > now) {
            Placitum.LOGGER.warn("Settlement clock was {} tick(s) ahead of the world; resetting",
                    settlement.lastSimTick() - now);
            settlement.clock = new SimClock(now, settlement.simStep());
            return true;
        }

        long elapsed = now - settlement.lastSimTick();
        if (elapsed > maxCatchup) {
            // Skip in whole steps so the remainder never gets mixed into the skipped span.
            long skipped = (elapsed - maxCatchup) / stepTicks * stepTicks;
            if (skipped > 0) {
                settlement.clock = settlement.clock.skipped(skipped);
                summarizeGap(settlement, skipped);
            }
        }

        while (now - settlement.lastSimTick() >= stepTicks) {
            if (System.nanoTime() >= deadlineNanos) {
                return false;
            }
            simStep(worldSeed, settlement, params);
            settlement.clock = settlement.clock.advanced(1, stepTicks);
        }
        return true;
    }

    /** Advances the clock without simulating, keeping the step boundary intact. */
    private static void skipWholeSteps(SettlementMut settlement, long now, int stepTicks) {
        long whole = (now - settlement.lastSimTick()) / stepTicks * stepTicks;
        if (whole > 0) {
            settlement.clock = settlement.clock.skipped(whole);
        }
    }

    /**
     * A long absence becomes a sentence, not a simulation.
     *
     * <p>Simulating months of game time would cost more than it is worth and produce a wall of
     * events nobody reads. A summary is both cheaper and better as fiction.
     */
    private static void summarizeGap(SettlementMut settlement, long skippedTicks) {
        long days = skippedTicks / 24000L;
        settlement.record(EntryType.GAP, settlement.identity.name(),
                "Quiet for about " + Math.max(1, days) + " day(s) while nobody was near.");
    }

    public static int stepsPending(Settlement settlement, SimParams params, long now) {
        long elapsed = Math.max(0, now - settlement.lastSimTick());
        long capped = Math.min(elapsed, params.maxCatchupTicks());
        return (int) (capped / params.stepTicks());
    }
}
