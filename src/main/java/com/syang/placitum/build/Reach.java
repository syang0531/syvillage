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
 * <p>The plan says where a street would go if the world were flat. The world is not flat, and the
 * difference used to stop nothing: streets were laid across a lake and out the other side, and a
 * cottage went up on an island nobody could reach because the plan said there was a lot there.
 *
 * <p>So the plan is filtered through a walk. A step between neighbouring columns is allowed when
 * neither is under water and the ground rises or falls by no more than one block - which is the
 * rule the game itself uses for getting somewhere without swimming it or jumping down it.
 *
 * <p>Two walks, because a street and a doorstep are not the same question.
 *
 * <ul>
 *   <li><b>Streets</b> spread along the plan's own road lines only. A road column is paved when
 *       the street can be walked to it <em>along the street</em>, so the road breaks at the water
 *       and breaks at the cliff rather than reappearing on the far side of one. The road is three
 *       wide and one lane getting through is enough to carry the rest - which is what makes a
 *       road round the shoulder of a hill work.
 *   <li><b>Ground</b> spreads from every paved street over anything walkable, which is what a
 *       lamp post in a margin and a lot in the middle of a block need: not "is there a road
 *       here" but "could somebody walk here from a road".
 * </ul>
 *
 * <p>That makes the failures read the way a player expects. A road stops at the shore. An island
 * inside the claim stays wild. A shelf three blocks up is left alone. And when somebody bridges
 * the water or ramps the shelf, the walk gets through on the next pass and the town carries on by
 * itself - no command, no rebuild, just ground that is now walkable.
 */
public final class Reach {

    private final Set<Long> streets;
    private final Set<Long> ground;

    private Reach(Set<Long> streets, Set<Long> ground) {
        this.streets = streets;
        this.ground = ground;
    }

    /** Whether the street network gets to this column without leaving the street network. */
    public boolean street(BlockPos pos) {
        return streets.contains(key(pos.getX(), pos.getZ()));
    }

    /** Whether somebody could walk here from a street. */
    public boolean has(int x, int z) {
        return ground.contains(key(x, z));
    }

    public boolean has(BlockPos pos) {
        return has(pos.getX(), pos.getZ());
    }

    /** How far the streets get, in columns. The number to look at when nothing is happening. */
    public int streetSize() {
        return streets.size();
    }

    public int size() {
        return ground.size();
    }

    public static Reach from(ServerLevel level, Settlement settlement, int phase) {
        BlockPos bell = settlement.center();
        int limit = TownPlan.phaseReach(phase);

        // The bell's own crossroads, seeded whole. The bell block sits on the ground and reads a
        // block higher than the ground beside it, so starting from that one column alone would
        // have the walk refuse to step off the thing it started on.
        Set<Long> seed = new HashSet<>();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                seed.add(key(bell.getX() + dx, bell.getZ() + dz));
            }
        }
        Set<Long> streets = walk(level, bell, limit, seed, true);
        return new Reach(streets, walk(level, bell, limit, streets, false));
    }

    /** A settlement that reaches nowhere, for the places that have no world to ask. */
    public static Reach nothing() {
        return new Reach(Set.of(), Set.of());
    }

    /**
     * Breadth-first over columns, one block of rise or fall at a time.
     *
     * <p>{@code onRoadsOnly} is the difference between the two walks: the street walk may not
     * leave the plan's road lines, so a road that cannot be reached by road is not laid, however
     * easy it would be to walk there across somebody's field.
     */
    private static Set<Long> walk(ServerLevel level, BlockPos bell, int limit, Set<Long> seed,
            boolean onRoadsOnly) {
        Set<Long> seen = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();

        for (long start : seed) {
            int x = (int) (start >> 32);
            int z = (int) start;
            int ground = ground(level, x, z);
            if (ground != Ground.SKIP && seen.add(start)) {
                queue.add(new int[] {x, z, ground});
            }
        }
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = at[0] + step[0];
                int z = at[1] + step[1];
                BlockPos pos = new BlockPos(x, bell.getY(), z);
                if (Math.abs(x - bell.getX()) > limit || Math.abs(z - bell.getZ()) > limit
                        || seen.contains(key(x, z))
                        || (onRoadsOnly && !TownPlan.onRoad(pos, bell))) {
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
        return seen;
    }

    /**
     * Ground height, or {@link Ground#SKIP} where the walk cannot go.
     *
     * <p>Unloaded counts as unreachable rather than as a wall: the walk stops there and picks the
     * ground up again the next time somebody is standing near enough to load it.
     */
    private static int ground(ServerLevel level, int x, int z) {
        return level.hasChunkAt(new BlockPos(x, 0, z))
                ? GridSurvey.groundOrSkip(level, x, z) : Ground.SKIP;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
