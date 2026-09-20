package com.syang.syvillage.build;

import java.util.List;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a ground reading means when there is no ground to read.
 *
 * <p>Every planner needs it: a column under water, in an unloaded chunk, or already built on has
 * no height worth freezing, and a build has to be able to leave a gap there rather than guess.
 */
public final class Ground {

    /** No usable ground here. Skip this column. */
    public static final int SKIP = Integer.MIN_VALUE;

    private Ground() {}

    /**
     * The highest ground in a profile, or {@link #SKIP} if there is none.
     *
     * <p>What a structure's floor is levelled to. Digging into a slope reads as griefing and
     * standing on stilts reads as a bug, so everything below it gets a foundation instead.
     */
    public static int highest(List<Integer> profile) {
        int best = SKIP;
        for (int ground : profile) {
            if (ground != SKIP && (best == SKIP || ground > best)) {
                best = ground;
            }
        }
        return best;
    }

    /**
     * Whether a column is under water, given the ground block and the one above it.
     *
     * <p>The one above it is the whole point. Water is replaceable, so the walk down to the
     * ground goes straight through a lake and stops on the sand at the bottom of it - and asking
     * that sand whether it is wet gets no for an answer, every time, because the water is not in
     * the sand, it is on top of it.
     *
     * <p>The rule had been written the other way round since it was first added and had
     * therefore never once fired. Streets were laid along sea beds and cottages stood in
     * shallows. It is a free function taking two block states so that it can be tested without
     * a world, which is what the version that lived inside the world lookup could not be.
     */
    public static boolean underwater(BlockState ground, BlockState above) {
        return !ground.getFluidState().isEmpty() || !above.getFluidState().isEmpty();
    }

    /**
     * Whether a column is no ground to build on, water or otherwise.
     *
     * <p>Water and lava are fluids and {@link #underwater} already refused them. Ice is water
     * that happens to be solid this minute: a lantern melts it, and a street laid across a
     * frozen lake is a street in the lake by spring. Powder snow is a hole with a lid on it.
     * Packed and blue ice are permanent and are left alone - they are ground.
     *
     * <p>Everything that reads the ground asks this, so the walk that decides what is reachable
     * refuses a frozen river the same way it refuses a flowing one.
     */
    public static boolean unfit(BlockState ground, BlockState above) {
        return underwater(ground, above)
                || ground.is(Blocks.ICE) || ground.is(Blocks.FROSTED_ICE)
                || ground.is(Blocks.POWDER_SNOW) || above.is(Blocks.POWDER_SNOW);
    }
}
