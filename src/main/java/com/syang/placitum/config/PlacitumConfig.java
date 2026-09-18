package com.syang.placitum.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Every number that decides how a town is laid out or how fast it goes up.
 *
 * <p>Magic numbers are forbidden - see CLAUDE.md - because none of these can be guessed
 * correctly up front and tuning them from source means a rebuild per attempt.
 *
 * <p>This file used to hold seventy keys and fourteen were read. The rest were the settings of a
 * simulation that no longer exists: raid odds, militia ratios, food per head, the age a villager
 * counts as old, the radius a settlement was promoted at. They went on being written into every
 * player's config file, where they read exactly like settings that do something.
 *
 * <p>That is not untidiness, it is a lie in a file people edit. A key here is a promise that
 * turning it changes the game, so <b>a key nothing reads must be deleted</b>. The same mistake in
 * its live form cost a day: an interval and a batch size that both had to agree, where a config
 * file left over from an earlier world quietly cancelled a tenfold speed-up.
 */
public final class PlacitumConfig {

    public static final ModConfigSpec SPEC;

    // [settlement] - what ground a bell claims
    public static final ModConfigSpec.IntValue CLAIM_RADIUS_CHUNKS;
    public static final ModConfigSpec.IntValue MIN_SETTLEMENT_DISTANCE;

    // [plan] - how far the town goes, and what ground it will build on
    public static final ModConfigSpec.IntValue BUILD_MAX_PHASES;
    public static final ModConfigSpec.IntValue MAX_CELL_SLOPE;
    public static final ModConfigSpec.IntValue MAX_ROAD_CLIMB;

    // [light] - the answer to the question the mod exists to ask
    public static final ModConfigSpec.IntValue MIN_LIGHT_LEVEL;
    public static final ModConfigSpec.IntValue LAMP_BLOCKS_BEYOND_STREET;

    // [building] - reading the ground, and the pace of putting blocks down
    public static final ModConfigSpec.IntValue SURVEY_SCAN_HEIGHT;
    public static final ModConfigSpec.IntValue SURVEY_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue PLAN_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue CLEAR_HEIGHT;
    public static final ModConfigSpec.IntValue BUILD_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue ROAD_BLOCKS_PER_JOB;
    public static final ModConfigSpec.IntValue LAMPS_PER_JOB;
    public static final ModConfigSpec.IntValue WALL_COLUMNS_PER_JOB;

    // [guardian] - the statue that keeps a golem
    public static final ModConfigSpec.IntValue GUARD_CHECK_TICKS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Registering a bell, and how much ground that claims.").push("settlement");
        CLAIM_RADIUS_CHUNKS = b.comment("Chunks each way. The claim is what bounds the town: the",
                        "plan stops at the last phase that fits inside it.")
                .defineInRange("claimRadiusChunks", 5, 1, 32);
        MIN_SETTLEMENT_DISTANCE = b.comment("Blocks. Registration is refused inside another claim.")
                .defineInRange("minSettlementDistance", 96, 0, 4096);
        b.pop();

        b.comment("The town plan: how far it reaches, and what ground it will build on.",
                        "The geometry itself is deliberately not configurable - it is what makes",
                        "'is there room for a house' stop being a judgement. See docs/design.md.")
                .push("plan");
        BUILD_MAX_PHASES = b.comment("Ceiling on how many phases of city blocks a town builds.",
                        "A phase is a ring of blocks around the bell: phase 0 is the four that",
                        "meet at it, phase 1 the twelve around those, phase 2 the twenty around",
                        "those. Each one finishes - roads, lamps, buildings - before the next",
                        "begins, and the last of all is street and light with no houses, left",
                        "open at its outer edge.",
                        "",
                        "What normally decides the limit is the claim; this is a lower ceiling",
                        "for anyone who wants a village rather than a city.")
                .defineInRange("buildMaxPhases", 8, 0, 32);
        MAX_CELL_SLOPE = b.comment("Blocks of relief a lot may have and still be built on.",
                        "0 means dead flat. Above it the settlement waits instead, because the",
                        "alternative is cutting the terrain, and a mod that reshapes a hillside",
                        "to suit itself reads as griefing.",
                        "",
                        "Waiting is not giving up: level a lot by hand and the next pass builds",
                        "on it. That is the mod's half of a loop the player is the other half of,",
                        "so a long list of lots the settlement will not touch is the point rather",
                        "than a problem. It was 4, and houses went up on ground nobody would call",
                        "flat.")
                .defineInRange("maxCellSlope", 0, 0, 16);
        MAX_ROAD_CLIMB = b.comment("Steps in a row a street may change height before it gives up.",
                        "Not about whether you could walk there - you plainly could - but about",
                        "what a street laid over rolling ground looks like, which is a paved",
                        "ribbon draped over a hillside that nobody would ever build. Measured",
                        "across the road's whole width, so a street following a contour with one",
                        "level lane and two that are not counts as uneven.",
                        "",
                        "Any change counts, up or down: a field of hummocks is as bad to lay a",
                        "road over as a slope. 3 means a street may climb three and must be back",
                        "on the level by the fourth, or that stretch is not laid at all. Raise it",
                        "for a town that sprawls over hills; 0 keeps the streets dead flat.")
                .defineInRange("maxRoadClimb", 3, 0, 64);
        b.pop();

