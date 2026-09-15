package com.syang.placitum.build;

import com.syang.placitum.Placitum;
import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.CellPos;
import com.syang.placitum.data.CellState;
import com.syang.placitum.data.PlotGrid;
import com.syang.placitum.data.Settlement;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Works out what is already on each cell of the plot grid.
 *
 * <p>The grid is laid over a village vanilla generated, so most of what it covers is not empty.
 * Reading the world is the only way to know which cells are: {@code CellState} is stored and
 * never rescanned during simulation, because rescanning would need loaded chunks and principle 2
 * forbids the virtual side from touching the world at all.
 *
 * <p>So this runs at the edge - registration, and the anchor refresh - and everything downstream
 * reads the stored answer. See docs/construction.md.
 */
public final class GridSurvey {

    /**
     * Every other block, in both axes: sixteen columns a cell.
     *
     * <p>Not a balance number, a precision one. Every column would be four times the cost to
     * tell apart cases that differ by a single block, and a one-block feature is not what
     * decides whether a house fits.
     */
    private static final int SAMPLE_STRIDE = 2;

    /** What the survey found, including what it could not look at. */
    public record Result(PlotGrid grid, int scanned, int skipped) {

        public boolean complete() {
            return skipped == 0;
        }
    }

    private GridSurvey() {}

    /**
     * Classifies every cell in the grid.
     *
     * <p>Cells in unloaded chunks are left exactly as they were and counted in {@code skipped}.
     * Defaulting them to FREE would be worse than leaving them unknown - the settlement would
     * plan a house onto ground nobody has looked at - and reporting the count is what stops a
     * half-surveyed grid from being read as a fully-surveyed empty one.
     */
    public static Result run(ServerLevel level, Settlement settlement) {
        // Settlements registered before the grid was sized from the claim carry a grid that
        // covers a fraction of it. Growing here rather than in a migration keeps every cell
        // they already have and needs no separate upgrade path.
        PlotGrid grid = settlement.grid().grownTo(
                PlotGrid.sizeForClaim(settlement.identity().claimRadiusChunks()));
        int radius = (grid.size() - 1) / 2;
        int maxSlope = PlacitumConfig.MAX_CELL_SLOPE.get();
        int scanHeight = PlacitumConfig.SURVEY_SCAN_HEIGHT.get();

        Map<CellPos, CellState> cells = new LinkedHashMap<>(grid.cells());
        int scanned = 0;
        int skipped = 0;

        for (int gz = -radius; gz <= radius; gz++) {
            for (int gx = -radius; gx <= radius; gx++) {
                CellPos cell = new CellPos(gx, gz);
                BlockPos nw = grid.blockAt(cell);
                int east = nw.getX() + PlotGrid.CELL_BLOCKS - 1;
                int south = nw.getZ() + PlotGrid.CELL_BLOCKS - 1;

                if (!level.hasChunksAt(nw.getX(), nw.getZ(), east, south)) {
                    skipped++;
                    continue;
                }
                // A cell the player has marked stays marked. Manual blocking is the escape
                // hatch for everything this heuristic gets wrong, so the survey must not
                // silently undo it on the next refresh.
                if (grid.stateAt(cell) == CellState.BLOCKED) {
                    scanned++;
                    continue;
                }
                cells.put(cell, classify(level, nw, maxSlope, scanHeight));
                scanned++;
            }
        }

        PlotGrid surveyed = new PlotGrid(grid.origin(), grid.size(), cells);
        Placitum.LOGGER.debug("Surveyed '{}': {} free, {} built, {} road, {} blocked"
                        + " ({} cell(s) scanned, {} unloaded)", settlement.name(),
                surveyed.countOf(CellState.FREE), surveyed.countOf(CellState.BUILT),
                surveyed.countOf(CellState.ROAD), surveyed.countOf(CellState.BLOCKED),
                scanned, skipped);
        return new Result(surveyed, scanned, skipped);
    }

