package com.syang.placitum.build;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.CellPos;
import com.syang.placitum.data.CellState;
import com.syang.placitum.data.PlotGrid;
import com.syang.placitum.data.Settlement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The crossroads a settlement starts from.
 *
 * <p>Site selection will only put a house beside a road, which is the one line in
 * docs/construction.md that decides what a village looks like - without it houses land wherever
 * the ground happens to be flat and the place stops reading as a village at all.
 *
 * <p>The catch is what happens when there are no roads. A vanilla village comes with paths
 * worldgen laid; a settlement somebody founded by placing two beds and a bell has none, so no
 * cell is ever beside a road, so no house is ever sited, so beds never rise above two, so
 * population never rises above two, and then everybody grows old. A minimum settlement was a
 * settlement with a death sentence.
 *
 * <p>So the settlement lays its own, which is what the design said all along: a cross through
 * the centre, and the rest of the pipeline treats it as any other build.
 */
public final class RoadPlan {

    public static final Identifier CROSS =
            Identifier.fromNamespaceAndPath(Placitum.MODID, "road/cross");

    /**
     * Three blocks across.
     *
     * <p>Not for looks. The survey samples a cell every other block, so a single-block path can
     * fall between samples and the cell it runs through never reads as a road at all - a road
     * nothing can be built beside is not a road.
     */
    private static final int WIDTH = 3;

    private static final BlockState PATH = Blocks.DIRT_PATH.defaultBlockState();

    private RoadPlan() {}

    /** Columns of the cross, in the order the ground profile stores them. */
    public static List<BlockPos> columns(BlockPos centre, int armBlocks) {
        List<BlockPos> out = new ArrayList<>();
        int half = WIDTH / 2;
        for (int along = -armBlocks; along <= armBlocks; along++) {
            for (int across = -half; across <= half; across++) {
                out.add(centre.offset(along, 0, across));   // east-west arm
            }
        }
        for (int along = -armBlocks; along <= armBlocks; along++) {
            for (int across = -half; across <= half; across++) {
                if (Math.abs(along) <= half) {
                    continue;   // the middle is already laid by the other arm
                }
                out.add(centre.offset(across, 0, along));   // north-south arm
            }
        }
        return out;
    }

    /** How far each arm reaches, in blocks. */
    public static int armBlocks(Settlement settlement) {
        return settlement.scale().buildRadiusCells() * PlotGrid.CELL_BLOCKS;
    }

    /**
     * Reads the ground along the cross and freezes it.
     *
     * <p>Empty when there is nothing to lay - every column is water, or unloaded. A settlement
     * that cannot put a road down is one that will say so rather than queue work forever.
     */
    public static Optional<BuildRecipe> plan(ServerLevel level, Settlement settlement) {
        BlockPos centre = settlement.center();
        int arm = armBlocks(settlement);
        List<Integer> profile = new ArrayList<>();
        int placeable = 0;

        for (BlockPos column : columns(centre, arm)) {
            if (!level.hasChunkAt(column)) {
                profile.add(WallGeometry.SKIP);
                continue;
            }
            // A path goes on the ground, and the ground is whatever is lowest there - a bed,
            // the bell, a gate post. All three were paved over on the first run. Anything
            // somebody put there stays, and the road simply has a gap in it.
            if (GridSurvey.builtOn(level, column.getX(), column.getZ())
                    || settlement.onWall(column)) {
                profile.add(WallGeometry.SKIP);
                continue;
            }
            int ground = GridSurvey.groundOrSkip(level, column.getX(), column.getZ());
            profile.add(ground);
            if (ground != WallGeometry.SKIP) {
                placeable++;
            }
        }
        if (placeable == 0) {
            return Optional.empty();
        }
        Placitum.LOGGER.info("Planned a crossroads for '{}': arms of {} block(s), {} of {}"
                        + " placeable", settlement.name(), arm, placeable, profile.size());
        return Optional.of(new BuildRecipe(CROSS, centre, Rotation.NONE,
                Identifier.fromNamespaceAndPath(Placitum.MODID, "biome_palette/plains"),
                List.copyOf(profile), new BlockPos(arm, 1, arm), List.of()));
    }

    /**
     * One path block per column, on the ground that was frozen.
     *
     * <p>Nothing is cleared above. A road that bulldozes whatever it meets would carve through
     * the houses the village already has, and docs/construction.md is firm that cutting terrain
     * is how a mod starts looking like griefing.
     */
    public static List<BuildOp> expand(BuildRecipe recipe) {
        List<Integer> profile = recipe.groundProfile();
        List<BlockPos> columns = columns(recipe.anchor(), recipe.width());
        if (columns.size() != profile.size()) {
            return List.of();
        }
        List<BuildOp> ops = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            if (profile.get(i) == WallGeometry.SKIP) {
                continue;
            }
            BlockPos column = columns.get(i);
            ops.add(new BuildOp(new BlockPos(column.getX(), profile.get(i), column.getZ()), PATH));
        }
        ops.sort(Comparator.comparingInt((BuildOp op) -> op.pos().getY())
                .thenComparingInt(op -> op.pos().getX())
                .thenComparingInt(op -> op.pos().getZ()));
        return List.copyOf(ops);
    }

    /** Which cells the finished cross runs through. */
    public static PlotGrid markCells(PlotGrid grid, BuildRecipe recipe) {
        PlotGrid out = grid;
        List<Integer> profile = recipe.groundProfile();
        List<BlockPos> columns = columns(recipe.anchor(), recipe.width());
        for (int i = 0; i < columns.size() && i < profile.size(); i++) {
            if (profile.get(i) == WallGeometry.SKIP) {
                continue;
            }
            CellPos cell = out.cellAt(columns.get(i));
            // Never over something already standing. A road is allowed to reach a house; it is
            // not allowed to declare the house a road and let the next one be built on it.
            if (out.stateAt(cell) == CellState.FREE) {
                out = out.with(cell, CellState.ROAD);
            }
        }
        return out;
    }
}
