package com.syang.placitum.build;

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
}
