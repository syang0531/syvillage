package com.syang.placitum.build;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.Craft;
import com.syang.placitum.data.Settlement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A watchtower on each corner of the wall.
 *
 * <p>Solid, not hollow. A tower with a room in it needs a doorway, a floor, a hole in that floor
 * for the stairs and a ceiling over the hole, and all of it is invisible from outside. A bastion
 * is the same silhouette for none of that: eight by eight of masonry standing eight high, with
 * the walkway cut into it as a ramp on each of the two arms that meet there.
 *
 * <p>The ramps are why it is worth having at all. The wall arrives at the corner four blocks up
 * and leaves it four blocks up, and between those the tower carries you to eight - so the corner
 * is somewhere to stand and see from, which is what a watchtower is, rather than a bulge.
 *
 * <pre>
 *   v=7  #######        the ring, crenellated at the top
 *   v=4  ##  DD ##      D: the ramp from the wall, arriving on the deck
 *   v=0  ##  dd ##      d: the same ramp, at the wall's own level
 *         u=3,4
 * </pre>
 */
public final class TowerPlan {

    public static final Identifier TOWER =
            Identifier.fromNamespaceAndPath(Placitum.MODID, "wall/tower");

    /** A side of the tower, and its height. Twice the wall, like the gatehouse. */
    public static final int SIDE = TownPlan.TOWER;

    /**
     * The whole footprint: the tower, and the ramp that climbs the wall to reach it.
     *
     * <p>The climb used to be cut into the tower, which cost the deck two of its rows on each
     * of two sides - a quarter of the one place in a town worth standing to look from. It is on
     * the wall now, and the wall arrives at the tower's height instead of four blocks under it.
     */
    public static final int FRAME = TownPlan.RAMP + SIDE;

    private TowerPlan() {}

    /**
     * The tower's own coordinates for a footprint index.
     *
     * <p>Every corner is the same tower with its axes flipped, and these two are the flip. They
     * exist because expand did it inline and the other three readers of the footprint did not do
     * it at all: {@code coreFloor} took a square of the footprint that is the core for one corner
     * out of four, and for the north-west tower forty-eight of the sixty-four columns it levelled
     * against were ramp. On flat ground every one of those is the same number, which is why it
     * survived a flat-ground test.
     */
    private static int frameU(int index, boolean[] mirror) {
        return mirror[0] ? FRAME - 1 - index % FRAME : index % FRAME;
    }

    private static int frameV(int index, boolean[] mirror) {
        return mirror[1] ? FRAME - 1 - index / FRAME : index / FRAME;
    }

    /** And back: where a frame cell lands in the footprint. */
    private static int indexOf(int u, int v, boolean[] mirror) {
        return (mirror[1] ? FRAME - 1 - v : v) * FRAME + (mirror[0] ? FRAME - 1 - u : u);
    }

    /**
     * A column in the middle of the deck, as an index into the footprint.
     *
     * <p>What "is this tower already up" is asked of. It used to be the middle of the
     * <em>footprint</em>, which for two corners out of four is a column the tower never puts a
     * block in - so those two could only ever answer no.
     */
    public static int deckColumn(int[] corner) {
        int middle = TownPlan.RAMP + SIDE / 2;
        return indexOf(middle, middle, mirrorOf(quadrantOf(corner)));
    }

    /** The four corners, as the pair of signs that puts a tower in that quadrant. */
    public static List<int[]> corners() {
        return List.of(new int[] {-1, -1}, new int[] {1, -1}, new int[] {1, 1}, new int[] {-1, 1});
    }

    /**
     * The north-west column of a corner tower.
     *
     * <p>Centred on the four-by-four the wall's two arms share, so it stands two blocks proud of
     * the wall on the outside and two into the town on the inside - which is what makes a corner
     * read as a corner rather than as two walls crossing.
     */
    public static BlockPos anchorOf(Settlement settlement, int[] corner) {
        int near = TownPlan.wallInner(settlement) - (SIDE - TownPlan.WALL) / 2 - TownPlan.RAMP;
        BlockPos bell = settlement.center();
        return new BlockPos(
                bell.getX() + (corner[0] < 0 ? -(near + FRAME - 1) : near), bell.getY(),
                bell.getZ() + (corner[1] < 0 ? -(near + FRAME - 1) : near));
    }

    /** Every column of the tower, west to east and then north to south. */
    public static List<BlockPos> footprint(BlockPos anchor) {
        List<BlockPos> out = new ArrayList<>(FRAME * FRAME);
        for (int v = 0; v < FRAME; v++) {
            for (int u = 0; u < FRAME; u++) {
                out.add(anchor.offset(u, 0, v));
            }
        }
        return out;
    }

