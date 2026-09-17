package com.syang.placitum.build;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.Craft;
import com.syang.placitum.data.Settlement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A gatehouse, where one of the bell's two roads leaves the town.
 *
 * <p>Four of them, and nobody works out where. The bell stands in the middle of a crossroads and
 * those roads run to the wall, so a gate is where the wall is and the bell's road still is -
 * which, because the roads are centred on the bell, puts each one dead centre of its side.
 *
 * <p>It is eight tall against the wall's four, and that is not decoration. An arch somebody can
 * walk through needs three blocks of headroom, the wall's walkway floor is at three, and those
 * two cannot both be true. So the gatehouse carries the walkway <em>over</em> the arch: it
 * arrives at the wall's level, climbs four steps inside the gatehouse, crosses on the deck, and
 * comes down the other side. That is what a gatehouse is for, and it is why this could not be
 * part of the wall.
 *
 * <pre>
 *   h8   #_#_#_#_#_#_#_#_#     crenellated parapet
 *   h7   ==================    deck, and the walkway crosses here
 *   h6   ####          ####
 *   h5   ####    _     ####    arch crown
 *   h4   ###    ___     ###
 *   h1-3 ###    ___     ###    the way through, three wide
 *         a=-4   0    a=+4
 * </pre>
 *
 * <p>No doors. The way through is left open on purpose - a player who wants it shut has better
 * ideas about how than we do, and a gate that opened for a zombie would be worse than none.
 */
public final class GatePlan {

    public static final Identifier GATEHOUSE =
            Identifier.fromNamespaceAndPath(Placitum.MODID, "wall/gatehouse");

    /**
     * Blocks across the road, ramps included: nine of gatehouse and four of approach either side.
     *
     * <p>The climb used to be cut into the gatehouse, which cost the deck two of its rows and
     * made the one place worth standing the one place there was nowhere to stand. It is on the
     * wall now, so the wall arrives at the deck's height instead of four blocks under it.
     */
    public static final int WIDE = TownPlan.GATE_WIDTH + 2 * TownPlan.RAMP;

    /** The gatehouse proper, in the middle of that. */
    public static final int HOUSE = TownPlan.GATE_WIDTH;

    /** Blocks along the road: the wall's four, and two of gatehouse either side of it. */
    public static final int DEEP = 8;

    /** To the top of the parapet. Twice the wall, which is what buys the arch its headroom. */
    public static final int TALL = TownPlan.TOWER;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private GatePlan() {}

    /** The four outward directions a gate faces, in a fixed order. */
    public static List<Direction> sides() {
        return List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    }

    /**
     * Where the gatehouse's first column sits: on the road's centre line, two blocks inside the
     * wall's inner face.
     */
    public static BlockPos anchorOf(Settlement settlement, Direction side) {
        int start = TownPlan.wallInner(settlement) - (DEEP - TownPlan.WALL) / 2;
        return settlement.center().relative(side, start);
    }

    /** Every column of the gatehouse, ordered along the road and then across it. */
    public static List<BlockPos> footprint(BlockPos anchor, Direction side) {
        Direction across = side.getClockWise();
        List<BlockPos> out = new ArrayList<>(DEEP * WIDE);
        for (int d = 0; d < DEEP; d++) {
            for (int a = -(WIDE / 2); a <= WIDE / 2; a++) {
                out.add(anchor.relative(side, d).relative(across, a));
            }
        }
        return out;
    }

