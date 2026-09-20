package com.syang.syvillage.build;

import com.syang.syvillage.config.SyVillageConfig;
import com.syang.syvillage.data.BuildOp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * Cutting down what is growing where something is about to be built.
 *
 * <p>The ground reading walks down past logs and leaves on purpose - a tree is not terrain, and
 * letting one veto a site meant 159 of 441 cells came back unbuildable in a wooded village. The
 * consequence nobody had followed through: the site is then judged flat and built on with the
 * tree still standing in it, so a cottage went up through a trunk and a street was laid under
 * one.
 *
 * <p>So the same rule has to be finished at the other end. If we are going to ignore a tree when
 * deciding, we have to fell it before building. A tree on ground we are not going to build on is
 * left exactly where it is - which is most of them, because a lot has to be dead flat and most
 * ground is not.
 *
 * <p>Frozen at plan time like every other reading, so expansion stays pure.
 */
public final class Clearance {

    private Clearance() {}

    /**
     * The highest block that has to come out of this column, or the ground when nothing does.
     *
     * <p>Reported as the top rather than as a count of blocks so that a column with nothing
     * growing on it costs zero ops rather than a stack of pointless writes to air - a road batch
     * is 64 columns and clearing each one blindly would be eight hundred wasted blocks of
     * building time.
     */
    public static int topOf(ServerLevel level, int x, int z, int ground) {
        if (ground == Ground.SKIP) {
            return Ground.SKIP;
        }
        int ceiling = ground + SyVillageConfig.CLEAR_HEIGHT.get();
        int top = ground;
        for (int y = ground + 1; y <= ceiling; y++) {
            if (GridSurvey.isCuttable(level.getBlockState(new BlockPos(x, y, z)))) {
                top = y;
            }
        }
        return top;
    }

    /** A span per column, in the order the columns were given. */
    public static List<Spans> spans(ServerLevel level, List<BlockPos> columns,
            List<Integer> grounds) {
        List<Spans> out = new ArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            int x = columns.get(i).getX();
            int z = columns.get(i).getZ();
            out.add(new Spans(x, z, grounds.get(i), topOf(level, x, z, grounds.get(i))));
        }
        return List.copyOf(out);
    }

    /** Spans for columns whose ground has to be read here as well. */
    public static List<Spans> spans(ServerLevel level, List<BlockPos> columns) {
        List<Integer> grounds = new ArrayList<>(columns.size());
        for (BlockPos column : columns) {
            grounds.add(GridSurvey.groundOrSkip(level, column.getX(), column.getZ()));
        }
        return spans(level, columns, grounds);
    }

    /**
     * Air from just above the ground to the top of what is growing there.
     *
     * <p>Everything in between, not only the blocks that were growth: a trunk with a gap in it
     * is still a trunk, and leaving the gap means reading the world again to find out where it
     * was, which is the dependency the frozen profile exists to remove.
     *
     * <p>Positions the caller is going to write itself are left out. Two ops for one position is
     * a shape decided by insertion order, which this file has been bitten by before.
     */
    public static List<BuildOp> ops(List<Spans> spans, Set<BlockPos> claimed) {
        List<BuildOp> out = new ArrayList<>();
        for (Spans span : spans) {
            if (span.clear()) {
                continue;
            }
            for (int y = span.base() + 1; y <= span.top(); y++) {
                BlockPos pos = span.at(y);
                if (!claimed.contains(pos)) {
                    out.add(new BuildOp(pos, Blocks.AIR.defaultBlockState()));
                }
            }
        }
        return out;
    }
}