    public static Optional<BuildRecipe> plan(ServerLevel level, Settlement settlement,
            int[] corner, Reach reach) {
        if (!settlement.walled()) {
            return Optional.empty();
        }
        BlockPos anchor = anchorOf(settlement, corner);
        BlockState stone = settlement.craft().wall();
        List<BlockPos> columns = footprint(anchor);
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
            boolean mine = touches(i, corner);
            if (!level.hasChunkAt(column)) {
                if (mine) {
                    return Optional.empty();
                }
                profile.add(Ground.SKIP);
                continue;
            }
            // Down through our own masonry, for the reason the gatehouse has it: a tower that
            // is already up reads as ground eight blocks above the ground.
            int ground = GridSurvey.footingOrSkip(level, column.getX(), column.getZ(), stone);
            profile.add(ground);
            if (!mine) {
                continue;
            }
            if (ground == Ground.SKIP || !reach.has(column)) {
                return Optional.empty();   // a tower with its feet in a lake is not a tower
            }
            ours.add(column);
            oursGround.add(ground);
        }
        // And this is now the only thing that stops a finished tower being built again. It used
        // to be the reach test above: a tower you cannot walk up is a tower whose columns are
        // unreachable, so the plan gave up before it ever got here. Relaxing that test takes the
        // guard away - the ramps make a standing tower's own columns walkable - which is why
        // standing() had to be right first. For two corners of four it never once said yes.
        if (standing(level, settlement, columns, profile, corner)) {
            return Optional.empty();
        }
        Placitum.LOGGER.debug("Planned a tower for '{}' at {}", settlement.name(), anchor);

