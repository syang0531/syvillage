package com.syang.placitum.build;

import com.syang.placitum.Placitum;
import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.data.WallTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

/**
 * Reads the ground under a wall, once, and freezes it into a recipe.
 *
 * <p>This is the QUEUE stage of docs/construction.md, and it is the only part of building a wall
 * that touches the world. Everything after it works from the recipe alone - which is what lets
 * the wall go up in a settlement nobody is standing in.
 *
 * <p>Runs at the edge, where chunks are loaded. A settlement being simulated virtually cannot
 * call this and must not want to: principle 2.
 */
public final class WallPlanner {

    public static final Identifier PALISADE =
            Identifier.fromNamespaceAndPath(Placitum.MODID, "wall/palisade");

    private WallPlanner() {}

    /**
     * Plans a wall, or explains why there is not one to plan.
     *
     * <p>The empty case is a real answer, not a failure. A settlement that occupies no cells has
     * nothing to enclose, and one whose ring is entirely water or cliff is already walled by the
     * landscape.
     */
    public static Optional<BuildRecipe> plan(ServerLevel level, Settlement settlement) {
        int margin = PlacitumConfig.WALL_MARGIN_CELLS.get();
        Optional<WallGeometry.Box> box = WallGeometry.enclose(settlement, margin);
        if (box.isEmpty()) {
            Placitum.LOGGER.debug("No wall for '{}': it occupies no cells yet", settlement.name());
            return Optional.empty();
        }

        List<BlockPos> ring = WallGeometry.perimeter(box.get());
        List<Integer> profile = new ArrayList<>(ring.size());
        int unreadable = 0;

        for (BlockPos column : ring) {
            if (!level.hasChunkAt(column)) {
                // Frozen as a skip rather than sampled later. A profile half read now and half
                // read after the player rebuilds the hillside is not a frozen profile at all.
                profile.add(WallGeometry.SKIP);
                unreadable++;
                continue;
            }
            profile.add(GridSurvey.groundOrSkip(level, column.getX(), column.getZ()));
        }
        profile = dropCliffs(profile);

        int placeable = 0;
        for (int height : profile) {
            if (height != WallGeometry.SKIP) {
                placeable++;
            }
        }
        if (placeable == 0) {
            Placitum.LOGGER.debug("No wall for '{}': every position is water or cliff",
                    settlement.name());
            return Optional.empty();
        }

        BuildRecipe recipe = new BuildRecipe(
                PALISADE,
                box.get().northWest(),
                Rotation.NONE,
                Identifier.fromNamespaceAndPath(Placitum.MODID, "biome_palette/plains"),
                List.copyOf(profile),
                new BlockPos(box.get().width(), heightOf(WallTier.PALISADE), box.get().depth()));

        Placitum.LOGGER.debug("Planned a wall for '{}': {}x{} box, {} of {} positions placeable"
                        + " ({} unloaded)", settlement.name(), box.get().width(),
                box.get().depth(), placeable, ring.size(), unreadable);
        return Optional.of(recipe);
    }

    /**
     * Drops positions the wall cannot cross.
     *
     * <p>A single pass, deliberately: dropping a position makes its neighbours adjacent, which
     * could cascade until a ring on rolling ground erased itself. The gap left where a cliff
     * meets the ring is the point - the cliff is the wall there.
     */
    private static List<Integer> dropCliffs(List<Integer> profile) {
        List<Integer> out = new ArrayList<>(profile);
        for (int i = 0; i < profile.size(); i++) {
            int here = profile.get(i);
            if (here == WallGeometry.SKIP) {
                continue;
            }
            int next = profile.get((i + 1) % profile.size());
            if (BuildPlanner.isCliff(here, next)) {
                out.set(i, WallGeometry.SKIP);
            }
        }
        return out;
    }

    /** Palisade height. STONE and RAMPART are M4; see docs/construction.md. */
    public static int heightOf(WallTier tier) {
        return tier == WallTier.PALISADE ? PlacitumConfig.PALISADE_HEIGHT.get() : 0;
    }
}
