package com.syang.syvillage.build;

import com.syang.syvillage.config.SyVillageConfig;
import com.syang.syvillage.data.BuildOp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

/**
 * Turning a placement into blocks, and then into blocks the player watches arrive.
 *
 * <p>{@link #expand} is the pure half and has no level in its signature, so it cannot read the
 * world even by accident. A template is static content - read from a jar once and cached - so
 * the same placement gives the same list of ops for ever, which is what makes any of this
 * testable.
 *
 * <p>The other half puts them down. It is laid layer by layer rather than all at once, and that
 * is <b>a flourish, not a queue</b>. Nothing about it is saved: the list lives in this class's
 * memory, and if the server stops or the world moves out from under it the rest goes down at
 * once. There is no resumption, so there is no order to get wrong, no codec, and nothing for a
 * later session to be confused by. Every one of the eight times this project has found that
 * nothing was happening, something with a lifecycle was in the middle of it.
 */
public final class Raise {

    private Raise() {}

    /**
     * Every block the structure puts down, lowest layer first.
     *
     * <p>Sorted, because the flourish goes upwards and an unsorted list would flicker. Sorting
     * ops broke a wall once - the sort was a shuffle - so the guarantee that makes it safe is
     * stated as a test instead of a comment: <b>no position appears twice.</b> A stable sort
     * cannot reorder two ops that never collide.
     */
    public static List<BuildOp> expand(Placement placement) {
        Template template = placement.template();
        List<BuildOp> ops = new ArrayList<>(template.placeAt(placement.origin(),
                placement.rotation(), placement.palette(), placement.floor()));
        ops.sort(Comparator.comparingInt(op -> op.pos().getY()));
        return List.copyOf(ops);
    }

    // ---- the flourish

    private static final class Job {
        private final ServerLevel level;
        private final List<BuildOp> ops;
        private int next;

        private Job(ServerLevel level, List<BuildOp> ops) {
            this.level = level;
            this.ops = ops;
        }

        /** @return true when there is nothing left */
        private boolean lay(int howMany) {
            int until = Math.min(ops.size(), next + howMany);
            for (; next < until; next++) {
                BuildOp op = ops.get(next);
                level.setBlock(op.pos(), op.state(), Block.UPDATE_CLIENTS);
            }
            return next >= ops.size();
        }
    }

    private static final List<Job> RUNNING = new ArrayList<>();

    /** Start laying. Returns how many blocks it will be. */
    public static int begin(ServerLevel level, Placement placement) {
        List<BuildOp> ops = expand(placement);
        RUNNING.add(new Job(level, ops));
        return ops.size();
    }

    /**
     * One tick of laying, for every structure going up anywhere.
     *
     * <p>Bounded by the number of structures a player has started in the last few seconds, and
     * it reads nothing: the ops were settled before the first block went down. This is the
     * difference between a flourish tick and the planning tick that CLAUDE.md forbids.
     */
    public static void tick() {
        if (RUNNING.isEmpty()) {
            return;
        }
        int perTick = SyVillageConfig.RAISE_BLOCKS_PER_TICK.get();
        RUNNING.removeIf(job -> job.lay(perTick));
    }

    /** The world is going away. Finish everything where it stands rather than leaving a shell. */
    public static void finishAll() {
        for (Job job : RUNNING) {
            job.lay(Integer.MAX_VALUE);
        }
        RUNNING.clear();
    }
}
