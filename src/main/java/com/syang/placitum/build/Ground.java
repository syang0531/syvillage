package com.syang.placitum.build;

import net.minecraft.world.level.block.state.BlockState;

/**
 * What a ground reading means when there is no ground to read.
 *
 * <p>Lived on WallGeometry until the wall was removed, which is a poor reason for a constant to
 * live anywhere. Every planner needs it: a column under water, in an unloaded chunk, or already
 * built on has no height worth freezing, and a build has to be able to leave a gap there rather
 * than guess.
 */
public final class Ground {

    /** No usable ground here. Skip this column. */
    public static final int SKIP = Integer.MIN_VALUE;

    private Ground() {}

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
}
