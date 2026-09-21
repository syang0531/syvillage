package com.syang.syvillage.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Every number that decides how something is read or built.
 *
 * <p>Magic numbers are forbidden - see CLAUDE.md - because none of these can be guessed
 * correctly up front and tuning them from source means a rebuild per attempt.
 *
 * <p>This file used to hold seventy keys and fourteen were read. The rest were the settings of a
 * simulation that no longer existed: raid odds, militia ratios, food per head, the age a villager
 * counts as old. They went on being written into every player's config file, where they read
 * exactly like settings that do something.
 *
 * <p>That is not untidiness, it is a lie in a file people edit. A key here is a promise that
 * turning it changes the game, so <b>a key nothing reads must be deleted</b>. The same mistake in
 * its live form cost a day: an interval and a batch size that both had to agree, where a config
 * file left over from an earlier world quietly cancelled a tenfold speed-up.
 *
 * <p>Eleven went with the grid in 0.3 - the claim, the phase ceiling, the slope and climb
 * limits, the street lamps, the intervals, the batch sizes of a queue that no longer exists -
 * and five more with the dark survey, which came out because a player at night can see the
 * dark without being told. <b>What is left is what something reads today.</b>
 */
public final class SyVillageConfig {

    public static final ModConfigSpec SPEC;

    // [blueprint] - putting a structure down, and looking at it first
    public static final ModConfigSpec.IntValue RAISE_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue MAX_DRAWING_OFFSET;
    public static final ModConfigSpec.IntValue DRAWING_REFRESH_TICKS;
    public static final ModConfigSpec.IntValue DRAWING_WATCH_RANGE;

    // [guardian] - the statue that keeps a golem
    public static final ModConfigSpec.IntValue GUARD_CHECK_TICKS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Blueprints: putting a structure down, and looking at it first.")
                .push("blueprint");
        RAISE_BLOCKS_PER_TICK = b.comment("Blocks laid per tick while a structure goes up.",
                        "Purely a flourish - every block is settled before the first one lands,",
                        "and if the server stops the rest appear at once. 40 puts a tower up",
                        "over about two seconds. 20000 is instant.")
                .defineInRange("raiseBlocksPerTick", 40, 1, 20000);
        MAX_DRAWING_OFFSET = b.comment("How far from its table a drawing may be pushed, in",
                        "blocks on each axis. Far enough to lay a house out across the stream;",
                        "not so far that the table is surveying chunks nobody has loaded, which",
                        "comes back as a refusal the player cannot see the cause of.")
                .defineInRange("maxDrawingOffset", 64, 1, 256);
        DRAWING_REFRESH_TICKS = b.comment("How often a table re-reads the ground under its",
                        "drawing. 20 is once a second.",
                        "",
                        "This is what turns the outline green after the player mines the block",
                        "that was in the way. It runs only for tables with their outline",
                        "switched on and a player nearby, and it decides nothing - it re-reads",
                        "an answer already on screen.")
                .defineInRange("drawingRefreshTicks", 20, 1, 1200);
        DRAWING_WATCH_RANGE = b.comment("How close a player has to be for a table to bother",
                        "re-reading. Beyond this the outline is still drawn from what the table",
                        "last worked out; it simply stops checking.")
                .defineInRange("drawingWatchRange", 48, 8, 256);
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

    private SyVillageConfig() {}
}