    public static Optional<BuildRecipe> plan(ServerLevel level, Settlement settlement,
            Direction side, Reach reach) {
        if (!settlement.walled()) {
            return Optional.empty();
        }
        BlockPos anchor = anchorOf(settlement, side);
        List<BlockPos> columns = footprint(anchor, side);
        List<Integer> profile = new ArrayList<>(columns.size());

        // Only the columns this build actually stands on. A gatehouse footprint is seventeen
        // across and a tower frame is twelve square because that is the box the shape fits in,
        // not because we build on all of it: thirty-two columns of the one and forty-eight of
        // the other are ground it never puts a block in. Demanding those be walkable refused
        // every gate in a finished town over sixteen columns of hillside outside the wall.
        //
        // The ground is still read for all of them, because expand indexes the profile by
        // footprint position and the indices have to line up. It is just not a veto.
        List<BlockPos> ours = new ArrayList<>();
        List<Integer> oursGround = new ArrayList<>();

        for (int i = 0; i < columns.size(); i++) {
            BlockPos column = columns.get(i);
            boolean mine = touches(i);
            if (!level.hasChunkAt(column)) {
                if (mine) {
                    return Optional.empty();
                }
                profile.add(Ground.SKIP);   // never read; the chunk is not ours to load
                continue;
            }
            int ground = GridSurvey.groundOrSkip(level, column.getX(), column.getZ());
            profile.add(ground);
            if (!mine) {
                continue;
            }
            if (ground == Ground.SKIP || !reach.has(column)) {
                return Optional.empty();   // a gate half in a lake is worse than a gap
            }
            ours.add(column);
            oursGround.add(ground);
        }
        if (standing(level, settlement.craft(), columns, profile)) {
            return Optional.empty();
        }
        Placitum.LOGGER.debug("Planned a {} gatehouse for '{}' facing {}",
                settlement.craft().getSerializedName(), settlement.name(), side);

        return Optional.of(new BuildRecipe(GATEHOUSE, anchor, rotationOf(side),
                settlement.craft().paletteId(), List.copyOf(profile),
                new BlockPos(WIDE, TALL, DEEP),
                // Felled only where we build, which is the other half of the same rule: a tree
                // on ground we are not going to build on is left exactly where it is.
                Spans.encode(Clearance.spans(level, ours, oursGround))));
    }

    /**
     * Whether this gatehouse is already up.
     *
     * <p>Asked of the deck rather than of the ground, because the ground under a gatehouse reads
     * as the top of the gatehouse once one is there - the same trick the wall got caught by. The
     * deck is at a known height above a known floor, so it is a fair question to ask twice.
     */
    private static boolean standing(ServerLevel level, Craft craft, List<BlockPos> columns,
            List<Integer> profile) {
        int floor = houseFloor(profile);
        BlockPos middle = columns.get(columns.size() / 2);
        // Asked for our own masonry rather than for "not air", so that a tree standing where the
        // deck will go does not read as a finished gatehouse.
        return level.getBlockState(new BlockPos(middle.getX(), floor + TALL - 1, middle.getZ()))
                .is(craft.wall().getBlock());
    }

    /**
     * The level the gatehouse stands at, taken from the gatehouse and not from its ramps.
     *
     * <p>The ramps run four blocks further out along the wall in each direction, and letting
     * their ground vote would lift the whole gate onto whatever the highest hummock out there
     * happened to be.
     */
    private static int houseFloor(List<Integer> profile) {
        List<Integer> house = new ArrayList<>();
        for (int i = 0; i < profile.size(); i++) {
            if (Math.abs(i % WIDE - WIDE / 2) <= HOUSE / 2) {
                house.add(profile.get(i));
            }
        }
        return Ground.highest(house);
    }

