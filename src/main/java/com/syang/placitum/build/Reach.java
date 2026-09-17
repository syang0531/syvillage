package com.syang.placitum.build;

import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.Settlement;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
 * neither is under water and the ground rises or falls by no more than one block.
 *
 * <p>Two walks, because a street and a doorstep are not the same question.
 *
 * <ul>
 *   <li><b>Streets</b> spread along the plan's own road lines only, so the road breaks at the
 *       water and breaks at the cliff rather than reappearing on the far side of one. They also
 *       stop on ground that is merely <em>rolling</em>: see {@link #uneven}. That is not about
 *       whether you could walk there - you plainly could - but about what a street laid over it
 *       looks like, which is a paved ribbon draped over a hillside that no one would ever build.
 *   <li><b>Ground</b> spreads from every paved street over anything walkable, which is what a
 *       lamp post in a margin and a lot in the middle of a block need: not "is there a road
 *       here" but "could somebody walk here from a road".
 * </ul>
 *
 * <p>That makes the failures read the way a player expects. A road stops at the shore, and at the
 * cliff, and partway up a hill. An island inside the claim stays wild. And when somebody bridges
 * the water or terraces the hill, the walk gets through on the next pass and the town carries on
 * by itself - no command, no rebuild, just ground that has become the kind you build a town on.
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
        int limit = TownPlan.reachOf(settlement, phase);
        Terrain terrain = new Terrain(level);

        // The bell's own crossroads, seeded whole. The bell block sits on the ground and reads a
        // block higher than the ground beside it, so starting from that one column alone would
        // have the walk refuse to step off the thing it started on.
        Set<Long> seed = new HashSet<>();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                seed.add(key(bell.getX() + dx, bell.getZ() + dz));
            }
        }
        // The streets stop at the last road; the ground has to carry on past the wall, because
        // a gatehouse and a tower both stand two blocks proud of it. Stopping at the wall's own
        // outer face made every gate and every tower unbuildable - their outermost columns were
        // simply not in the set - and the town came out with four gaps and no corners.
        Set<Long> streets = walk(terrain, bell, limit, seed, true);
        int toTheWall = Math.max(limit, TownPlan.gateOuter(settlement));
        return new Reach(streets, walk(terrain, bell, toTheWall, streets, false));
    }

    /** A settlement that reaches nowhere, for the places that have no world to ask. */
    public static Reach nothing() {
        return new Reach(Set.of(), Set.of());
    }

    /** A column the walk has got to, and how long it has been climbing to get there. */
    private record Step(int x, int z, int ground, int climb, long from) {}

    /**
     * Breadth-first over columns, one block of rise or fall at a time.
     *
     * <p>{@code onRoadsOnly} is the difference between the two walks: the street walk may not
     * leave the plan's road lines, so a road that cannot be reached by road is not laid, however
     * easy it would be to walk there across somebody's field. It is also the only one that
     * counts the climb - a lamp on a hillside is worth having and a street up it is not.
     */
    private static Set<Long> walk(Terrain terrain, BlockPos bell, int limit, Set<Long> seed,
            boolean onRoadsOnly) {
        int allowed = PlacitumConfig.MAX_ROAD_CLIMB.get();
        Set<Long> seen = new HashSet<>();
        Map<Long, Long> cameFrom = new HashMap<>();
        Set<Long> level = new HashSet<>();
        Deque<Step> queue = new ArrayDeque<>();

        for (long start : seed) {
            int x = (int) (start >> 32);
            int z = (int) start;
            int ground = terrain.at(x, z);
            if (ground != Ground.SKIP && seen.add(start)) {
                level.add(start);
                queue.add(new Step(x, z, ground, 0, start));
            }
        }
        while (!queue.isEmpty()) {
            Step at = queue.poll();
            for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = at.x() + step[0];
                int z = at.z() + step[1];
                BlockPos pos = new BlockPos(x, bell.getY(), z);
                if (Math.abs(x - bell.getX()) > limit || Math.abs(z - bell.getZ()) > limit
                        || seen.contains(key(x, z))
                        || (onRoadsOnly && !TownPlan.onRoad(pos, bell))) {
                    continue;
                }
                int ground = terrain.at(x, z);
                if (ground == Ground.SKIP || Math.abs(ground - at.ground()) > 1) {
                    continue;   // water, a cliff, or ground nobody has loaded
                }
                int climb = at.climb();
                if (onRoadsOnly && TownPlan.runsAlongRoad(step[0], step[1], pos, bell)) {
                    climb = uneven(terrain, at, step, bell) ? climb + 1 : 0;
                    if (climb > allowed) {
                        continue;   // rolling ground: the street gives up rather than ride it
                    }
                }
                long here = key(x, z);
                seen.add(here);
                cameFrom.put(here, key(at.x(), at.z()));
                if (climb == 0) {
                    level.add(here);
                }
                queue.add(new Step(x, z, ground, climb, here));
            }
        }
        return onRoadsOnly ? leadingSomewhere(seen, cameFrom, level) : seen;
    }

    /**
     * The walk with its dead-end climbs pruned off.
     *
     * <p>A street that climbs two blocks up a hill and stops because the third was too much is a
     * ramp to nowhere. The walk cannot know that while it is walking - it finds out by failing to
     * get any further - so the ramps come off afterwards.
     *
     * <p>A column is kept if it is level ground, or if the walk got from it to level ground
     * later: every column on the path back from somewhere level is on a street that goes
     * somewhere. Everything else is the tail of a climb that ran out.
     */
    public static Set<Long> leadingSomewhere(Set<Long> seen, Map<Long, Long> cameFrom,
            Set<Long> level) {
        Set<Long> keep = new HashSet<>(level);
        for (long at : level) {
            long step = at;
            Long parent;
            // Back to the bell, or to the first column already known to lead somewhere.
            while ((parent = cameFrom.get(step)) != null && keep.add(parent)) {
                step = parent;
            }
        }
        keep.retainAll(seen);
        return keep;
    }

    /**
     * Whether the road's full width changes height over this step.
     *
     * <p>All three lanes of it, not just the one being walked. A street running along the
     * contour of a hill has one lane that is dead level and two that are not, and paving it on
     * the strength of the level one gives a road with a step down its length - which is the
     * thing that looks wrong from the ground.
     *
     * <p>Any change counts, up or down. Rolling ground is as unbuildable-looking as a slope, and
     * a rule that only counted climbing would let a street ripple across a field of hummocks.
     */
    private static boolean uneven(Terrain terrain, Step from, int[] step, BlockPos bell) {
        int px = step[0] != 0 ? 0 : 1;
        int pz = step[0] != 0 ? 1 : 0;
        for (int lane = -1; lane <= 1; lane++) {
            int x = from.x() + px * lane;
            int z = from.z() + pz * lane;
            // The road's own lanes and nothing else. One block further out is the margin, where
            // the lamps stand, and a lamp reads three blocks higher than the ground it is on -
            // so counting it made the town's own street lights break the street beside them.
            if (!(px != 0 ? TownPlan.isRoad(x, bell.getX()) : TownPlan.isRoad(z, bell.getZ()))) {
                continue;
            }
            int before = terrain.at(x, z);
            int after = terrain.at(x + step[0], z + step[1]);
            if (before == Ground.SKIP || after == Ground.SKIP) {
                continue;   // that lane is in the water; it has nothing to say about the slope
            }
            if (before != after) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ground heights, read once each.
     *
     * <p>The walk asks for the same column from up to four directions and the slope test asks
     * for six columns a step, and reading the ground means a heightmap lookup and a walk down
     * through whatever is growing on it. Two walks over a hundred and ninety columns square is
     * enough work to be worth not doing twice.
     */
    private static final class Terrain {

        private final ServerLevel level;
        private final Map<Long, Integer> known = new HashMap<>();

        private Terrain(ServerLevel level) {
            this.level = level;
        }

        /** Ground height, or {@link Ground#SKIP} where the walk cannot go. */
        int at(int x, int z) {
            return known.computeIfAbsent(key(x, z), unused ->
                    level.hasChunkAt(new BlockPos(x, 0, z))
                            ? GridSurvey.groundOrSkip(level, x, z)
                            : Ground.SKIP);   // unloaded: stop here and pick it up next time
        }
    }

    /** A column as one number, so the walk can keep a set of them cheaply. */
    public static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
