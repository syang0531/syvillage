package com.syang.syvillage.build;

import net.minecraft.core.BlockPos;

/**
 * A column of ground a build has an interest in, and how much has to come off the top of it.
 *
 * <p>Four numbers: where the column is, where its ground is, and the highest block that must be
 * cut out of it. Streets and lamp posts are a scattering of positions rather than a shape
 * measured from a corner, so they need the positions frozen anyway; a house and a field need the
 * clearance and know their own shape. One record covers both.
 *
 * <p>Frozen when the placement is confirmed, for the same reason the ground profile is: an
 * expansion that has to look at the world again is not a pure function.
 */
public record Spans(int x, int z, int base, int top) {

    /** Nothing grows here, so nothing is cut. */
    public boolean clear() {
        return base == Ground.SKIP || top <= base;
    }

    public BlockPos at(int y) {
        return new BlockPos(x, y, z);
    }
}
