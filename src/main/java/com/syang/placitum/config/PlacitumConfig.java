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

    // [defense]
    public static final ModConfigSpec.IntValue CURFEW_LEAD_TICKS;
    public static final ModConfigSpec.IntValue ALERT_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue WATCH_RADIUS;
    public static final ModConfigSpec.IntValue WATCH_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue MAX_WATCH_POINTS;
    public static final ModConfigSpec.IntValue ANCHOR_REFRESH_TICKS;
    public static final ModConfigSpec.DoubleValue CURFEW_WALK_SPEED;
    public static final ModConfigSpec.DoubleValue MILITIA_RATIO_CAP;
    public static final ModConfigSpec.DoubleValue ROUT_THRESHOLD;
    public static final ModConfigSpec.DoubleValue RAID_CHANCE_PER_STEP;
    public static final ModConfigSpec.DoubleValue RAID_LOOT_FRACTION;
    public static final ModConfigSpec.IntValue MILITIA_WEIGHT;
    public static final ModConfigSpec.IntValue WALL_WEIGHT;
    public static final ModConfigSpec.IntValue WATCHTOWER_WEIGHT;
    public static final ModConfigSpec.IntValue COMBAT_READY_BONUS;
    public static final ModConfigSpec.IntValue GOLEM_DEFENSE_WEIGHT;
    public static final ModConfigSpec.IntValue BASE_BIOME_DANGER;

    // [population]
    public static final ModConfigSpec.IntValue CONSUMPTION_PER_HEAD;
    public static final ModConfigSpec.IntValue YIELD_RATE;
    public static final ModConfigSpec.IntValue SAFETY_WINDOW_DAYS;
    public static final ModConfigSpec.IntValue TIMBER_RATE;
    public static final ModConfigSpec.DoubleValue BASE_BIRTH_RATE;
    public static final ModConfigSpec.IntValue ELDER_THRESHOLD_DAYS;
    public static final ModConfigSpec.IntValue INFANT_DAYS;
    public static final ModConfigSpec.IntValue CHILD_DAYS;
    public static final ModConfigSpec.IntValue BASE_SAFETY;
    public static final ModConfigSpec.IntValue MIN_SAFETY;
    public static final ModConfigSpec.IntValue SAFETY_RATING_DIVISOR;
    public static final ModConfigSpec.IntValue SAFETY_DEATH_PENALTY;
    public static final ModConfigSpec.IntValue FAMINE_GRACE_STEPS;
    public static final ModConfigSpec.IntValue FAMINE_MORALE_PENALTY;
    public static final ModConfigSpec.IntValue FOOD_WARNING_STEPS;
    public static final ModConfigSpec.DoubleValue FAMINE_DEATH_CHANCE_PER_STEP;
    public static final ModConfigSpec.DoubleValue ELDER_DEATH_CHANCE_PER_STEP;
    public static final ModConfigSpec.BooleanValue ENABLE_AGING;
    public static final ModConfigSpec.IntValue MAX_CELL_SLOPE;
    public static final ModConfigSpec.IntValue SURVEY_SCAN_HEIGHT;
    public static final ModConfigSpec.IntValue WALL_MARGIN_CELLS;
    public static final ModConfigSpec.IntValue PALISADE_HEIGHT;
    public static final ModConfigSpec.IntValue WALL_MIN_POPULATION;
    public static final ModConfigSpec.IntValue BUILD_OP_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue BUILDER_REACH;
    public static final ModConfigSpec.DoubleValue BUILDER_WALK_SPEED;
    public static final ModConfigSpec.DoubleValue BUILDER_WORK_RADIUS;

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

        b.comment("Keeping villagers alive. See docs/defense.md.").push("defense");
        CURFEW_LEAD_TICKS = b.comment("How long before dusk residents start heading home.",
                        "1200 = one minute. Vanilla leaves this far too late, which is how",
                        "villagers end up locked outside with the mobs.")
                .defineInRange("curfewLeadTicks", 1200, 0, 12000);
        ALERT_COOLDOWN_TICKS = b.comment("Quiet time needed before the alarm steps back down.")
                .defineInRange("alertCooldownTicks", 1200, 0, 24000);
        WATCH_RADIUS = b.comment("How far a watch point can see.")
                .defineInRange("watchRadius", 32, 8, 128);
        WATCH_INTERVAL_TICKS = b.comment("Ticks between threat scans.")
                .defineInRange("watchIntervalTicks", 20, 1, 200);
        MAX_WATCH_POINTS = b.comment("Cap on watch points per settlement. Detection cost scales",
                        "with this number, never with the size of the claim.")
                .defineInRange("maxWatchPoints", 6, 1, 32);
        ANCHOR_REFRESH_TICKS = b.comment("How often to re-scan shelters and watch points while a",
                        "settlement has bodies. 6000 = five minutes.")
                .defineInRange("anchorRefreshTicks", 6000, 200, 72000);
        CURFEW_WALK_SPEED = b.comment("Walk speed modifier when heading home under curfew.")
                .defineInRange("curfewWalkSpeed", 0.6D, 0.1D, 2.0D);
        MILITIA_RATIO_CAP = b.comment("Most of the population that may be under arms at once.",
                        "Arming everyone stops production, which is the trade-off.")
                .defineInRange("militiaRatioCap", 0.3D, 0.0D, 1.0D);
        ROUT_THRESHOLD = b.comment("Militia losses past which the settlement breaks and flees.",
                        "A village should be able to be abandoned, not only wiped out.")
                .defineInRange("routThreshold", 0.5D, 0.0D, 1.0D);
        RAID_CHANCE_PER_STEP = b.comment("Chance of a raid per simulation step while virtual.",
                        "PER STEP: one game day is 120 steps, so 0.002 is about one raid",
                        "every four days. 0.02 would be 2.4 a day and no village would survive.")
                .defineInRange("raidChancePerStep", 0.002D, 0.0D, 1.0D);
        RAID_LOOT_FRACTION = b.comment("Share of each stored item a lost raid carries off.",
                        "A third emptied a granary over three unseen raids, which is harsh for",
                        "something the player never had a chance to respond to.")
                .defineInRange("raidLootFraction", 0.15D, 0.0D, 1.0D);

        b.comment("defenseRating coefficients. Guesses until docs/testing.md section 4",
                        "measures real fights and tunes them.").push("rating");
        MILITIA_WEIGHT = b.defineInRange("militiaWeight", 2, 0, 100);
        WALL_WEIGHT = b.defineInRange("wallWeight", 15, 0, 100);
        WATCHTOWER_WEIGHT = b.defineInRange("watchtowerWeight", 8, 0, 100);
        COMBAT_READY_BONUS = b.defineInRange("combatReadyBonus", 10, 0, 100);
        GOLEM_DEFENSE_WEIGHT = b.comment("Iron golems are kept, not replaced - they already work.")
                .defineInRange("golemDefenseWeight", 12, 0, 100);
        BASE_BIOME_DANGER = b.comment("Baseline threat before lighting and structures apply.")
                .defineInRange("baseBiomeDanger", 10, 0, 100);
        b.pop();
        b.pop();

        b.comment("Population and food.").push("population");
        CONSUMPTION_PER_HEAD = b.comment("Food eaten per resident per step.")
                .defineInRange("consumptionPerHead", 1, 0, 64);
        YIELD_RATE = b.comment("Food produced per farmer per step.")
                .defineInRange("yieldRate", 3, 0, 64);
        TIMBER_RATE = b.comment("Logs cut per woodcutter per step.",
                        "PER STEP: 120 steps to a game day, so 3 is 360 logs a day and a small",
                        "palisade is several days of one person's work.")
                .defineInRange("timberRate", 3, 0, 1000);
        SAFETY_WINDOW_DAYS = b.comment("Length of the combat-casualty ring buffer, in game days.")
                .defineInRange("safetyWindowDays", 7, 1, 64);
        BASE_BIRTH_RATE = b.comment("Birth chance per step in an empty settlement with good morale.",
                        "PER STEP: 120 steps to a game day. 0.02 is roughly two births a day",
                        "before the logistic curve damps it - lower this first if growth feels fast.")
                .defineInRange("baseBirthRate", 0.02D, 0.0D, 1.0D);
        ENABLE_AGING = b.comment("Residents grow old and eventually die. Some players will not want",
                        "a villager they have grown attached to dying of old age; this is for them.")
                .define("enableAging", true);
        ELDER_THRESHOLD_DAYS = b.defineInRange("elderThresholdDays", 90, 10, 10000);
        INFANT_DAYS = b.comment("Infants are records only and are never spawned as entities.")
                .defineInRange("infantDays", 3, 0, 100);
        CHILD_DAYS = b.defineInRange("childDays", 20, 1, 1000);
        ELDER_DEATH_CHANCE_PER_STEP = b.comment("About 9% a game day.")
                .defineInRange("elderDeathChancePerStep", 0.0008D, 0.0D, 1.0D);

        b.comment("Safety capacity: how many will settle somewhere this dangerous.",
                        "This is where defence feeds back into growth.").push("safety");
        BASE_SAFETY = b.defineInRange("baseSafety", 4, 0, 1000);
        MIN_SAFETY = b.comment("Floor, so a mauled settlement can still recover.")
                .defineInRange("minSafety", 4, 0, 1000);
        SAFETY_RATING_DIVISOR = b.comment("defenseRating is divided by this before being added.")
                .defineInRange("safetyRatingDivisor", 4, 1, 100);
        SAFETY_DEATH_PENALTY = b.comment("Capacity lost per combat death inside the window.")
                .defineInRange("safetyDeathPenalty", 3, 0, 100);
        b.pop();

        b.comment("Famine. The warning has to come first - a village starving with no notice",
                        "is the original complaint in another costume.").push("famine");
        FOOD_WARNING_STEPS = b.comment("Warn when stores fall below this many steps of eating.",
                        "180 steps is about a day and a half.")
                .defineInRange("foodWarningSteps", 180, 0, 10000);
        FAMINE_GRACE_STEPS = b.comment("Steps at zero food before anyone starts dying.")
                .defineInRange("famineGraceSteps", 18, 0, 1000);
        FAMINE_MORALE_PENALTY = b.defineInRange("famineMoralePenalty", 4, 0, 100);
        FAMINE_DEATH_CHANCE_PER_STEP = b.comment("Per resident per step once the grace runs out.")
                .defineInRange("famineDeathChancePerStep", 0.01D, 0.0D, 1.0D);
        b.pop();
        b.pop();

        b.comment("Construction. See docs/construction.md.").push("construction");
        MAX_CELL_SLOPE = b.comment("Height difference across an 8-block cell before it is judged",
                        "unbuildable. Raising it means more terracing and more flattening, which",
                        "reads as griefing; lowering it means a hillside village never grows.",
                        "",
                        "4 matches the wall terrain rules in docs/construction.md, which step up",
                        "1-2, run a vertical segment at 3-4, and give up at 5. A site rule",
                        "stricter than the wall rule refuses ground the walls would have crossed.",
                        "Measured on a terraced hilltop village: 3 left 226 of 441 cells free,",
                        "4 left 273, and the gain flattens out after 5.")
                .defineInRange("maxCellSlope", 4, 0, 32);
        SURVEY_SCAN_HEIGHT = b.comment("How far above the surface a cell survey looks for existing",
                        "buildings. Too low and it misses a house's walls while seeing its floor.")
                .defineInRange("surveyScanHeight", 6, 1, 64);
        WALL_MARGIN_CELLS = b.comment("Cells of slack between the built-up area and the wall.",
                        "Zero builds the wall against the outermost house, so the next house",
                        "has to go outside it.")
                .defineInRange("wallMarginCells", 1, 0, 8);
        PALISADE_HEIGHT = b.comment("Log courses above ground. 3 is tall enough to stop a",
                        "zombie and short enough not to wall the village off from its own sky.")
                .defineInRange("palisadeHeight", 3, 1, 16);
        WALL_MIN_POPULATION = b.comment("Below this, a settlement has better things to do with",
                        "its timber than fortify.")
                .defineInRange("wallMinPopulation", 4, 1, 1000);
        BUILD_OP_INTERVAL_TICKS = b.comment("Ticks between blocks while somebody is watching.",
                        "10 is a block every half second: long enough to look like work and",
                        "short enough that a wall does not take an evening.")
                .defineInRange("buildOpIntervalTicks", 10, 1, 200);
        BUILDER_REACH = b.comment("How far a builder can place from where it stands.",
                        "Wider than a player arm on purpose - the alternative is scaffolding,",
                        "which has to be put up, taken down, and got wrong.")
                .defineInRange("builderReach", 5.5D, 1.0D, 16.0D);
        BUILDER_WALK_SPEED = b.defineInRange("builderWalkSpeed", 0.6D, 0.1D, 2.0D);
        BUILDER_WORK_RADIUS = b.comment("Unused since builder proximity stopped gating",
                        "placement. Kept so an existing config file does not lose a key, and",
                        "because a BuilderEntity with real AI will want it back.")
                .defineInRange("builderWorkRadius", 48.0D, 4.0D, 256.0D);
        b.pop();

        SPEC = b.build();
    }

    private PlacitumConfig() {}

    /** One game day in simulation steps. Use this when reading a per-step probability. */
    public static int stepsPerDay() {
        return 24000 / STEP_TICKS.get();
    }
}
