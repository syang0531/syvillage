package com.syang.placitum.sim;

import com.syang.placitum.config.PlacitumConfig;

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
        int yieldRate) {

    public static SimParams fromConfig() {
        return new SimParams(
                PlacitumConfig.STEP_TICKS.get(),
                PlacitumConfig.MAX_CATCHUP_TICKS.get(),
                PlacitumConfig.CONSUMPTION_PER_HEAD.get(),
                PlacitumConfig.YIELD_RATE.get());
    }

    /** Defaults matching the shipped config, for tests and for headless tooling. */
    public static SimParams defaults() {
        return new SimParams(200, 72000L, 1, 3);
    }

    /** One game day in steps. Per-step probabilities have to be read against this. */
    public int stepsPerDay() {
        return 24000 / stepTicks;
    }
}
