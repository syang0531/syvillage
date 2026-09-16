package com.syang.placitum.build;

import com.syang.placitum.Placitum;
import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.CellState;
import com.syang.placitum.data.CellPos;
import com.syang.placitum.data.PlotGrid;
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

        List<Integer> gates = chooseGates(settlement, box.get(), ring, profile);

        BuildRecipe recipe = new BuildRecipe(
                PALISADE,
                box.get().northWest(),
                Rotation.NONE,
                Identifier.fromNamespaceAndPath(Placitum.MODID, "biome_palette/plains"),
                List.copyOf(profile),
                new BlockPos(box.get().width(), heightOf(WallTier.PALISADE), box.get().depth()),
                gates);

        Placitum.LOGGER.debug("Planned a wall for '{}': {}x{} box, {} of {} positions placeable"
                        + " ({} unloaded)", settlement.name(), box.get().width(),
                box.get().depth(), placeable, ring.size(), unreadable);
        return Optional.of(recipe);
    }

    /**
     * Where the roads already leave the village.
     *
     * <p>Not chosen: found. The paths through a vanilla village were laid by worldgen and are
     * where its people already walk, so cutting the gates anywhere else would mean walling off
     * the routes and opening new ones nobody uses.
     *
     * <p>One gate per run of road, taken from the middle. A run four cells wide would otherwise
     * become four gates side by side, which is a gap, not a gate.
     */
    private static List<Integer> chooseGates(Settlement settlement, WallGeometry.Box box,
            List<BlockPos> ring, List<Integer> profile) {
        // Looking only at the cell under the ring finds almost nothing, and the reason is the
        // margin: the ring is deliberately a cell or two outside everything built, so it stands
        // on empty ground while the road stops just short of it. Measured on a real village
        // that was one gate in a ring of 538 posts.
        //
        // So look inward as well, as far as the margin reaches. A road that runs out at the
        // edge of the village is a road that wants to leave it.
        int reachIn = PlacitumConfig.WALL_MARGIN_CELLS.get() + 1;
        BlockPos centre = settlement.center();
        List<Integer> onRoad = new ArrayList<>();

        for (int i = 0; i < ring.size(); i++) {
            if (profile.get(i) == WallGeometry.SKIP) {
                continue;
            }
            if (roadWithin(settlement, ring.get(i), centre, reachIn)) {
                onRoad.add(i);
            }
        }
        List<Integer> gates = new ArrayList<>();
        int runStart = -1;
        for (int i = 0; i < onRoad.size(); i++) {
            if (runStart < 0) {
                runStart = i;
            }
            boolean endsHere = i + 1 == onRoad.size()
                    || onRoad.get(i + 1) != onRoad.get(i) + 1;
            if (endsHere) {
                gates.add(onRoad.get((runStart + i) / 2));
                runStart = -1;
            }
        }
        if (gates.isEmpty() && !ring.isEmpty()) {
            // A village whose roads never reach the ring still needs a way out. The south side
            // faces the way most players arrive, and any gate beats a sealed village.
            int south = box.width() + box.depth() + box.width() / 2 - 2;
            if (south >= 0 && south < profile.size() && profile.get(south) != WallGeometry.SKIP) {
                gates.add(south);
                Placitum.LOGGER.debug("No road crosses the ring of '{}'; cutting one gate south",
                        settlement.name());
            }
        }
        return List.copyOf(gates);
    }

    /**
     * Whether a road reaches this stretch of wall from inside.
     *
     * <p>Steps from the ring position towards the settlement centre, a cell at a time. Towards
     * the centre and not in every direction, because a road outside the wall is a road going
     * somewhere else, and cutting a gate for it opens the village to a path it does not use.
     */
    private static boolean roadWithin(Settlement settlement, BlockPos onRing, BlockPos centre,
            int cells) {
        double dx = centre.getX() - onRing.getX();
        double dz = centre.getZ() - onRing.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0) {
            return false;
        }
        for (int step = 0; step <= cells; step++) {
            int inX = (int) Math.round(onRing.getX() + dx / length * step * PlotGrid.CELL_BLOCKS);
            int inZ = (int) Math.round(onRing.getZ() + dz / length * step * PlotGrid.CELL_BLOCKS);
            CellPos cell = settlement.grid().cellAt(new BlockPos(inX, onRing.getY(), inZ));
            if (settlement.grid().stateAt(cell) == CellState.ROAD) {
                return true;
            }
        }
        return false;
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
