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
 * <p>Eleven more went with the grid in 0.3: the claim, the phase ceiling, the slope and climb
 * limits, the street lamps, the survey and plan intervals, and the four batch sizes of a build
 * queue that no longer exists. What is left is what something still reads today. The dark
 * survey's own keys arrive with the survey, not before it.
 */
public final class SyVillageConfig {

    public static final ModConfigSpec SPEC;

    // [building] - reading the ground
    public static final ModConfigSpec.IntValue SURVEY_SCAN_HEIGHT;
    public static final ModConfigSpec.IntValue CLEAR_HEIGHT;

    // [guardian] - the statue that keeps a golem
    public static final ModConfigSpec.IntValue GUARD_CHECK_TICKS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Reading the ground.").push("building");
        SURVEY_SCAN_HEIGHT = b.comment("How far above the surface a reading looks for existing",
                        "buildings. Too low and it misses a house's walls while seeing its floor.")
                .defineInRange("surveyScanHeight", 6, 1, 64);
        CLEAR_HEIGHT = b.comment("How far above the ground a site is cleared of growth.",
                        "Only where something is actually being built. Tall enough for an oak,",
                        "because the ground reading walks down past trunks on purpose - one tree",
                        "must not make a site unbuildable - and the price of that is felling it",
                        "before building.")
                .defineInRange("clearHeight", 16, 0, 64);
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
