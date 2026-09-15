package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Simulation time.
 *
 * <p>{@code lastSimTick} is the boundary of the last completed step, NOT the time of the last
 * catch-up call. Setting it to "now" discards the {@code elapsed % STEP} remainder and makes
 * the slice-independence invariant impossible to satisfy - see docs/simulation.md.
 */
public record SimClock(long lastSimTick, long simStep) {

    public static final Codec<SimClock> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("last_sim_tick").forGetter(SimClock::lastSimTick),
            Codec.LONG.fieldOf("sim_step").forGetter(SimClock::simStep)
    ).apply(i, SimClock::new));

    public static SimClock startingAt(long gameTime) {
        return new SimClock(gameTime, 0L);
    }

    public SimClock advanced(int steps, int stepTicks) {
        return new SimClock(lastSimTick + (long) steps * stepTicks, simStep + steps);
    }

    /** Skips time without simulating it - used when an absence exceeds maxCatchupTicks. */
    public SimClock skipped(long ticks) {
        return new SimClock(lastSimTick + ticks, simStep);
    }
}
