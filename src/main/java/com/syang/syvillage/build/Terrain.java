package com.syang.syvillage.build;

import com.syang.syvillage.config.SyVillageConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * What a column of the world is: how high its ground is, whether somebody built there, and
 * what is merely growing on it.
 *
 * <p>Every reading the mod takes of the terrain comes through here, and that is the point.
 * Two callers that decided where the ground was by different rules would disagree about the
 * height of the same block - which is how a flight of steps once arrived four blocks above
 * the wall it was meant to land on.
 *
 * <p>Was GridSurvey, which also held the plot grid. The grid is gone; these readings are not.
 */
public final class Terrain {

    private Terrain() {}
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
     * <p>Whether the column is fit to build on is {@link Ground#unfit}, which looks above the ground as
     * well as at it. See there for why that matters, and for how long it did not.
     */
    public static int groundOrSkip(ServerLevel level, int x, int z) {
        int y = groundAt(level, x, z);
        return Ground.unfit(level.getBlockState(new BlockPos(x, y, z)),
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
        return Ground.unfit(level.getBlockState(new BlockPos(x, y, z)),
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
        int scanHeight = SyVillageConfig.SURVEY_SCAN_HEIGHT.get();
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
    /** Whether a villager could take a job at this block. */
    public static boolean isJobSite(BlockState state) {
        return isWorkstation(state);
    }

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
