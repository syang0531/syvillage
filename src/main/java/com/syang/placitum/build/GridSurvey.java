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
     * Every other block, in both axes.
     *
     * <p>This is the map, not the decision. What actually gates a building is
     * {@link Lots#verdict}, which reads every column of the lot - the survey is for the player's
     * benefit and for narrowing where to look.
     */
    private static final int SAMPLE_STRIDE = 2;

    /** Slope thresholds the report costs out, so tuning is a measurement and not an argument. */
    public static final int[] SLOPE_LADDER = {2, 3, 4, 5, 6, 8};

    /**
     * What the survey found, including what it could not look at and why it said no.
     *
     * <p>A single blocked count is the mistake this milestone keeps relearning: it conflates
     * water with gradient, and gradient with the threshold gradient is measured against. Three
     * different fixes, one indistinguishable symptom.
     *
     * <p>{@code freeAtSlope} costs out {@link #SLOPE_LADDER} against the terrain actually
     * surveyed - what the free count would have been at each threshold. maxCellSlope is a config
     * number, and this is how it gets chosen from evidence rather than taste.
     */
    public record Result(PlotGrid grid, int scanned, int skipped, int forbidden,
            int blockedWet, int blockedSlope, int[] freeAtSlope) {

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
        int skippedForbidden = 0;
        int blockedWet = 0;
        int blockedSlope = 0;
        int[] freeAtSlope = new int[SLOPE_LADDER.length];

        for (int gz = -radius; gz <= radius; gz++) {
            for (int gx = -radius; gx <= radius; gx++) {
                CellPos cell = new CellPos(gx, gz);
                BlockPos nw = TownPlan.lotCorner(cell, grid.origin());
                int east = nw.getX() + TownPlan.LOT - 1;
                int south = nw.getZ() + TownPlan.LOT - 1;

                if (!level.hasChunksAt(nw.getX(), nw.getZ(), east, south)) {
                    skipped++;
                    continue;
                }
                // A cell the player has forbidden stays forbidden. That is the escape hatch
                // for everything this heuristic gets wrong, and an override the next refresh
                // undoes is not one.
                //
                // Only FORBIDDEN, never BLOCKED. BLOCKED is this survey's own verdict and has
                // to be re-winnable: skipping it made the second survey of a village silently
                // re-report the first, identical down to the character, while claiming to have
                // scanned 441 cells.
                if (isFrozen(grid.stateAt(cell))) {
                    skippedForbidden++;
                    continue;
                }
                Reading read = read(level, nw, scanHeight);
                cells.put(cell, read.verdict(maxSlope));
                scanned++;

                if (read.blocksAt(maxSlope)) {
                    if (read.wet()) {
                        blockedWet++;
                    } else {
                        blockedSlope++;
                    }
                }
                for (int i = 0; i < SLOPE_LADDER.length; i++) {
                    if (read.verdict(SLOPE_LADDER[i]) == CellState.FREE) {
                        freeAtSlope[i]++;
                    }
                }
            }
        }

        PlotGrid surveyed = new PlotGrid(grid.origin(), grid.size(), cells);
        Placitum.LOGGER.debug("Surveyed '{}': {} free, {} built, {} road, {} blocked"
                        + " ({} cell(s) scanned, {} unloaded)", settlement.name(),
                surveyed.countOf(CellState.FREE), surveyed.countOf(CellState.BUILT),
                surveyed.countOf(CellState.ROAD), surveyed.countOf(CellState.BLOCKED),
                scanned, skipped);
        return new Result(surveyed, scanned, skipped, skippedForbidden, blockedWet,
                blockedSlope, freeAtSlope);
    }

    /**
     * Whether a cell is the survey to leave alone.
     *
     * <p>Exactly one state qualifies, and the reason it is a named function rather than an
     * inline comparison is that getting it wrong is invisible. When this also covered BLOCKED,
     * a second survey re-reported the first one character for character while claiming to have
     * scanned every cell - the output of a survey that skipped a third of the grid looks
     * precisely like the output of one that did not.
     */
    public static boolean isFrozen(CellState state) {
        return state == CellState.FORBIDDEN;
    }

    /**
     * What one cell is, separated from what to make of it.
     *
     * <p>Reading the world and judging it are split so the judgement can be re-run at other
     * thresholds without touching a chunk again. That is the whole trick behind costing out a
     * slope ladder: 441 cells read once, judged six times.
     */
    private record Reading(int relief, boolean wet, boolean built, boolean road) {

        /**
         * Order matters: something built on it beats a path across it, and either beats the
         * terrain underneath. A cell with a house on a slope is occupied, not unbuildable, and
         * calling it BLOCKED would mean the settlement forgets the house is there.
         */
        CellState verdict(int maxSlope) {
            if (built) {
                return CellState.BUILT;
            }
            if (road) {
                return CellState.ROAD;
            }
            return blocksAt(maxSlope) ? CellState.BLOCKED : CellState.FREE;
        }

        boolean blocksAt(int maxSlope) {
            return !built && !road && (wet || relief > maxSlope);
        }
    }

    private static Reading read(ServerLevel level, BlockPos nw, int scanHeight) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        boolean wet = false;
        boolean built = false;
        boolean road = false;

        for (int dx = 0; dx < TownPlan.LOT; dx += SAMPLE_STRIDE) {
            for (int dz = 0; dz < TownPlan.LOT; dz += SAMPLE_STRIDE) {
                int x = nw.getX() + dx;
                int z = nw.getZ() + dz;
                int surface = groundAt(level, x, z);
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

        return new Reading(highest - lowest, wet, built, road);
    }

    /**
     * The ground, with whatever is growing on it discounted.
     *
     * <p>No heightmap answers this. MOTION_BLOCKING_NO_LEAVES drops the leaves and keeps the
     * trunk, so a single tree makes a cell read as six blocks of relief and the slope rule
     * calls it unbuildable. The first survey of a wooded village came back with 159 of 441
     * cells blocked against 23 built - it was counting trees, not gradient.
     *
     * <p>Trees are not terrain. Clearing them is part of building somewhere, which is why they
     * must not be allowed to veto a site; a cliff is a different matter and still does.
     */
    public static int groundAt(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        int floor = level.getMinY();
        while (y > floor && isGrowth(level.getBlockState(new BlockPos(x, y, z)))) {
            y--;
        }
        return y;
    }

    /**
     * Ground height for a column, or {@link Ground#SKIP} where it stands in water.
     *
     * <p>Shares {@link #groundAt} with the survey on purpose. A planner that decided where the
     * ground was by different rules than the survey that judged the site buildable would put its
     * footings at a height the site never agreed to.
     *
     * <p>Whether the column is wet is {@link Ground#underwater}, which looks above the ground as
     * well as at it. See there for why that matters, and for how long it did not.
     */
    public static int groundOrSkip(ServerLevel level, int x, int z) {
        int y = groundAt(level, x, z);
        return Ground.underwater(level.getBlockState(new BlockPos(x, y, z)),
                level.getBlockState(new BlockPos(x, y + 1, z))) ? Ground.SKIP : y;
    }

    /**
     * The terrain under our own masonry in this column.
     *
     * <p>{@link #groundAt} reports the top of a wall as the ground once a wall is standing, which
     * is right for almost everything and wrong for anything that has to line up with the wall's
     * footing. A flight of steps levelled against the top of the wall it lands on arrives four
     * blocks above it.
     *
     * <p>Only our own material is walked through, and only in the columns of something we are
     * building against, so a player's cobblestone house is not treated as a hole.
     */
    /**
     * The footing, with water marked rather than guessed at.
     *
     * <p>The two questions a structure asks of a column, in the one order that answers both:
     * walk down past our own masonry first, then ask whether what is left is wet. Asking them
     * the other way round asks the top of a gatehouse whether it is under water.
     */
    public static int footingOrSkip(ServerLevel level, int x, int z, BlockState ours) {
        int y = footingAt(level, x, z, ours);
        return Ground.underwater(level.getBlockState(new BlockPos(x, y, z)),
                level.getBlockState(new BlockPos(x, y + 1, z))) ? Ground.SKIP : y;
    }

    public static int footingAt(ServerLevel level, int x, int z, BlockState ours) {
        int y = groundAt(level, x, z);
        int floor = level.getMinY();
        while (y > floor && level.getBlockState(new BlockPos(x, y, z)).is(ours.getBlock())) {
            y--;
        }
        return y;
    }

    /**
     * Whether somebody has already built on this column.
     *
     * <p>{@link #groundAt} walks down past logs and undergrowth, and a plank roof is neither, so
     * a finished house reads back as ground at roof height. A site chosen on that reading gets a
     * second house built on the first one's roof - which is precisely what happened, and what a
     * twelve-block plank wall in a screenshot turned out to be.
     *
     * <p>Cheaper than teaching groundAt to see through buildings, and more honest: the ground
     * under a house genuinely is not available, whatever height it is at.
     */
    public static boolean builtOn(ServerLevel level, int x, int z) {
        int ground = groundAt(level, x, z);
        int scanHeight = PlacitumConfig.SURVEY_SCAN_HEIGHT.get();
        for (int dy = 0; dy <= scanHeight; dy++) {
            if (isBuilt(level.getBlockState(new BlockPos(x, ground + dy, z)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Growth that is actually in the way: a plant, and not simply air.
     *
     * <p>{@link #isGrowth} counts air, because for walking down to the ground air is exactly as
     * ignorable as a leaf. For felling it is not - a column of air is nothing to cut, and
     * treating it as growth would have every site clear sixteen blocks of sky.
     */
    public static boolean isCuttable(BlockState state) {
        return !state.isAir() && isGrowth(state);
    }

    /** Things that stand on the ground without being it. */
    private static boolean isGrowth(BlockState state) {
        return state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES)
                || state.is(Blocks.BAMBOO)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.MUSHROOM_STEM)
                || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || state.is(Blocks.RED_MUSHROOM_BLOCK)
                || state.is(Blocks.SNOW)
                || state.canBeReplaced();
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
                // Our own masonry. None of these three generate on the surface, so finding one
                // means somebody built it - and without them a stone wall could not tell it was
                // already standing. See WallPlan.
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.STONE_BRICKS)
                || state.is(Blocks.STONE_BRICK_SLAB)
                // The desert palette. Sandstone does generate on the surface, so this is looser
                // there than elsewhere - but the alternative is a desert wall that cannot tell it
                // is standing and gets planned again for ever.
                || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.CUT_SANDSTONE)
                || state.is(Blocks.SMOOTH_SANDSTONE)
                || state.is(Blocks.SANDSTONE_SLAB)
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
