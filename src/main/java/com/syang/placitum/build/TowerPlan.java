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

    private TowerPlan() {}

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
        int near = TownPlan.wallInner(settlement) - (SIDE - TownPlan.WALL) / 2;
        BlockPos bell = settlement.center();
        return new BlockPos(
                bell.getX() + (corner[0] < 0 ? -(near + SIDE - 1) : near), bell.getY(),
                bell.getZ() + (corner[1] < 0 ? -(near + SIDE - 1) : near));
    }

    /** Every column of the tower, west to east and then north to south. */
    public static List<BlockPos> footprint(BlockPos anchor) {
        List<BlockPos> out = new ArrayList<>(SIDE * SIDE);
        for (int v = 0; v < SIDE; v++) {
            for (int u = 0; u < SIDE; u++) {
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
        List<BlockPos> columns = footprint(anchor);
        List<Integer> profile = new ArrayList<>(columns.size());

        for (BlockPos column : columns) {
            if (!level.hasChunkAt(column) || !reach.has(column)) {
                return Optional.empty();
            }
            int ground = GridSurvey.groundOrSkip(level, column.getX(), column.getZ());
            if (ground == Ground.SKIP) {
                return Optional.empty();   // a tower with its feet in a lake is not a tower
            }
            profile.add(ground);
        }
        int floor = Ground.highest(profile);
        BlockPos middle = columns.get(columns.size() / 2);
        if (level.getBlockState(new BlockPos(middle.getX(), floor + SIDE - 1, middle.getZ()))
                .is(settlement.craft().wall().getBlock())) {
            return Optional.empty();   // standing already
        }
        Placitum.LOGGER.debug("Planned a tower for '{}' at {}", settlement.name(), anchor);

        // The quadrant is frozen as the rotation, because which way the ramps face is a fact
        // about which corner this is, and expansion may not go and ask the settlement.
        return Optional.of(new BuildRecipe(TOWER, anchor, quadrantOf(corner),
                settlement.craft().paletteId(), List.copyOf(profile),
                new BlockPos(SIDE, SIDE, SIDE),
                Spans.encode(Clearance.spans(level, columns, profile))));
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
        int floor = Ground.highest(profile);
        if (floor == Ground.SKIP) {
            return List.of();
        }
        BlockState stone = Craft.fromPalette(recipe.palette()).wall();
        boolean[] mirror = mirrorOf(recipe.rotation());
        List<BuildOp> ops = new ArrayList<>();

        for (int i = 0; i < columns.size(); i++) {
            BlockPos column = columns.get(i);
            int u = mirror[0] ? SIDE - 1 - i % SIDE : i % SIDE;
            int v = mirror[1] ? SIDE - 1 - i / SIDE : i / SIDE;

            // Up to and including the floor: one short of it left a hole under every column
            // that was not the highest, and the tower floated.
            for (int y = profile.get(i) + 1; y <= floor; y++) {
                ops.add(new BuildOp(new BlockPos(column.getX(), y, column.getZ()), stone));
            }
            int top = topAt(u, v);
            for (int h = 1; h <= top; h++) {
                ops.add(new BuildOp(new BlockPos(column.getX(), floor + h, column.getZ()), stone));
            }
            if (top == SIDE - 1 && merlon(u, v) && (u + v) % 2 == 0) {
                ops.add(new BuildOp(new BlockPos(column.getX(), floor + SIDE, column.getZ()),
                        stone));
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
     * How high the masonry goes in one column.
     *
     * <p>Everything is solid to the deck except the two ramps, which come in at the wall's own
     * height and gain a block per block until they reach it. The walkway's columns are depths 1
     * and 2 of the wall, which land on 3 and 4 of an eight-wide tower centred on the corner.
     */
    private static int topAt(int u, int v) {
        int deck = SIDE - 1;
        int reached = deck;
        boolean onRamp = false;
        if ((u == 3 || u == 4) && v <= 4) {
            reached = Math.min(reached, TownPlan.WALL_HEIGHT - 1 + v);
            onRamp = true;
        }
        if ((v == 3 || v == 4) && u <= 4) {
            // The lower of the two where they cross, not the higher. Taking the higher put a
            // two-block step in the middle of one ramp: at the crossing the two disagree by one,
            // and the ramp that was climbing has to be the one that is believed.
            reached = Math.min(reached, TownPlan.WALL_HEIGHT - 1 + u);
            onRamp = true;
        }
        return onRamp ? reached : deck;
    }

    /** The parapet runs round the edge, but never across the mouth of a ramp. */
    private static boolean merlon(int u, int v) {
        boolean edge = u == 0 || u == SIDE - 1 || v == 0 || v == SIDE - 1;
        boolean mouth = (v == 0 && (u == 3 || u == 4)) || (u == 0 && (v == 3 || v == 4));
        return edge && !mouth;
    }
}