    public static Rotation rotationOf(Direction side) {
        return switch (side) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public static Direction sideOf(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> Direction.EAST;
            case CLOCKWISE_180 -> Direction.SOUTH;
            case COUNTERCLOCKWISE_90 -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }

    /**
     * The gatehouse, carved rather than assembled.
     *
     * <p>Every column is filled to the deck and then three things are cut out of it: the way
     * through, the stairwell that carries the walkway over the way through, and the headroom
     * above both. Carving is easier to read than stacking here, because what makes a gatehouse a
     * gatehouse is the holes.
     */
    public static List<BuildOp> expand(BuildRecipe recipe) {
        List<Integer> profile = recipe.groundProfile();
        Direction side = sideOf(recipe.rotation());
        List<BlockPos> columns = footprint(recipe.anchor(), side);
        if (profile.size() != columns.size()) {
            return List.of();
        }
        int floor = houseFloor(profile);
        if (floor == Ground.SKIP) {
            return List.of();
        }
        BlockState stone = Craft.fromPalette(recipe.palette()).wall();
        List<BuildOp> ops = new ArrayList<>();

        for (int i = 0; i < columns.size(); i++) {
            BlockPos column = columns.get(i);
            int d = i / WIDE;
            int a = i % WIDE - WIDE / 2;

            boolean bears = Math.abs(a) <= HOUSE / 2 || inWall(d);
            if (bears) {
                // Foundation, up to and including the floor level. Stopping one short left every
                // column below the highest with a hole under it, so the gatehouse stood on the
                // one corner that happened to be level with it and floated over the rest.
                //
                // Only under what is actually built: the rest of a ramp's width is open ground
                // out here, and filling it would be a plinth with nothing on it.
                for (int y = profile.get(i) + 1; y <= floor; y++) {
                    ops.add(new BuildOp(new BlockPos(column.getX(), y, column.getZ()), stone));
                }
            }
            for (int h = 1; h <= TALL; h++) {
                BlockState state = blockAt(d, a, h, stone);
                if (state != null) {
                    ops.add(new BuildOp(new BlockPos(column.getX(), floor + h, column.getZ()),
                            state));
                }
            }
        }
        // Everything this gatehouse writes, so that felling what grows on the site does not
        // rub out the gatehouse. An empty set here built the structure and cleared it in the
        // same job, and the settlement asked for it again a second later, for ever.
        ops.addAll(Clearance.ops(Spans.decode(recipe.gates()), written(ops)));
        ops.sort(java.util.Comparator.comparingInt((BuildOp op) -> op.pos().getY())
                .thenComparingInt(op -> op.pos().getX())
                .thenComparingInt(op -> op.pos().getZ()));
        return List.copyOf(ops);
    }

    /**
     * Why a footprint cannot be built on, counted by reason.
     *
     * <p>A gatehouse is a hundred and thirty-six columns and a tower a hundred and forty-four,
     * and one bad column stops the whole thing. "It did not build" is useless at that size - it
     * has been guessed at three times now - so the settlement says which columns and what was
     * wrong with them. Principle 11, applied to something bigger than a lot.
     */
    public static String status(ServerLevel level, Settlement settlement, Direction side,
            Reach reach) {
        List<BlockPos> columns = footprint(anchorOf(settlement, side), side);
        return standing(level, settlement.craft(), columns, grounds(level, columns))
                ? "standing" : trouble(level, columns, reach, GatePlan::touches);
    }

    /** The ground under a footprint, with water marked rather than guessed at. */
    static List<Integer> grounds(ServerLevel level, List<BlockPos> columns) {
        List<Integer> out = new ArrayList<>(columns.size());
        for (BlockPos column : columns) {
            out.add(level.hasChunkAt(column)
                    ? GridSurvey.groundOrSkip(level, column.getX(), column.getZ())
                    : Ground.SKIP);
        }
        return out;
    }

    /**
     * @param ours whether the build would put a block in that column, by footprint index
     */
    public static String trouble(ServerLevel level, List<BlockPos> columns, Reach reach,
            IntPredicate ours) {
        int unloaded = 0;
        int unreachable = 0;
        int wet = 0;
        int blocking = 0;
        BlockPos first = null;

        for (int i = 0; i < columns.size(); i++) {
            BlockPos column = columns.get(i);
            boolean bad = true;
            if (!level.hasChunkAt(column)) {
                unloaded++;
            } else if (GridSurvey.groundOrSkip(level, column.getX(), column.getZ())
                    == Ground.SKIP) {
                wet++;
            } else if (!reach.has(column)) {
                unreachable++;
            } else {
                bad = false;
            }
            if (bad && ours.test(i)) {
                blocking++;
                if (first == null) {
                    first = column;
                }
            }
        }
        if (unloaded + unreachable + wet == 0) {
            return "nothing";
        }
        // The count alone has now been read wrong twice. Eight structures all reporting sixteen
        // says the number is not terrain, but it does not say whether those sixteen are ground
        // the gatehouse would stand on or ground it was only going to walk past - and those are
        // a bad site and an over-strict rule, which want opposite fixes.
        return unreachable + " unreachable, " + wet + " water, " + unloaded + " unloaded of "
                + columns.size() + " - " + blocking + " under the build"
                + (first == null ? "" : ", first at " + first.getX() + "," + first.getZ());
    }

    /** The positions a build has already spoken for. */
    static java.util.Set<BlockPos> written(List<BuildOp> ops) {
        java.util.Set<BlockPos> out = new java.util.HashSet<>(ops.size());
        for (BuildOp op : ops) {
            out.add(op.pos());
        }
        return out;
    }

    /**
     * Whether the build writes anything at all in this column of the footprint.
     *
     * <p>A gatehouse is seventeen columns across but only nine of them are gatehouse; the eight
     * outside are a ramp's width of ordinary ground, and out there only the four columns the
     * rampart occupies are ours - {@link #onRamp} returns null for the rest and expand leaves
     * them exactly as it found them. Thirty-two of a hundred and thirty-six.
     *
     * <p>This exists so the diagnostic can say whether what is blocking a gatehouse is ground it
     * would build on, which is a different problem from ground it was only going to walk past.
     */
    public static boolean touches(int index) {
        int d = index / WIDE;
        int a = index % WIDE - WIDE / 2;
        return Math.abs(a) <= HOUSE / 2 || inWall(d);
    }

    /**
     * What stands at one place in the gatehouse, or null to leave the world alone.
     *
     * <p>Heights are counted from the floor: 1 to 6 is the body, 7 is the deck people walk on,
     * 8 is the parapet.
     */
    private static BlockState blockAt(int d, int a, int h, BlockState stone) {
        if (Math.abs(a) > HOUSE / 2) {
            return onRamp(d, a, h, stone);
        }
        boolean onRoad = Math.abs(a) <= TownPlan.ROAD / 2;
        if (h == TALL) {
            // Crenellated all round the deck, except where the two ramps arrive.
            boolean edge = d == 0 || d == DEEP - 1 || Math.abs(a) == HOUSE / 2;
            boolean mouth = Math.abs(a) == HOUSE / 2 && inWalkway(d);
            return edge && !mouth && (d + a) % 2 == 0 ? stone : AIR;
        }
        if (h == TALL - 1) {
            return stone;   // the deck, solid the whole way across now
        }
        if (onRoad && h <= archHeight(a)) {
            return AIR;     // the way through
        }
        return stone;
    }

    /**
     * The wall, climbing to meet the gatehouse.
     *
     * <p>Only the four columns the rampart itself occupies. The rest of a gatehouse's width is
     * ordinary ground out here and is left as it was.
     */
    private static BlockState onRamp(int d, int a, int h, BlockState stone) {
        if (!inWall(d)) {
            return null;
        }
        boolean parapet = !inWalkway(d);
        int top = rampTop(a, parapet);
        if (h > top) {
            return AIR;
        }
        if (parapet && h == top && (d + a) % 2 != 0) {
            return AIR;   // crenellated as it climbs, like the rest of the rampart
        }
        return stone;
    }

    /** The four columns of the rampart, as indices into the gatehouse's depth. */
    private static boolean inWall(int d) {
        int outerFace = (DEEP + TownPlan.WALL) / 2 - 1;
        return d <= outerFace && d > outerFace - TownPlan.WALL;
    }

    /** The two of those four you walk on. */
    private static boolean inWalkway(int d) {
        int outerFace = (DEEP + TownPlan.WALL) / 2 - 1;
        return d == outerFace - 1 || d == outerFace - 2;
    }

    /** The arch: three wide and four tall, with a crown over the middle of the road. */
    private static int archHeight(int a) {
        return a == 0 ? TownPlan.WALL_HEIGHT + 1 : TownPlan.WALL_HEIGHT;
    }

    /**
     * How high the wall stands where it is climbing towards the gatehouse.
     *
     * <p>Level with the rest of the rampart at the far end of the ramp, and level with the deck
     * by the time it meets the gatehouse. The parapet rides a block above the walkway the whole
     * way up, so the climb has a handrail rather than an edge.
     */
    private static int rampTop(int a, boolean parapet) {
        int climbed = TownPlan.RAMP - (Math.abs(a) - HOUSE / 2);
        return TownPlan.WALL_HEIGHT - 1 + climbed + (parapet ? 1 : 0);
    }
}
