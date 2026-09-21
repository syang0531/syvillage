package com.syang.syvillage.build;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Which ground somebody could walk to from here.
 *
 * <p>A step between neighbouring columns is allowed when neither is under water and the ground
 * rises or falls by no more than one block - the game's own rule for getting somewhere without
 * jumping. What counts as ground at all is {@link Ground#unfit}: water and lava, ice (a lantern
 * melts it), powder snow (a covered hole).
 *
 * <p>There used to be two walks, because a street and a doorstep were different questions. The
 * streets are gone - a path is a shovel's work and the player lays it where they want it - so
 * one walk answers the only question left: <em>could somebody walk here</em>. That is what the
 * dark survey needs, and it is the whole of it.
 *
 * <p>The failures still read the way a player expects. An island inside the radius stays wild;
 * a cliff three blocks up is not ours to worry about. And when somebody bridges the water, the
 * walk gets through on the next pass without being told.
 */
public final class Reach {

    private final Set<Long> ground;

    private Reach(Set<Long> ground) {
        this.ground = ground;
    }

    /** Whether somebody could walk here from the centre. */
    public boolean has(int x, int z) {
        return ground.contains(key(x, z));
    }

    public boolean has(BlockPos pos) {
        return has(pos.getX(), pos.getZ());
    }

    /** Every column the walk got to. The number to look at when nothing is happening. */
    public Set<Long> columns() {
        return ground;
    }

    public int size() {
        return ground.size();
    }

    /** A walk that reaches nowhere, for the places that have no world to ask. */
    public static Reach nothing() {
        return new Reach(Set.of());
    }

    /**
     * Everything walkable within {@code radius} columns of {@code centre}, in both axes.
     *
     * <p>The centre is seeded three by three rather than as one column. A bell sits on the
     * ground and reads a block higher than the ground beside it, so a walk starting from that
     * one column refuses to step off the thing it started on.
     */
    public static Reach from(ServerLevel level, BlockPos centre, int radius) {
        Heights heights = new Heights(level);
        Set<Long> seen = new HashSet<>();
        Deque<Step> queue = new ArrayDeque<>();

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int x = centre.getX() + dx;
                int z = centre.getZ() + dz;
                int ground = heights.at(x, z);
                if (ground != Ground.SKIP && seen.add(key(x, z))) {
                    queue.add(new Step(x, z, ground));
                }
            }
        }
        while (!queue.isEmpty()) {
            Step at = queue.poll();
            for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = at.x() + step[0];
                int z = at.z() + step[1];
                if (Math.abs(x - centre.getX()) > radius || Math.abs(z - centre.getZ()) > radius
                        || seen.contains(key(x, z))) {
                    continue;
                }
                int ground = heights.at(x, z);
                if (ground == Ground.SKIP || Math.abs(ground - at.ground()) > 1) {
                    continue;   // water, a cliff, or ground nobody has loaded
                }
                seen.add(key(x, z));
                queue.add(new Step(x, z, ground));
            }
        }
        return new Reach(seen);
    }

    /** A column the walk has got to. */
    private record Step(int x, int z, int ground) {}

    /**
     * Ground heights, read once each.
     *
     * <p>The walk asks for the same column from up to four directions, and reading the ground
     * means a heightmap lookup and a walk down through whatever is growing on it. Over a couple
     * of hundred columns square that is enough work to be worth not doing twice.
     */
    private static final class Heights {

        private final ServerLevel level;
        private final Map<Long, Integer> known = new HashMap<>();

        private Heights(ServerLevel level) {
            this.level = level;
        }

        /** Ground height, or {@link Ground#SKIP} where the walk cannot go. */
        int at(int x, int z) {
            return known.computeIfAbsent(key(x, z), unused ->
                    level.hasChunkAt(new BlockPos(x, 0, z))
                            ? Terrain.groundOrSkip(level, x, z)
                            : Ground.SKIP);   // unloaded: stop here and pick it up next time
        }
    }

    /** A column as one number, so the walk can keep a set of them cheaply. */
    public static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    public static int keyX(long key) {
        return (int) (key >> 32);
    }

    public static int keyZ(long key) {
        return (int) key;
    }
}
