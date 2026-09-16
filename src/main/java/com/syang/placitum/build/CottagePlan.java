package com.syang.placitum.build;

import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.BuildRecipe;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * A cottage, generated rather than loaded.
 *
 * <p>docs/construction.md specifies .nbt templates, and that is still the better answer for
 * variety - a dozen hand-built houses will always look better than one rule. It is not the
 * answer available here: authoring an .nbt means writing blocks from code and saving the result,
 * so the template would be this file with a file format in front of it, and nobody could look at
 * the shape and adjust it.
 *
 * <p>So the loop gets closed with a generated cottage and the template loader stays a hook. A
 * settlement that grows plain houses is the milestone's criterion; a settlement that grows
 * beautiful ones is not.
 *
 * <p>Pure, like every expansion: the ground was read once at QUEUE time and frozen.
 */
public final class CottagePlan {

    /**
     * Blocks to a side, walls included, so the room inside is three by three.
     *
     * <p>Seven was the first guess and it looks enormous next to anything vanilla builds - a
     * five-by-five room for two beds. Five costs half the timber too, which matters while this
     * is the only thing a settlement can spend timber on.
     */
    public static final int SIDE = 5;

    /** Floor, three courses of wall, and a roof. */
    public static final int HEIGHT = 5;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState WALL = Blocks.OAK_PLANKS.defaultBlockState();
    private static final BlockState FLOOR = Blocks.OAK_PLANKS.defaultBlockState();
    private static final BlockState ROOF = Blocks.OAK_PLANKS.defaultBlockState();
    private static final BlockState WINDOW = Blocks.GLASS_PANE.defaultBlockState();
    private static final BlockState FOUNDATION = Blocks.COBBLESTONE.defaultBlockState();

    private CottagePlan() {}

    /**
     * Columns the ground is sampled at, in the order the profile stores them.
     *
     * <p>The house itself, and then one column outside the door. That last one is not decoration:
     * the floor is laid at the highest ground under the house, so on any slope the threshold ends
     * up above the ground outside it and the door opens onto a wall of dirt. Somebody has to
     * stand there to get in.
     */
    public static List<BlockPos> footprint(BlockPos northWest, Rotation rotation) {
        List<BlockPos> out = new ArrayList<>(SIDE * SIDE + 1);
        for (int dz = 0; dz < SIDE; dz++) {
            for (int dx = 0; dx < SIDE; dx++) {
                out.add(northWest.offset(dx, 0, dz));
            }
        }
        out.add(doorstep(northWest, doorFacing(rotation)));
        return out;
    }

    /** The column immediately outside the door. */
    public static BlockPos doorstep(BlockPos northWest, Direction door) {
        int middle = SIDE / 2;
        return switch (door) {
            case NORTH -> northWest.offset(middle, 0, -1);
            case SOUTH -> northWest.offset(middle, 0, SIDE);
            case WEST -> northWest.offset(-1, 0, middle);
            default -> northWest.offset(SIDE, 0, middle);
        };
    }

    /** How many beds a finished cottage holds. What the whole loop is ultimately counting. */
    public static int bedCount() {
        return 2;
    }

