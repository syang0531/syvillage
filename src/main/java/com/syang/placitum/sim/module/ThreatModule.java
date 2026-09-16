package com.syang.placitum.sim.module;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.AlertState;
import com.syang.placitum.defense.AlertMachine;
import com.syang.placitum.defense.RaidResolver;
import com.syang.placitum.sim.SimModule;
import com.syang.placitum.sim.SimParams;
import com.syang.placitum.store.SettlementMut;
import net.minecraft.util.RandomSource;

/**
 * Raids while nobody is there to see them. Order 30.
 *
 * <p>Between production and population on purpose: a raid that kills farmers should be felt by
 * the next step's food, not the last one's.
 */
public class ThreatModule implements SimModule {

    @Override
    public void step(SettlementMut settlement, SimParams params, RandomSource rng) {
        // No entities means nothing is in sight, so the alarm winds down on its own cooldown.
        // The live tick only runs while the settlement has bodies, so without this an alerted
        // village that the player walked away from would stay alerted for ever.
        settlement.defense = AlertMachine.relaxed(settlement.defense, settlement.lastSimTick());

        // An alarm still up means something is being fought already - a vanilla raid, or the
        // aftermath of one. Rolling another on top punishes the same village twice for one night.
        if (settlement.alert() != AlertState.PEACE) {
            return;
        }
        // Nothing in vanilla sends a pillager band after two villagers, and a settlement of
        // three with a defence rating of eight lost five residents to nine raids without being
        // able to replace one of them. A raid is meant to be a reason to build a wall, not a
        // countdown on a village too small to build one.
        if (settlement.population() < params.raidMinPopulation()) {
            return;
        }
        // Peaceful spawns nothing hostile, so no raid could have reached this village in the
        // world. Rolling one anyway would kill people to an attack that cannot exist.
        if (!params.hostilesExist()) {
            return;
        }
        if (rng.nextDouble() >= params.raidChancePerStep()) {
            return;
        }

        int threat = RaidResolver.threatLevel(settlement.freezeView(), rng);
        RaidResolver.Outcome outcome = RaidResolver.resolve(settlement, threat, rng);
        Placitum.LOGGER.debug("  step {}: raid threat {} vs rating {} - {}, {} dead",
                settlement.simStep(), outcome.threat(), outcome.rating(),
                outcome.repelled() ? "repelled" : "lost", outcome.casualties());
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public String name() {
        return "threat";
    }
}