        // The quadrant is frozen as the rotation, because which way the ramps face is a fact
        // about which corner this is, and expansion may not go and ask the settlement.
        return Optional.of(new BuildRecipe(TOWER, anchor, quadrantOf(corner),
                settlement.craft().paletteId(), List.copyOf(profile),
                new BlockPos(FRAME, SIDE, FRAME),
                Spans.encode(Clearance.spans(level, ours, oursGround))));
    }

    /**
     * The level the tower stands at, taken from the tower and not from its ramps.
     *
     * <p>The ramps run four blocks further along the wall in each direction, and letting their
     * ground vote would lift the whole tower onto whatever was highest out there.
     */
    private static int coreFloor(List<Integer> profile, Rotation quadrant) {
        boolean[] mirror = mirrorOf(quadrant);
        List<Integer> core = new ArrayList<>();
        for (int i = 0; i < profile.size(); i++) {
            if (frameU(i, mirror) >= TownPlan.RAMP && frameV(i, mirror) >= TownPlan.RAMP) {
                core.add(profile.get(i));
            }
        }
        return Ground.highest(core);
    }

    /**
     * Whether this tower is already up.
     *
     * <p>Asked of the deck, which is at a known height above a known floor. Asking the ground
     * would get the top of the tower back once there is one.
     */
    private static boolean standing(ServerLevel level, Settlement settlement,
            List<BlockPos> columns, List<Integer> profile, int[] corner) {
        int floor = coreFloor(profile, quadrantOf(corner));
        BlockPos deck = columns.get(deckColumn(corner));
        return floor != Ground.SKIP
                && level.getBlockState(new BlockPos(deck.getX(), floor + SIDE - 1,
                        deck.getZ())).is(settlement.craft().wall().getBlock());
    }

    /** Standing, or what is stopping it. For the log when a settlement has gone quiet. */
    public static String status(ServerLevel level, Settlement settlement, int[] corner,
            Reach reach) {
        List<BlockPos> columns = footprint(anchorOf(settlement, corner));
        return standing(level, settlement, columns,
                GatePlan.grounds(level, columns, settlement.craft().wall()), corner)
                ? "standing"
                : GatePlan.trouble(level, columns, reach, i -> touches(i, corner));
    }

    /**
     * Whether the build writes anything at all in this column of the frame.
     *
     * <p>Sixteen of the hundred and forty-four are the four-by-four beyond both ramps, which
     * {@link #topAt} answers zero for and expand skips outright - "open ground beside a ramp;
     * nothing of ours belongs here". They are in the footprint only because the frame is square.
     */
    public static boolean touches(int index, int[] corner) {
        boolean[] mirror = mirrorOf(quadrantOf(corner));
        return topAt(frameU(index, mirror), frameV(index, mirror)) > 0;
    }

    /** Which corner this is, carried in the recipe as a rotation. */
    public static Rotation quadrantOf(int[] corner) {
        if (corner[0] < 0) {
            return corner[1] < 0 ? Rotation.NONE : Rotation.COUNTERCLOCKWISE_90;
        }
        return corner[1] < 0 ? Rotation.CLOCKWISE_90 : Rotation.CLOCKWISE_180;
    }

    /** The tower, with the walkway's two ramps cut into it. */
    public static List<BuildOp> expand(BuildRecipe recipe) {
        List<Integer> profile = recipe.groundProfile();
        List<BlockPos> columns = footprint(recipe.anchor());
        if (profile.size() != columns.size()) {
            return List.of();
        }
        int floor = coreFloor(profile, recipe.rotation());
        if (floor == Ground.SKIP) {
            return List.of();
        }
        BlockState stone = Craft.fromPalette(recipe.palette()).wall();
        boolean[] mirror = mirrorOf(recipe.rotation());
        List<BuildOp> ops = new ArrayList<>();

        for (int i = 0; i < columns.size(); i++) {
            BlockPos column = columns.get(i);
            int u = frameU(i, mirror);
            int v = frameV(i, mirror);

            int top = topAt(u, v);
            if (top <= 0) {
                continue;   // open ground beside a ramp; nothing of ours belongs here
            }
            // Up to and including the floor: one short of it left a hole under every column
            // that was not the highest, and the tower floated.
            for (int y = profile.get(i) + 1; y <= floor; y++) {
                ops.add(new BuildOp(new BlockPos(column.getX(), y, column.getZ()), stone));
            }
            for (int h = 1; h <= top; h++) {
                ops.add(new BuildOp(new BlockPos(column.getX(), floor + h, column.getZ()), stone));
            }
            if (top == SIDE - 1 && merlon(u, v) && (u + v) % 2 == 0) {
                ops.add(new BuildOp(new BlockPos(column.getX(), floor + SIDE, column.getZ()),
                        stone));
                // A lantern on the two corner merlons - the one that is the corner of the town
                // and the one facing in. The tower stands on the ground of one lamp post, which
                // is never built, and this is the light in its place.
                boolean corner = (u == TownPlan.RAMP && v == TownPlan.RAMP)
                        || (u == FRAME - 1 && v == FRAME - 1);
                if (corner) {
                    ops.add(new BuildOp(new BlockPos(column.getX(), floor + SIDE + 1,
                            column.getZ()), Blocks.LANTERN.defaultBlockState()));
                }
            }
        }
        ops.addAll(Clearance.ops(Spans.decode(recipe.gates()), GatePlan.written(ops)));
        ops.sort(Comparator.comparingInt((BuildOp op) -> op.pos().getY())
                .thenComparingInt(op -> op.pos().getX())
                .thenComparingInt(op -> op.pos().getZ()));
        return List.copyOf(ops);
    }

    /**
     * Whether the tower's coordinates run backwards along each axis for this corner.
     *
     * <p>The ramps always come in from the two sides the wall arrives on, and which sides those
     * are is the corner. Flipping the coordinates is cheaper than writing the ramp out four
     * times, and cheaper to be sure of.
     */
    private static boolean[] mirrorOf(Rotation quadrant) {
        return switch (quadrant) {
            case NONE -> new boolean[] {true, true};                     // north-west
            case CLOCKWISE_90 -> new boolean[] {false, true};            // north-east
            case CLOCKWISE_180 -> new boolean[] {false, false};          // south-east
            case COUNTERCLOCKWISE_90 -> new boolean[] {true, false};     // south-west
        };
    }

    /**
     * How high the masonry goes in one column of the frame.
     *
     * <p>Inside the tower it is solid all the way to the deck - the whole eight by eight of it,
     * which is the point of moving the climb out. Outside, this is the rampart on its way up:
     * only the four columns the wall occupies are ours, and the rest of the frame is open ground
     * beside it.
     */
    private static int topAt(int u, int v) {
        boolean inCore = u >= TownPlan.RAMP && v >= TownPlan.RAMP;
        if (inCore) {
            return SIDE - 1;
        }
        if (u >= TownPlan.RAMP) {
            return inWall(u) ? rampTop(v, !inWalkway(u)) : 0;
        }
        if (v >= TownPlan.RAMP) {
            return inWall(v) ? rampTop(u, !inWalkway(v)) : 0;
        }
        return 0;   // the square beyond both ramps belongs to neither of them
    }

    /** Where the rampart's four columns land in the frame. */
    private static boolean inWall(int w) {
        int outerFace = TownPlan.RAMP + (SIDE + TownPlan.WALL) / 2 - 1;
        return w <= outerFace && w > outerFace - TownPlan.WALL;
    }

    /** The two of those four you walk on. */
    private static boolean inWalkway(int w) {
        int outerFace = TownPlan.RAMP + (SIDE + TownPlan.WALL) / 2 - 1;
        return w == outerFace - 1 || w == outerFace - 2;
    }

    /**
     * How high the wall stands where it is climbing towards the tower.
     *
     * <p>Rampart height at the far end of the ramp and deck height where it meets the tower,
     * with the parapet a block above the walkway the whole way, so the climb has a handrail
     * rather than an edge.
     */
    private static int rampTop(int along, boolean parapet) {
        return TownPlan.WALL_HEIGHT - 1 + along + (parapet ? 1 : 0);
    }

    /** The parapet runs round the edge of the deck, but never across the mouth of a ramp. */
    private static boolean merlon(int u, int v) {
        boolean edge = u == TownPlan.RAMP || u == FRAME - 1
                || v == TownPlan.RAMP || v == FRAME - 1;
        boolean mouth = (v == TownPlan.RAMP && inWalkway(u))
                || (u == TownPlan.RAMP && inWalkway(v));
        return edge && !mouth;
    }
}
