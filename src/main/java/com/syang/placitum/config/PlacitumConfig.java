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
    public static final ModConfigSpec.IntValue ANCHOR_REFRESH_NEAR_TICKS;
    public static final ModConfigSpec.DoubleValue CURFEW_WALK_SPEED;
    public static final ModConfigSpec.DoubleValue MILITIA_RATIO_CAP;
    public static final ModConfigSpec.DoubleValue ROUT_THRESHOLD;
    public static final ModConfigSpec.DoubleValue RAID_CHANCE_PER_STEP;
    public static final ModConfigSpec.IntValue RAID_MIN_POPULATION;
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
    public static final ModConfigSpec.IntValue MAX_SITE_DROP;
    public static final ModConfigSpec.IntValue BUILD_MAX_PHASES;
    public static final ModConfigSpec.IntValue MAX_ROAD_CLIMB;
    public static final ModConfigSpec.IntValue LAMP_BLOCKS_BEYOND_STREET;
    public static final ModConfigSpec.IntValue MIN_LIGHT_LEVEL;
    public static final ModConfigSpec.IntValue LAMPS_PER_JOB;
    public static final ModConfigSpec.IntValue ROAD_BLOCKS_PER_JOB;
    public static final ModConfigSpec.IntValue SURVEY_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue VERIFY_SAMPLE_EVERY;
    public static final ModConfigSpec.IntValue MAX_REBUILD_ATTEMPTS;
    public static final ModConfigSpec.IntValue CLEAR_HEIGHT;
    public static final ModConfigSpec.IntValue BUILD_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue PLAN_INTERVAL_TICKS;
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
        ANCHOR_REFRESH_NEAR_TICKS = b.comment("How often to re-read beds while a player is",
                        "actually in the settlement. 100 = five seconds.",
                        "This is a POI query, not the block survey, so it is cheap enough to",
                        "run at the speed a player expects a placed bed to be noticed.")
                .defineInRange("anchorRefreshNearTicks", 100, 20, 6000);
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
        RAID_MIN_POPULATION = b.comment("Settlements smaller than this are not raided.",
                        "Nothing in vanilla sends a pillager band after two villagers either.",
                        "Measured without it: a village of three with a defence rating of 8 lost",
                        "five residents to nine raids and could not replace one of them.",
                        "Set to 1 to raid everything, or raidChancePerStep to 0 for none at all.")
                .defineInRange("raidMinPopulation", 5, 1, 1000);
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
        ENABLE_AGING = b.comment("Residents grow old and eventually die.",
                        "Off by default. Vanilla villagers do not age, and this mod's promise is",
                        "that you can tell why somebody died - not that more of them do. It is",
                        "here for anyone who wants a settlement with generations in it.")
                .define("enableAging", false);
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
        MAX_CELL_SLOPE = b.comment("Blocks of relief a lot may have and still be built on.",
                        "0 means dead flat. Above it the settlement waits instead, because the",
                        "alternative is cutting the terrain, and a mod that reshapes a hillside",
                        "to suit itself reads as griefing.",
                        "",
                        "Waiting is not giving up: level a lot by hand and the next pass builds",
                        "on it, which is a better way to steer a village than any command. It was",
                        "4, and houses went up on ground nobody would call flat.")
                .defineInRange("maxCellSlope", 0, 0, 16);
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
        MAX_SITE_DROP = b.comment("How far below or above the bell a house may be built.",
                        "Site selection otherwise only measures distance across the map, and a",
                        "village on a plateau has no free ground at its own height - so every",
                        "house it built appeared eighteen blocks down a slope, out of sight.",
                        "A settlement that grows somewhere you cannot see it has not grown.")
                .defineInRange("maxSiteDrop", 8, 1, 128);
        BUILD_MAX_PHASES = b.comment("Ceiling on how many phases of city blocks a town builds.",
                        "A phase is a ring of blocks around the bell: phase 0 is the four that",
                        "meet at it, phase 1 the twelve around those, phase 2 the twenty around",
                        "those. Each one finishes - roads, lamps, buildings - before the next",
                        "begins. What normally decides the limit is the claim; this is a lower",
                        "ceiling for anyone who wants a village rather than a city.")
                .defineInRange("buildMaxPhases", 8, 0, 32);
        MAX_ROAD_CLIMB = b.comment("Steps in a row a street may change height before it gives up.",
                        "Not about whether you could walk there - you plainly could - but about",
                        "what a street laid over rolling ground looks like, which is a paved",
                        "ribbon draped over a hillside that nobody would ever build. Measured",
                        "across the road's whole width, so a street following a contour with one",
                        "level lane and two that are not counts as uneven.",
                        "",
                        "Any change counts, up or down: a field of hummocks is as unbuildable",
                        "looking as a slope. 3 means the street climbs three and stops on the",
                        "fourth. Raise it for a town that sprawls over hills; 0 keeps the streets",
                        "dead flat.")
                .defineInRange("maxRoadClimb", 3, 0, 64);
        LAMP_BLOCKS_BEYOND_STREET = b.comment("How many city blocks past the last street the lamps go.",
                        "Light is not a road. Where the street gives up on a hillside the mobs",
                        "do not, and they walk down it into the town, so the lit ground has to",
                        "reach further than the paved ground.",
                        "",
                        "It cannot be unlimited or the town grows lamps in meadows a long way",
                        "from any house, which is what it did when lamps only asked whether",
                        "somebody could walk to them.")
                .defineInRange("lampBlocksBeyondStreet", 1, 0, 8);
        MIN_LIGHT_LEVEL = b.comment("Block light a cell needs before the settlement stops",
                        "lighting it. Hostile mobs spawn at block light 0, so anything above",
                        "that suppresses them; a little headroom covers a lantern being broken.",
                        "This is the whole point of the mod: mobs killing villagers at night.")
                .defineInRange("minLightLevel", 8, 0, 15);
        LAMPS_PER_JOB = b.comment("Dark cells lit per build job, so lighting a new village is",
                        "something you watch rather than something that appears.")
                .defineInRange("lampsPerJob", 8, 1, 256);
        ROAD_BLOCKS_PER_JOB = b.comment("Street blocks laid per job. Bounded so the search for",
                        "unlaid street stops as soon as it has a batch rather than walking the",
                        "whole claim every tick looking for work that was finished an hour ago.")
                .defineInRange("roadBlocksPerJob", 64, 1, 4096);
        SURVEY_INTERVAL_TICKS = b.comment("How often the ground is re-read. 600 = thirty seconds.")
                .defineInRange("surveyIntervalTicks", 600, 20, 72000);
        VERIFY_SAMPLE_EVERY = b.comment("Check one already-placed block every this many new ones.",
                        "Checking every block would double the cost of building to catch",
                        "something that usually is not happening.")
                .defineInRange("verifySampleEvery", 16, 1, 1024);
        MAX_REBUILD_ATTEMPTS = b.comment("How often a settlement will start a stretch over before",
                        "giving up on the site. A player who clears the same ground three times",
                        "has said what they want.")
                .defineInRange("maxRebuildAttempts", 3, 1, 64);
        CLEAR_HEIGHT = b.comment("How far above the ground a site is cleared of growth.",
                        "Only where something is actually being built: a road column, a lamp",
                        "post, a house footprint. Tall enough for an oak, because the ground",
                        "reading walks down past trunks and a tree that cannot block a site has",
                        "to be felled before the site is used.")
                .defineInRange("clearHeight", 16, 0, 64);
        BUILD_BLOCKS_PER_TICK = b.comment("Blocks laid per tick while somebody is watching.",
                        "There used to be two keys here - an interval and a batch size - and a",
                        "config file left over from an earlier world set one of them back, so",
                        "ten blocks every ten ticks came out as exactly the old speed. One knob",
                        "cannot disagree with itself. 10 is a development speed; 1 is what a",
                        "finished mod would ship.")
                .defineInRange("buildBlocksPerTick", 10, 1, 256);
        PLAN_INTERVAL_TICKS = b.comment("How often an idle settlement looks for work.",
                        "Looking means reading the ground under every lot of the plan, which is",
                        "far too much to do sixty times a second for a village that finished",
                        "building an hour ago. 20 is once a second, which no one can see.")
                .defineInRange("planIntervalTicks", 20, 1, 1200);
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
