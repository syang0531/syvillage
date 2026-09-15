package com.syang.placitum.sim;

import com.syang.placitum.config.PlacitumConfig;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;

/**
 * The balance numbers one simulation run needs, resolved once at the edge.
 *
 * <p>The modules take values rather than reaching for config handles. That keeps the
 * deterministic core a pure function of (state, params, seed), which is what makes the
 * equivalence test possible at all - a config handle cannot be read before the config file has
 * loaded, so a simulation that reads config directly cannot be unit tested.
 */
public record SimParams(
        int stepTicks,
        long maxCatchupTicks,
        int consumptionPerHead,
        int yieldRate,
        boolean hostilesExist) {

    /**
     * Reads the values, including whether this world has anything hostile in it.
     *
     * <p>Difficulty has to come in as a value like everything else. On Peaceful no hostile mob
     * ever spawns, so a real raid cannot happen - and a virtual raid that kills villagers anyway
     * would be the two halves of the game giving different answers in the most visible way
     * there is. docs/defense.md rules that out, and a player would notice immediately.
     */
    public static SimParams fromConfig(Level level) {
        return new SimParams(
                PlacitumConfig.STEP_TICKS.get(),
                PlacitumConfig.MAX_CATCHUP_TICKS.get(),
                PlacitumConfig.CONSUMPTION_PER_HEAD.get(),
                PlacitumConfig.YIELD_RATE.get(),
                level.getDifficulty() != Difficulty.PEACEFUL);
    }

    /** Defaults matching the shipped config, for tests and for headless tooling. */
    public static SimParams defaults() {
        return new SimParams(200, 72000L, 1, 3, true);
    }

    /** One game day in steps. Per-step probabilities have to be read against this. */
    public int stepsPerDay() {
        return 24000 / stepTicks;
    }
}