    /**
     * One cell.
     *
     * <p>Order matters: something built on it beats a path across it, and either beats the
     * terrain underneath. A cell with a house on a slope is occupied, not unbuildable, and
     * calling it BLOCKED would mean the settlement forgets the house is there.
     */
    private static CellState classify(ServerLevel level, BlockPos nw, int maxSlope,
            int scanHeight) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        boolean wet = false;
        boolean built = false;
        boolean road = false;

        for (int dx = 0; dx < PlotGrid.CELL_BLOCKS; dx += SAMPLE_STRIDE) {
            for (int dz = 0; dz < PlotGrid.CELL_BLOCKS; dz += SAMPLE_STRIDE) {
                int x = nw.getX() + dx;
                int z = nw.getZ() + dz;
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                lowest = Math.min(lowest, surface);
                highest = Math.max(highest, surface);

                BlockState top = level.getBlockState(new BlockPos(x, surface, z));
                if (!top.getFluidState().isEmpty()) {
                    wet = true;
                }
                if (top.is(Blocks.DIRT_PATH)) {
                    road = true;
                }
                for (int dy = 0; dy <= scanHeight && !built; dy++) {
                    if (isBuilt(level.getBlockState(new BlockPos(x, surface + dy, z)))) {
                        built = true;
                    }
                }
            }
        }

        if (built) {
            return CellState.BUILT;
        }
        if (road) {
            return CellState.ROAD;
        }
        if (wet || highest - lowest > maxSlope) {
            return CellState.BLOCKED;
        }
        return CellState.FREE;
    }

    /**
     * Whether a block is evidence somebody built here.
     *
     * <p>A heuristic, and openly one. It is tuned to what vanilla village generation actually
     * places, and it errs towards saying yes: mistaking a house for empty ground puts a new
     * building through someone's roof, while mistaking a stray fence post for a house costs one
     * cell out of a hundred. Those two errors are not worth the same.
     *
     * <p>The player's own builds are covered by the same list, which is most of what
     * docs/construction.md means by protecting them. The plot block command is the override
     * for the rest.
     */
    private static boolean isBuilt(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        return state.is(BlockTags.BEDS)
                || state.is(BlockTags.DOORS)
                || state.is(BlockTags.PLANKS)
                || state.is(BlockTags.WOODEN_STAIRS)
                || state.is(BlockTags.WOODEN_FENCES)
                || state.is(BlockTags.WALLS)
                || state.is(BlockTags.WOOL)
                || state.is(BlockTags.TRAPDOORS)
                || state.is(BlockTags.CAMPFIRES)
                || state.is(Blocks.FARMLAND)
                || state.is(Blocks.GLASS)
                || state.is(Blocks.GLASS_PANE)
                || state.is(Blocks.TORCH)
                || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.LANTERN)
                || state.is(Blocks.BELL)
                || state.is(Blocks.HAY_BLOCK)
                || state.is(Blocks.BOOKSHELF)
                || state.is(Blocks.LADDER)
                || state.is(Blocks.CHEST)
                || state.is(Blocks.CRAFTING_TABLE)
                || state.is(Blocks.FURNACE)
                || isWorkstation(state);
    }

    /** Villager job sites. Their presence is the strongest signal of all: someone works here. */
    private static boolean isWorkstation(BlockState state) {
        return state.is(Blocks.COMPOSTER) || state.is(Blocks.BARREL)
                || state.is(Blocks.SMOKER) || state.is(Blocks.BLAST_FURNACE)
                || state.is(Blocks.CAULDRON) || state.is(Blocks.BREWING_STAND)
                || state.is(Blocks.FLETCHING_TABLE) || state.is(Blocks.SMITHING_TABLE)
                || state.is(Blocks.CARTOGRAPHY_TABLE) || state.is(Blocks.LOOM)
                || state.is(Blocks.STONECUTTER) || state.is(Blocks.GRINDSTONE)
                || state.is(Blocks.LECTERN);
    }
}
