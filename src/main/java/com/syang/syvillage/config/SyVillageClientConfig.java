package com.syang.syvillage.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * How much of an outline you want to see through.
 *
 * <p>Separate from the other file because these are not balance. Nothing here changes what can
 * be built or where; they decide how heavily the preview is painted over the world, and the
 * right answer depends on the monitor, the shaders and how close the player stands. That makes
 * them a per-client preference, which is a different kind of number from "how far a drawing may
 * be pushed" - and a different config file, so a server never ships its taste to anybody.
 *
 * <p>Alpha out of 255 rather than a percentage, because that is what goes into the colour.
 */
public final class SyVillageClientConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MASS_OPACITY;
    public static final ModConfigSpec.IntValue MARK_OPACITY;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("How solidly a drawing's outline is painted. Also on the board's own screen,",
                        "where the sliders write these.")
                .push("preview");
        MASS_OPACITY = b.comment("The building's silhouette, out of 255.",
                        "",
                        "There are a couple of hundred of these bars and the player is looking",
                        "*through* them at the ground they are judging. A third was tried and a",
                        "seventeen-wide tower covered the screen; standing inside the footprint",
                        "it was a wall. 0 turns the silhouette off and leaves the outline.")
                .defineInRange("massOpacity", 26, 0, 255);
        MARK_OPACITY = b.comment("The blocks in the way, and the holes under the floor, out of",
                        "255. The opposite trade from the silhouette: there are a handful of",
                        "these and each is somewhere to walk to, so they are painted to stand",
                        "out of what is around them.")
                .defineInRange("markOpacity", 153, 0, 255);
        b.pop();

        SPEC = b.build();
    }

    private SyVillageClientConfig() {}
}