    /**
     * Expands a cottage.
     *
     * <p>One floor level for the whole house, taken as the highest ground under it: digging a
     * house into a slope reads as griefing, and standing it on stilts reads as a bug. Everything
     * below gets a foundation, which is what the cobblestone is for.
     */
    public static List<BuildOp> expand(BuildRecipe recipe) {
        List<Integer> profile = recipe.groundProfile();
        List<BlockPos> columns = footprint(recipe.anchor(), recipe.rotation());
        if (profile.size() != columns.size()) {
            return List.of();
        }
        int floor = highest(profile);
        if (floor == WallGeometry.SKIP) {
            return List.of();
        }
        Direction door = doorFacing(recipe.rotation());

        // Fixtures first, so the shell knows which positions are already spoken for. Letting
        // both passes write the same block and relying on the later one to win works only
        // because the sort happens to be stable - eight of 253 ops were doing exactly that,
        // which is a shape decided by insertion order rather than by anything readable.
        List<BuildOp> fixtures = new ArrayList<>();
        fixtures.addAll(doorway(recipe.anchor(), floor, door));
        fixtures.addAll(furnish(recipe.anchor(), floor));
        Set<BlockPos> claimed = new HashSet<>();
        for (BuildOp fixture : fixtures) {
            claimed.add(fixture.pos());
        }

        List<BuildOp> ops = new ArrayList<>(fixtures);

        for (int i = 0; i < SIDE * SIDE; i++) {
            BlockPos column = columns.get(i);
            int dx = i % SIDE;
            int dz = i / SIDE;
            boolean edge = dx == 0 || dz == 0 || dx == SIDE - 1 || dz == SIDE - 1;

            // Foundation up to the floor, so the house sits on the ground rather than in it.
            for (int y = profile.get(i) + 1; y < floor; y++) {
                ops.add(new BuildOp(new BlockPos(column.getX(), y, column.getZ()), FOUNDATION));
            }
            add(ops, claimed, new BlockPos(column.getX(), floor, column.getZ()), FLOOR);

            for (int course = 1; course <= HEIGHT - 2; course++) {
                int y = floor + course;
                BlockState state = edge ? wallBlock(dx, dz, course, door) : AIR;
                add(ops, claimed, new BlockPos(column.getX(), y, column.getZ()), state);
            }
            add(ops, claimed, new BlockPos(column.getX(), floor + HEIGHT - 1, column.getZ()),
                    ROOF);
        }

        ops.addAll(step(columns.getLast(), profile.getLast(), floor));

        // Y-ascending, so a builder stands on what it has laid; then a fixed order within a
        // course so the list is the same every time it is expanded.
        ops.sort(Comparator.comparingInt((BuildOp op) -> op.pos().getY())
                .thenComparingInt(op -> op.pos().getX())
                .thenComparingInt(op -> op.pos().getZ()));
        return List.copyOf(ops);
    }

    /** Adds a shell block unless a fixture already owns that position. */
    private static void add(List<BuildOp> ops, Set<BlockPos> claimed, BlockPos pos,
            BlockState state) {
        if (!claimed.contains(pos)) {
            ops.add(new BuildOp(pos, state));
        }
    }

    /**
     * The step outside the door, and the air over it.
     *
     * <p>Built up where the ground outside is lower than the floor and cut down where it is
     * higher. Both happen: the floor sits at the highest ground under the house, so downhill of
     * it the threshold is a ledge and uphill of it the doorway is buried.
     */
    private static List<BuildOp> step(BlockPos outside, int ground, int floor) {
        if (ground == WallGeometry.SKIP) {
            return List.of();
        }
        List<BuildOp> ops = new ArrayList<>();
        for (int y = ground + 1; y < floor; y++) {
            ops.add(new BuildOp(new BlockPos(outside.getX(), y, outside.getZ()), FOUNDATION));
        }
        ops.add(new BuildOp(new BlockPos(outside.getX(), floor, outside.getZ()), FLOOR));
        for (int y = floor + 1; y <= Math.max(floor + 2, ground + 2); y++) {
            ops.add(new BuildOp(new BlockPos(outside.getX(), y, outside.getZ()), AIR));
        }
        return ops;
    }

    /**
     * What goes in a given piece of wall.
     *
     * <p>Windows at head height, and never on a corner - a corner window leaves the roof resting
     * on glass, which looks like a mistake because structurally it is one.
     */
    private static BlockState wallBlock(int dx, int dz, int course, Direction door) {
        boolean corner = (dx == 0 || dx == SIDE - 1) && (dz == 0 || dz == SIDE - 1);
        boolean midWall = dx == SIDE / 2 || dz == SIDE / 2;
        if (course == 2 && !corner && midWall && !isDoorColumn(dx, dz, door)) {
            return WINDOW;
        }
        return WALL;
    }

