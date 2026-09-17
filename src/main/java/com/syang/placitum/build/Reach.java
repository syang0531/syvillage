package com.syang.placitum.build;

import com.syang.placitum.data.Settlement;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Which ground a settlement can actually walk to from its bell.
 *
 * <p>The plan says where a street would go if the world were flat. The world is not flat, and
 * until now the difference did not stop anything: streets were laid across a lake and out the
 * other side, and a cottage went up on an island nobody could reach because the plan said there
 * was a lot there.
 *
 * <p>So the plan is filtered through a walk. Starting at the bell, a step is allowed between
 * neighbouring columns when neither is under water and the ground rises or falls by no more than
 * one block - which is the rule the game itself uses for whether you can get there without
 * jumping down a cliff or swimming. Everything the walk does not reach gets nothing: no road, no
 * lamp, no building.
 *
 * <p>That makes the failures read the way a player expects. A road stops at the shore. An island
 * inside the claim stays wild. A shelf three blocks up is left alone. And when somebody bridges
 * the water or ramps the shelf, the walk gets through on the next pass and the town carries on
 * by itself - no command, no rebuild, just ground that is now walkable.
 */
public final class Reach {

    private final Set<Long> walkable;

    private Reach(Set<Long> walkable) {
        this.walkable = walkable;
    }

    /** Whether the settlement can get to this column on foot. */
    public boolean has(int x, int z) {
        return walkable.contains(key(x, z));
    }

    public boolean has(BlockPos pos) {
        return has(pos.getX(), pos.getZ());
    }

    public int size() {
        return walkable.size();
    }

    /**
     * Everything within reach of the bell, out to the given phase.
     *
     * <p>Over all ground, not only over the streets. A lamp stands in the margin and a house
     * stands on a lot, and neither is on a road - what matters is whether a person could walk
     * from the bell to that spot, which is a question about the ground rather than about what we
     * have built on it so far.
     */
    public static Reach from(ServerLevel level, Settlement settlement, int phase) {
        BlockPos bell = settlement.center();
        int limit = TownPlan.phaseReach(phase);
        Set<Long> seen = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();

        // The bell's own crossroads, seeded whole. The bell block itself sits on the ground and
        // reads a block higher than the ground beside it, so starting from that one column alone
        // would have the walk refuse to step off the thing it started on.
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int x = bell.getX() + dx;
                int z = bell.getZ() + dz;
                int ground = ground(level, x, z);
                if (ground != Ground.SKIP && seen.add(key(x, z))) {
                    queue.add(new int[] {x, z, ground});
                }
            }
        }

        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = at[0] + step[0];
                int z = at[1] + step[1];
                if (Math.abs(x - bell.getX()) > limit || Math.abs(z - bell.getZ()) > limit
                        || seen.contains(key(x, z))) {
                    continue;
                }
                int ground = ground(level, x, z);
                if (ground == Ground.SKIP || Math.abs(ground - at[2]) > 1) {
                    continue;   // water, a cliff, or ground nobody has loaded
                }
                seen.add(key(x, z));
                queue.add(new int[] {x, z, ground});
            }
        }
        return new Reach(seen);
    }

    /** A settlement that reaches nowhere, for the places that have no world to ask. */
    public static Reach nothing() {
        return new Reach(Set.of());
    }

    /**
     * Ground height, or {@link Ground#SKIP} where the walk cannot go.
     *
     * <p>Unloaded counts as unreachable rather than as a wall: the walk simply stops there and
     * picks the ground up again the next time somebody is standing near enough to load it.
     */
    private static int ground(ServerLevel level, int x, int z) {
        return level.hasChunkAt(new BlockPos(x, 0, z))
                ? GridSurvey.groundOrSkip(level, x, z) : Ground.SKIP;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