        b.comment("Street lighting. Mobs killing villagers at night is the problem this mod was",
                        "started to solve, and light is what decides whether one spawns.")
                .push("light");
        MIN_LIGHT_LEVEL = b.comment("Block light below which /placitum light reports ground as",
                        "dark. Hostile mobs spawn at block light 0, so anything above that",
                        "suppresses them; a little headroom covers a lantern being broken.",
                        "",
                        "A report, not a plan. Where lamps go is decided by the town plan, so",
                        "that the posts of a block are all there or all absent rather than",
                        "scattered by whichever happened to be lit when the last one went up.")
                .defineInRange("minLightLevel", 8, 0, 15);
        LAMP_BLOCKS_BEYOND_STREET = b.comment("How many city blocks past the last street the lamps",
                        "go. Light is not a road: where the street gives up on a hillside the mobs",
                        "do not, and they walk down it into the town, so the lit ground has to",
                        "reach further than the paved ground.",
                        "",
                        "It cannot be unlimited or the town grows lamps in meadows a long way",
                        "from any house, which is what it did when lamps only asked whether",
                        "somebody could walk to them.")
                .defineInRange("lampBlocksBeyondStreet", 1, 0, 8);
        b.pop();

        b.comment("Reading the ground, and the pace of putting blocks down.").push("building");
        SURVEY_SCAN_HEIGHT = b.comment("How far above the surface a survey looks for existing",
                        "buildings. Too low and it misses a house's walls while seeing its floor.")
                .defineInRange("surveyScanHeight", 6, 1, 64);
        SURVEY_INTERVAL_TICKS = b.comment("How often the ground is re-read. 600 = thirty seconds.")
                .defineInRange("surveyIntervalTicks", 600, 20, 72000);
        PLAN_INTERVAL_TICKS = b.comment("How often an idle settlement looks for work.",
                        "Looking means walking the whole plan and reading the ground under every",
                        "lot of it, which is far too much to do sixty times a second for a",
                        "village that finished building an hour ago. 20 is once a second, which",
                        "no one can see.")
                .defineInRange("planIntervalTicks", 20, 1, 1200);
        CLEAR_HEIGHT = b.comment("How far above the ground a site is cleared of growth.",
                        "Only where something is actually being built: a road column, a lamp",
                        "post, a house footprint. Tall enough for an oak, because the ground",
                        "reading walks down past trunks on purpose - one tree must not make a",
                        "site unbuildable - and the price of that is felling it before building.")
                .defineInRange("clearHeight", 16, 0, 64);
        BUILD_BLOCKS_PER_TICK = b.comment("Blocks laid per tick while somebody is watching.",
                        "There used to be two keys here - an interval and a batch size - and a",
                        "config file left over from an earlier world set one of them back, so",
                        "ten blocks every ten ticks came out as exactly the old speed. One knob",
                        "cannot disagree with itself. 1 is a village you can watch being built;",
                        "10 is the speed it was developed at.")
                .defineInRange("buildBlocksPerTick", 1, 1, 256);
        ROAD_BLOCKS_PER_JOB = b.comment("Street blocks laid per job. Bounded so the search for",
                        "unlaid street stops as soon as it has a batch rather than walking the",
                        "whole claim every tick looking for work that was finished an hour ago.")
                .defineInRange("roadBlocksPerJob", 64, 1, 4096);
        LAMPS_PER_JOB = b.comment("Lamp posts raised per build job, so lighting a new village is",
                        "something you watch rather than something that appears.")
                .defineInRange("lampsPerJob", 8, 1, 256);
        WALL_COLUMNS_PER_JOB = b.comment("Columns of wall raised per job. A column is four",
                        "blocks or so, where a street block is one, so this is smaller than",
                        "roadBlocksPerJob for the same amount of watching.")
                .defineInRange("wallColumnsPerJob", 32, 1, 1024);
        b.pop();

        b.comment("The guardian statue.").push("guardian");
        GUARD_CHECK_TICKS = b.comment("How often a statue asks after its golem. 200 = ten",
                        "seconds. The answer changes about as often as a golem dies, so this",
                        "is deliberately slow: it decides how long a town stands unguarded",
                        "after one falls, and nothing else.")
                .defineInRange("guardCheckTicks", 200, 20, 24000);
        b.pop();

        SPEC = b.build();
    }

    private PlacitumConfig() {}
}
