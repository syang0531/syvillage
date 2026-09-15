package com.syang.placitum.sim.module;

import com.syang.placitum.Placitum;
import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.AlertState;
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
        // A vanilla raid already running owns the outcome; rolling our own on top would punish
        // the same village twice for the same night.
        if (settlement.alert() != AlertState.PEACE) {
            return;
        }
        if (settlement.population() == 0) {
            return;
        }
        if (rng.nextDouble() >= PlacitumConfig.RAID_CHANCE_PER_STEP.get()) {
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
