package com.syang.placitum.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Every number that touches balance. Magic numbers in the simulation are forbidden - see
 * CLAUDE.md - because none of these can be guessed correctly up front and tuning them from
 * source means a rebuild per attempt.
 *
 * <p>Only the keys M0 actually reads are defined here. Later milestones add their sections
 * rather than smuggling constants into the modules.
 *
 * <p><b>Probabilities are per step.</b> One game day is 24000 ticks = 120 steps, so a
 * per-step chance of 0.02 fires about 2.4 times a day. Every probability key must carry its
 * per-day conversion in the comment.
 */
public final class PlacitumConfig {

    public static final ModConfigSpec SPEC;

    // [settlement]
    public static final ModConfigSpec.IntValue CLAIM_RADIUS_CHUNKS;
    public static final ModConfigSpec.IntValue REGISTER_MIN_BEDS;
    public static final ModConfigSpec.IntValue REGISTER_MIN_RESIDENTS;
    public static final ModConfigSpec.IntValue MIN_SETTLEMENT_DISTANCE;

    // [lifecycle]
    public static final ModConfigSpec.IntValue PROMOTE_RADIUS;
    public static final ModConfigSpec.IntValue DEMOTE_RADIUS;
    public static final ModConfigSpec.IntValue DEMOTE_DELAY_TICKS;
    public static final ModConfigSpec.IntValue REPLAY_OPS_PER_TICK;
    public static final ModConfigSpec.IntValue MAX_MATERIALIZED_RESIDENTS;

    // [simulation]
    public static final ModConfigSpec.IntValue STEP_TICKS;
    public static final ModConfigSpec.LongValue MAX_CATCHUP_TICKS;
    public static final ModConfigSpec.LongValue TICK_BUDGET_NANOS;

    // [population]
    public static final ModConfigSpec.IntValue CONSUMPTION_PER_HEAD;
    public static final ModConfigSpec.IntValue YIELD_RATE;
    public static final ModConfigSpec.IntValue SAFETY_WINDOW_DAYS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Settlement registration and claim size.").push("settlement");
        CLAIM_RADIUS_CHUNKS = b.defineInRange("claimRadiusChunks", 5, 1, 32);
        REGISTER_MIN_BEDS = b.defineInRange("registerMinBeds", 4, 0, 256);
        REGISTER_MIN_RESIDENTS = b.defineInRange("registerMinResidents", 3, 0, 256);
        MIN_SETTLEMENT_DISTANCE = b.comment("Blocks. Registration is refused inside another claim.")
                .defineInRange("minSettlementDistance", 96, 0, 4096);
        b.pop();

        b.comment("Promote / demote. demoteRadius must stay larger than promoteRadius:",
                        "equal values make a player standing on the boundary thrash the whole village.")
                .push("lifecycle");
        PROMOTE_RADIUS = b.defineInRange("promoteRadius", 96, 16, 512);
        DEMOTE_RADIUS = b.defineInRange("demoteRadius", 144, 16, 1024);
        DEMOTE_DELAY_TICKS = b.defineInRange("demoteDelayTicks", 200, 0, 12000);
        REPLAY_OPS_PER_TICK = b.defineInRange("replayOpsPerTick", 4, 1, 256);
        MAX_MATERIALIZED_RESIDENTS = b.defineInRange("maxMaterializedResidents", 60, 1, 512);
        b.pop();

        b.comment("Deterministic catch-up simulation.").push("simulation");
        STEP_TICKS = b.comment("Ticks per simulation step. 200 = 10 seconds = 1/120 of a game day.")
                .defineInRange("stepTicks", 200, 20, 24000);
        MAX_CATCHUP_TICKS = b.comment("Longest absence actually simulated. 72000 = 3 game days.",
                        "Anything beyond this is summarised into the chronicle instead.")
                .defineInRange("maxCatchupTicks", 72000L, 1200L, 1728000L);
        TICK_BUDGET_NANOS = b.comment("Simulation work allowed per server tick. 500000 = 0.5ms.")
                .defineInRange("tickBudgetNanos", 500000L, 50000L, 20000000L);
        b.pop();

        b.comment("Population and food.").push("population");
        CONSUMPTION_PER_HEAD = b.comment("Food eaten per resident per step.")
                .defineInRange("consumptionPerHead", 1, 0, 64);
        YIELD_RATE = b.comment("Food produced per farmer per step.")
                .defineInRange("yieldRate", 3, 0, 64);
        SAFETY_WINDOW_DAYS = b.comment("Length of the combat-casualty ring buffer, in game days.")
                .defineInRange("safetyWindowDays", 7, 1, 64);
        b.pop();

        SPEC = b.build();
    }

    private PlacitumConfig() {}

    /** One game day in simulation steps. Use this when reading a per-step probability. */
    public static int stepsPerDay() {
        return 24000 / STEP_TICKS.get();
    }
}
