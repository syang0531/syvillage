package com.syang.placitum.build;

import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.Craft;
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
    public static final int SIDE = TownPlan.BUILDING;

    /** Floor, three courses of wall, and a roof. */
    public static final int HEIGHT = 5;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /**
     * A block, not a pane.
     *
     * <p>A pane works out its own shape from what it is next to, and blocks are written with
     * UPDATE_KNOWN_SHAPE so that beds and doors cannot destroy themselves mid-build - which also
     * means a pane never gets asked to connect. It rendered as a bar floating in a hole. A solid
     * pane of glass needs no neighbours to look like a window.
     */
    private static final BlockState WINDOW = Blocks.GLASS.defaultBlockState();

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
        if (floor == Ground.SKIP) {
            return List.of();
        }
        Direction door = doorFacing(recipe.rotation());
        // Read once off the recipe, not off the settlement: a cottage half built when the mason
        // arrives finishes in the timber it started in, rather than changing material halfway up
        // its own wall.
        Craft craft = Craft.fromPalette(recipe.palette());

        // Fixtures first, so the shell knows which positions are already spoken for. Letting
        // both passes write the same block and relying on the later one to win works only
        // because the sort happens to be stable - eight of 253 ops were doing exactly that,
        // which is a shape decided by insertion order rather than by anything readable.
        List<BuildOp> fixtures = new ArrayList<>();
        fixtures.addAll(doorway(recipe.anchor(), floor, door, craft));
        fixtures.addAll(furnish(recipe.anchor(), floor, door));
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
                ops.add(new BuildOp(new BlockPos(column.getX(), y, column.getZ()),
                        craft.foundation()));
            }
            add(ops, claimed, new BlockPos(column.getX(), floor, column.getZ()), craft.floor());

            for (int course = 1; course <= HEIGHT - 2; course++) {
                int y = floor + course;
                BlockState state = edge ? wallBlock(dx, dz, course, door, craft) : AIR;
                add(ops, claimed, new BlockPos(column.getX(), y, column.getZ()), state);
            }
            add(ops, claimed, new BlockPos(column.getX(), floor + HEIGHT - 1, column.getZ()),
                    craft.roof());
        }

        ops.addAll(step(columns.getLast(), profile.getLast(), floor, craft));

        // Fell whatever is growing on the lot, last, so every position the house itself writes
        // is already spoken for. The ground reading walks down past a trunk on purpose - that is
        // what stops one tree making a site unbuildable - and the price of that is a house built
        // straight through the tree unless it comes out here.
        Set<BlockPos> written = new HashSet<>();
        for (BuildOp op : ops) {
            written.add(op.pos());
        }
        ops.addAll(Clearance.ops(Spans.decode(recipe.gates()), written));

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
    private static List<BuildOp> step(BlockPos outside, int ground, int floor,
            Craft craft) {
        if (ground == Ground.SKIP) {
            return List.of();
        }
        List<BuildOp> ops = new ArrayList<>();
        for (int y = ground + 1; y < floor; y++) {
            ops.add(new BuildOp(new BlockPos(outside.getX(), y, outside.getZ()),
                    craft.foundation()));
        }
        ops.add(new BuildOp(new BlockPos(outside.getX(), floor, outside.getZ()), craft.floor()));
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
    private static BlockState wallBlock(int dx, int dz, int course, Direction door,
            Craft craft) {
        boolean corner = (dx == 0 || dx == SIDE - 1) && (dz == 0 || dz == SIDE - 1);
        boolean midWall = dx == SIDE / 2 || dz == SIDE / 2;
        if (course == 2 && !corner && midWall && !isDoorColumn(dx, dz, door)) {
            return WINDOW;
        }
        return craft.wall();
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

    /** Which column of the wall the door sits in. Y is the caller's business. */
    public static BlockPos doorPosition(BlockPos northWest, Direction door) {
        int middle = SIDE / 2;
        return switch (door) {
            case NORTH -> northWest.offset(middle, 0, 0);
            case SOUTH -> northWest.offset(middle, 0, SIDE - 1);
            case WEST -> northWest.offset(0, 0, middle);
            default -> northWest.offset(SIDE - 1, 0, middle);
        };
    }

    /**
     * The door, both halves, facing out.
     *
     * <p>A wooden door on purpose. Villagers open them and zombies break them on hard difficulty,
     * which is a fair trade; an iron door cannot be opened by the people who live there, and
     * pathfinding reads it as a wall.
     */
    private static List<BuildOp> doorway(BlockPos northWest, int floor, Direction door,
            Craft craft) {
        BlockPos at = doorPosition(northWest, door);
        // Shut, and both halves hinged the same way. The defaults happen to be right, but a
        // door built ajar is a hole in the wall all night and neither half may disagree with the
        // other about which side it swings from.
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, door.getOpposite())
                .setValue(DoorBlock.OPEN, false)
                .setValue(DoorBlock.POWERED, false)
                .setValue(DoorBlock.HINGE, net.minecraft.world.level.block.state.properties
                        .DoorHingeSide.LEFT)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        return List.of(
                new BuildOp(new BlockPos(at.getX(), floor + 1, at.getZ()), lower),
                new BuildOp(new BlockPos(at.getX(), floor + 2, at.getZ()),
                        lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)),
                new BuildOp(new BlockPos(at.getX(), floor + 3, at.getZ()), craft.wall()));
    }

    /**
     * Beds and a light, laid out to a plan rather than to whatever was convenient.
     *
     * <pre>
     *   w w W w w
     *   w n B B w
     *   D n n f W
     *   w n B B w
     *   w w W w w
     * </pre>
     *
     * <p>Written with the door on the west and then turned to wherever the door actually is. The
     * middle row is left clear on purpose: it is the way in, and beds across it would mean
     * walking over somebody to get through your own front door.
     *
     * <p>FACING on a bed points from the foot towards the head. Setting it south and then putting
     * the head to the north is how the last one ended up as two mismatched halves with a villager
     * lying across them.
     */
    private static List<BuildOp> furnish(BlockPos northWest, int floor, Direction door) {
        List<BuildOp> ops = new ArrayList<>();
        int y = floor + 1;
        int turns = turnsFromWest(door);

        for (int dz : new int[] {1, 3}) {
            BlockPos foot = northWest.offset(0, 0, 0).offset(rotX(2, dz, turns), 0,
                    rotZ(2, dz, turns));
            BlockPos head = northWest.offset(rotX(3, dz, turns), 0, rotZ(3, dz, turns));
            Direction facing = rotate(Direction.EAST, turns);
            BlockState bed = Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED)
                    .defaultBlockState()
                    .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                            facing);
            ops.add(new BuildOp(new BlockPos(foot.getX(), y, foot.getZ()),
                    bed.setValue(BedBlock.PART, BedPart.FOOT)));
            ops.add(new BuildOp(new BlockPos(head.getX(), y, head.getZ()),
                    bed.setValue(BedBlock.PART, BedPart.HEAD)));
        }

        // Standing on the floor, never in mid-air: a torch with nothing under it pops off as an
        // item and leaves the room dark enough to spawn what the walls are for.
        BlockPos torch = northWest.offset(rotX(3, 2, turns), 0, rotZ(3, 2, turns));
        ops.add(new BuildOp(new BlockPos(torch.getX(), y, torch.getZ()),
                Blocks.TORCH.defaultBlockState()));
        return ops;
    }

    /** Quarter-turns clockwise that take a west-facing door to this one. */
    private static int turnsFromWest(Direction door) {
        return switch (door) {
            case WEST -> 0;
            case NORTH -> 1;
            case EAST -> 2;
            default -> 3;
        };
    }

    private static int rotX(int dx, int dz, int turns) {
        return switch (turns) {
            case 1 -> SIDE - 1 - dz;
            case 2 -> SIDE - 1 - dx;
            case 3 -> dz;
            default -> dx;
        };
    }

    private static int rotZ(int dx, int dz, int turns) {
        return switch (turns) {
            case 1 -> dx;
            case 2 -> SIDE - 1 - dz;
            case 3 -> SIDE - 1 - dx;
            default -> dz;
        };
    }

    private static Direction rotate(Direction facing, int turns) {
        Direction out = facing;
        for (int i = 0; i < turns; i++) {
            out = out.getClockWise();
        }
        return out;
    }

    /** The highest ground under the house. SKIP if none of it could be read. */
    private static int highest(List<Integer> profile) {
        int best = Ground.SKIP;
        for (int height : profile) {
            if (height != Ground.SKIP && (best == Ground.SKIP || height > best)) {
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
