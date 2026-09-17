package com.syang.placitum.build;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.Craft;
import com.syang.placitum.data.Settlement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    /** Blocks across the road. Odd, so the gate is centred on a road that is itself centred. */
    public static final int WIDE = TownPlan.GATE_WIDTH;

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

        for (BlockPos column : columns) {
            if (!level.hasChunkAt(column) || !reach.has(column)) {
                return Optional.empty();   // the ground it needs is not there, or not walkable
            }
            int ground = GridSurvey.groundOrSkip(level, column.getX(), column.getZ());
            if (ground == Ground.SKIP) {
                return Optional.empty();   // a gate half in a lake is worse than a gap
            }
            profile.add(ground);
        }
        if (standing(level, settlement.craft(), columns, profile)) {
            return Optional.empty();
        }
        Placitum.LOGGER.debug("Planned a {} gatehouse for '{}' facing {}",
                settlement.craft().getSerializedName(), settlement.name(), side);

        return Optional.of(new BuildRecipe(GATEHOUSE, anchor, rotationOf(side),
                settlement.craft().paletteId(), List.copyOf(profile),
                new BlockPos(WIDE, TALL, DEEP),
                Spans.encode(Clearance.spans(level, columns, profile))));
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
        int floor = Ground.highest(profile);
        BlockPos middle = columns.get(columns.size() / 2);
        // Asked for our own masonry rather than for "not air", so that a tree standing where the
        // deck will go does not read as a finished gatehouse.
        return level.getBlockState(new BlockPos(middle.getX(), floor + TALL - 1, middle.getZ()))
                .is(craft.wall().getBlock());
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
        int floor = Ground.highest(profile);
        if (floor == Ground.SKIP) {
            return List.of();
        }
        BlockState stone = Craft.fromPalette(recipe.palette()).wall();
        List<BuildOp> ops = new ArrayList<>();

        for (int i = 0; i < columns.size(); i++) {
            BlockPos column = columns.get(i);
            int d = i / WIDE;
            int a = i % WIDE - WIDE / 2;

            // Foundation, up to and including the floor level. Stopping one short left every
            // column below the highest with a hole under it, so the gatehouse stood on the one
            // corner that happened to be level with it and floated over the rest.
            for (int y = profile.get(i) + 1; y <= floor; y++) {
                ops.add(new BuildOp(new BlockPos(column.getX(), y, column.getZ()), stone));
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

    /** The positions a build has already spoken for. */
    static java.util.Set<BlockPos> written(List<BuildOp> ops) {
        java.util.Set<BlockPos> out = new java.util.HashSet<>(ops.size());
        for (BuildOp op : ops) {
            out.add(op.pos());
        }
        return out;
    }

    /**
     * What stands at one place in the gatehouse, or null to leave the world alone.
     *
     * <p>Heights are counted from the floor: 1 to 6 is the body, 7 is the deck people walk on,
     * 8 is the parapet.
     */
    private static BlockState blockAt(int d, int a, int h, BlockState stone) {
        boolean onRoad = Math.abs(a) <= TownPlan.ROAD / 2;
        boolean inStairwell = d == stairLane(0) || d == stairLane(1);

        if (h == TALL) {
            // Crenellated all round the deck, but not over the stairwell, which is where the
            // walkway climbs in and out and wants its head.
            boolean edge = d == 0 || d == DEEP - 1 || Math.abs(a) == WIDE / 2;
            return edge && !inStairwell && (d + a) % 2 == 0 ? stone : AIR;
        }
        if (inStairwell) {
            // The way through goes through here too. The walkway's two columns are the ones the
            // road passes under, so taking the stairwell's word for it would have walled the
            // gate shut with the staircase that exists to get over it.
            if (onRoad && h <= archHeight(a)) {
                return AIR;
            }
            // Four steps up and four down, and everything above each step is air so that
            // somebody can stand on it.
            return h <= stepHeight(a) ? stone : AIR;
        }
        if (h == TALL - 1) {
            return stone;   // the deck, over everything that is not the stairwell
        }
        if (onRoad && h <= archHeight(a)) {
            return AIR;     // the way through
        }
        return stone;
    }

    /** The two columns of the wall's walkway, as indices along the gatehouse. */
    private static int stairLane(int which) {
        int outerFace = (DEEP + TownPlan.WALL) / 2 - 1;
        return outerFace - 1 - which;
    }

    /**
     * How high the walkway has climbed by this point across the gatehouse.
     *
     * <p>It comes in at the wall's own level and has to be on the deck by the time it is over
     * the arch, so it gains a block for every block inward. Four steps, because the deck is four
     * above the walkway - which is the arch's headroom, paid for.
     */
    private static int stepHeight(int a) {
        int fromEdge = WIDE / 2 - Math.abs(a);
        return Math.min(TALL - 1, TownPlan.WALL_HEIGHT - 1 + fromEdge);
    }

    /** The arch: three wide and four tall, with a crown over the middle of the road. */
    private static int archHeight(int a) {
        return a == 0 ? TownPlan.WALL_HEIGHT + 1 : TownPlan.WALL_HEIGHT;
    }
}
