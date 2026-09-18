package com.syang.placitum.build;

import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.Craft;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * A structure built by hand in a creative world, saved with a structure block, and shipped
 * in the jar.
 *
 * <p>This is the answer to a question the gatehouse and the tower kept asking: how does
 * somebody who is not a programmer change the shape of a thing the settlement builds? They
 * build it. A structure block writes it out as NBT, the file goes in
 * {@code data/placitum/structure/}, and this reads it back - without the game running,
 * which is what lets it be tested.
 *
 * <p>Read once and cached, because a template is static content, like a palette. Expansion
 * stays a pure function of its recipe: the same recipe and the same template give the same
 * blocks, and nothing here looks at a world.
 *
 * <p>A template is authored in one orientation - north is outside, or the north-west corner
 * - and turned for the other three by {@link #turn}. Materials are the palette's: the
 * template is stone brick and oak because that is what a creative player builds in, and
 * {@link #remap} makes it sandstone and acacia where the biome says so.
 */
public final class Template {

    /** One block of the template, in template coordinates (x east, y up, z south). */
    public record Piece(int x, int y, int z, BlockState state) {}

    private static final Map<String, Template> LOADED = new ConcurrentHashMap<>();

    private final String name;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<Piece> pieces;

    private Template(String name, int sizeX, int sizeY, int sizeZ, List<Piece> pieces) {
        this.name = name;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.pieces = List.copyOf(pieces);
    }

    /** The template of that name from the jar. Loaded on first use, never again. */
    public static Template of(String name) {
        return LOADED.computeIfAbsent(name, Template::read);
    }

    private static Template read(String name) {
        String path = "/data/placitum/structure/" + name + ".nbt";
        try (InputStream in = Template.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("no template at " + path);
            }
            CompoundTag nbt = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            ListTag size = nbt.getListOrEmpty("size");
            ListTag palette = nbt.getListOrEmpty("palette");
            List<BlockState> states = new ArrayList<>(palette.size());
            for (int i = 0; i < palette.size(); i++) {
                states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK,
                        palette.getCompoundOrEmpty(i)));
            }
            List<Piece> pieces = new ArrayList<>();
            ListTag blocks = nbt.getListOrEmpty("blocks");
            for (int i = 0; i < blocks.size(); i++) {
                CompoundTag block = blocks.getCompoundOrEmpty(i);
                ListTag pos = block.getListOrEmpty("pos");
                BlockState state = states.get(block.getIntOr("state", 0));
                if (state.isAir()) {
                    continue;   // a structure block saves the air too; we do not build it
                }
                pieces.add(new Piece(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0),
                        state));
            }
            return new Template(name, size.getIntOr(0, 0), size.getIntOr(1, 0),
                    size.getIntOr(2, 0), pieces);
        } catch (IOException e) {
            throw new IllegalStateException("could not read template " + path, e);
        }
    }

    public String name() {
        return name;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public List<Piece> pieces() {
        return pieces;
    }

    /**
     * The columns the template puts anything in, as template x and z, in a fixed order.
     *
     * <p>Only those. A saved box is a box; the structure inside it is not, and the ground it
     * does not stand on is nobody's business - that is the difference between a footprint and
     * a bounding box, and it is the whole of principle ten's cousin about reachability.
     */
    public List<int[]> columns() {
        List<int[]> out = new ArrayList<>();
        boolean[][] seen = new boolean[sizeX][sizeZ];
        for (Piece piece : pieces) {
            seen[piece.x()][piece.z()] = true;
        }
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                if (seen[x][z]) {
                    out.add(new int[] {x, z});
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** The highest block in a column, or -1 for none. */
    public int topOf(int x, int z) {
        int top = -1;
        for (Piece piece : pieces) {
            if (piece.x() == x && piece.z() == z) {
                top = Math.max(top, piece.y());
            }
        }
        return top;
    }

    /**
     * Whether a column is the structure's masonry from the ground all the way up.
     *
     * <p>These are the columns a structure is levelled against and asked about. A column with
     * an archway or a stair in it reads wrong twice over: the footing walk down through our
     * masonry stops at the first hole, and a probe above it finds air where the deck should be.
     * The old gatehouse was rebuilt in place nineteen times over exactly that.
     */
    public boolean solidToTop(int x, int z) {
        int top = topOf(x, z);
        if (top < 0) {
            return false;
        }
        boolean[] filled = new boolean[top + 1];
        for (Piece piece : pieces) {
            if (piece.x() == x && piece.z() == z && piece.state().is(Blocks.STONE_BRICKS)) {
                filled[piece.y()] = true;
            }
        }
        for (boolean f : filled) {
            if (!f) {
                return false;
            }
        }
        return true;
    }

    /**
     * The column "is it standing" is asked of: solid from the ground, and as tall as any such.
     *
     * <p>Returned as {@code {x, z, top}}. Chosen from the template rather than written down,
     * so that a redrawn template cannot leave the probe pointing at a column that is no longer
     * there.
     */
    public int[] probe() {
        int[] best = null;
        for (int[] column : columns()) {
            if (!solidToTop(column[0], column[1])) {
                continue;
            }
            int top = topOf(column[0], column[1]);
            if (best == null || top > best[2]) {
                best = new int[] {column[0], column[1], top};
            }
        }
        if (best == null) {
            throw new IllegalStateException(name + " has no column solid from the ground");
        }
        return best;
    }

    /**
     * A template-frame vector turned about the origin.
     *
     * <p>The same convention as {@link BlockState#rotate}: clockwise-90 turns north into east.
     * Used for the offset of a template from the bell, so that a gatehouse authored facing
     * north and placed with the east rotation stands east of the bell facing east.
     */
    public static int[] turn(int dx, int dz, Rotation rotation) {
        return switch (rotation) {
            case NONE -> new int[] {dx, dz};
            case CLOCKWISE_90 -> new int[] {-dz, dx};
            case CLOCKWISE_180 -> new int[] {-dx, -dz};
            case COUNTERCLOCKWISE_90 -> new int[] {dz, -dx};
        };
    }

    /**
     * The template's blocks in the world, turned and re-materialed.
     *
     * @param bell     what the offset is measured from
     * @param origin   where the template's (0, 0) sits relative to the bell, in the frame it was
     *                 authored in
     * @param floor    the world y the template's lowest layer stands on: layer 0 goes at
     *                 {@code floor + 1}
     */
    public List<BuildOp> place(BlockPos bell, int[] origin, Rotation rotation, Craft craft,
            int floor) {
        List<BuildOp> ops = new ArrayList<>(pieces.size());
        for (Piece piece : pieces) {
            int[] v = turn(origin[0] + piece.x(), origin[1] + piece.z(), rotation);
            ops.add(new BuildOp(new BlockPos(bell.getX() + v[0], floor + 1 + piece.y(),
                    bell.getZ() + v[1]), remap(piece.state().rotate(rotation), craft)));
        }
        return ops;
    }

    /** Where one template column lands in the world. */
    public static BlockPos columnAt(BlockPos bell, int[] origin, Rotation rotation, int x, int z) {
        int[] v = turn(origin[0] + x, origin[1] + z, rotation);
        return new BlockPos(bell.getX() + v[0], bell.getY(), bell.getZ() + v[1]);
    }

    /**
     * The creative builder's stone brick and oak, in the biome's materials.
     *
     * <p>By family, so that a stair stays a stair and keeps its facing: a sandstone stair for a
     * stone brick one, an acacia fence for an oak one. Anything not in the family table - the
     * lanterns - is left exactly as built.
     */
    public static BlockState remap(BlockState state, Craft craft) {
        if (state.is(Blocks.STONE_BRICKS)) {
            return craft.wall();
        }
        if (state.is(Blocks.STONE_BRICK_STAIRS)) {
            return withPropertiesOf(state, craft.wallStairs());
        }
        if (state.is(Blocks.OAK_FENCE)) {
            return withPropertiesOf(state, craft.fence());
        }
        if (state.is(Blocks.OAK_FENCE_GATE)) {
            return withPropertiesOf(state, craft.fenceGate());
        }
        return state;
    }

    private static BlockState withPropertiesOf(BlockState from, BlockState to) {
        for (Property<?> property : from.getProperties()) {
            if (to.hasProperty(property)) {
                to = copy(from, to, property);
            }
        }
        return to;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to,
            Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

    /** Whether the block at a template position is what the author built it in. */
    public static boolean isMasonry(BlockState state) {
        return state.is(Blocks.STONE_BRICKS);
    }

    /** For a test: whether this block is one the family table changes. */
    public static boolean isRemapped(Block block) {
        return block == Blocks.STONE_BRICKS || block == Blocks.STONE_BRICK_STAIRS
                || block == Blocks.OAK_FENCE || block == Blocks.OAK_FENCE_GATE;
    }
}