    /** The doorway is cut by {@link #doorway}; the wall pass must leave it alone. */
    private static boolean isDoorColumn(int dx, int dz, Direction door) {
        int middle = SIDE / 2;
        return switch (door) {
            case NORTH -> dz == 0 && dx == middle;
            case SOUTH -> dz == SIDE - 1 && dx == middle;
            case WEST -> dx == 0 && dz == middle;
            default -> dx == SIDE - 1 && dz == middle;
        };
    }

    /**
     * The door, both halves, facing out.
     *
     * <p>A wooden door on purpose. Villagers open them and zombies break them on hard difficulty,
     * which is a fair trade; an iron door cannot be opened by the people who live there, and
     * pathfinding reads it as a wall.
     */
    private static List<BuildOp> doorway(BlockPos northWest, int floor, Direction door) {
        int middle = SIDE / 2;
        BlockPos at = switch (door) {
            case NORTH -> northWest.offset(middle, 0, 0);
            case SOUTH -> northWest.offset(middle, 0, SIDE - 1);
            case WEST -> northWest.offset(0, 0, middle);
            default -> northWest.offset(SIDE - 1, 0, middle);
        };
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, door.getOpposite())
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        return List.of(
                new BuildOp(new BlockPos(at.getX(), floor + 1, at.getZ()), lower),
                new BuildOp(new BlockPos(at.getX(), floor + 2, at.getZ()),
                        lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)),
                new BuildOp(new BlockPos(at.getX(), floor + 3, at.getZ()), WALL));
    }

    /**
     * Two beds and a light.
     *
     * <p>The beds are the entire point: carrying capacity counts beds, so a house without them
     * is a decoration that cost a settlement its timber. The torch is not decoration either - an
     * unlit room spawns the things the walls were built to keep out.
     */
    private static List<BuildOp> furnish(BlockPos northWest, int floor) {
        List<BuildOp> ops = new ArrayList<>();
        int y = floor + 1;
        for (int bed = 0; bed < bedCount(); bed++) {
            int dx = 1 + bed * 2;
            BlockPos foot = northWest.offset(dx, 0, SIDE - 2);
            BlockPos head = northWest.offset(dx, 0, SIDE - 3);
            // Beds are a ColorCollection in 26.2 - Blocks.BED.pick(colour) - rather
            // than sixteen separate constants.
            BlockState base = Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED)
                    .defaultBlockState()
                    .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                            Direction.SOUTH);
            ops.add(new BuildOp(new BlockPos(foot.getX(), y, foot.getZ()),
                    base.setValue(BedBlock.PART, BedPart.FOOT)));
            ops.add(new BuildOp(new BlockPos(head.getX(), y, head.getZ()),
                    base.setValue(BedBlock.PART, BedPart.HEAD)));
        }
        // Standing on the floor in a corner. A torch in mid-air has nothing to attach to and
        // pops off as an item the moment the chunk ticks, which leaves the room dark and the
        // spawning it was meant to prevent happening anyway.
        ops.add(new BuildOp(new BlockPos(northWest.getX() + 1, y, northWest.getZ() + 1),
                Blocks.TORCH.defaultBlockState()));
        return ops;
    }

    /** The highest ground under the house. SKIP if none of it could be read. */
    private static int highest(List<Integer> profile) {
        int best = WallGeometry.SKIP;
        for (int height : profile) {
            if (height != WallGeometry.SKIP && (best == WallGeometry.SKIP || height > best)) {
                best = height;
            }
        }
        return best;
    }

    /** Rotation carries which wall the door is in; the planner picks it from the nearest road. */
    public static Direction doorFacing(Rotation rotation) {
        return switch (rotation) {
            case NONE -> Direction.NORTH;
            case CLOCKWISE_90 -> Direction.EAST;
            case CLOCKWISE_180 -> Direction.SOUTH;
            case COUNTERCLOCKWISE_90 -> Direction.WEST;
        };
    }
}
