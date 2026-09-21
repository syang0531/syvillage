package com.syang.syvillage.build;

import com.syang.syvillage.data.BuildOp;
import com.syang.syvillage.data.Craft;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * A structure built by hand in a creative world, saved with a structure block, and shipped
 * in the jar.
 *
 * <p>This is the answer to a question the gatehouse and the tower kept asking: how does
 * somebody who is not a programmer change the shape of a thing the settlement builds? They
 * build it. A structure block writes it out as NBT, the file goes in
 * {@code data/syvillage/structure/}, and this reads it back - without the game running,
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

    private final Identifier id;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<Piece> pieces;
    private final @Nullable Direction front;
    private final int entranceY;

    private Template(Identifier id, int sizeX, int sizeY, int sizeZ, List<Piece> pieces,
            @Nullable Direction front, int entranceY) {
        this.id = id;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.pieces = List.copyOf(pieces);
        this.front = front;
        this.entranceY = entranceY;
    }

    /** One of ours, by name: {@code data/syvillage/structure/<name>.nbt}. */
    public static Template of(String name) {
        return of(Identifier.fromNamespaceAndPath("syvillage", name));
    }

    /**
     * A template already loaded, ours or vanilla's.
     *
     * <p>Ours are read from our own jar on first use. Vanilla's village buildings are in the
     * game's jar, which is another module: its packages are closed to us, so they cannot be
     * read as a resource - and they are not copied into our jar either, being Mojang's. They
     * are read through the game's own {@link StructureTemplateManager} by {@link #ensure} at
     * the two places that have a level (planning, and laying), and cached; after that
     * expansion finds them here and stays pure.
     */
    public static Template of(Identifier id) {
        Template cached = LOADED.get(id.toString());
        if (cached != null) {
            return cached;
        }
        if (!id.getNamespace().equals("syvillage")) {
            throw new IllegalStateException(id + " has not been loaded; Template.ensure first");
        }
        return LOADED.computeIfAbsent(id.toString(), key -> read(id));
    }

    /** Loads a vanilla template through the game, if it is not loaded already. */
    public static Template ensure(StructureTemplateManager manager, Identifier id) {
        Template cached = LOADED.get(id.toString());
        if (cached != null) {
            return cached;
        }
        StructureTemplate template = manager.get(id).orElseThrow(
                () -> new IllegalStateException("the game has no structure " + id));
        return LOADED.computeIfAbsent(id.toString(),
                key -> parse(id, template.save(new CompoundTag())));
    }

    /**
     * Loads a template straight out of a jar. For tests, which have no game to ask: the
     * game's jar is on their classpath, and this reads the data from it in place.
     */
    public static Template loadFromJar(Path jar, Identifier id) {
        String entry = "data/" + id.getNamespace() + "/structure/" + id.getPath() + ".nbt";
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry e = zip.getEntry(entry);
            if (e == null) {
                throw new IllegalStateException("no " + entry + " in " + jar);
            }
            try (InputStream in = zip.getInputStream(e)) {
                CompoundTag nbt = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
                return LOADED.computeIfAbsent(id.toString(), key -> parse(id, nbt));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("could not read " + entry + " from " + jar, ex);
        }
    }

    private static Template read(Identifier id) {
        String path = "/data/" + id.getNamespace() + "/structure/" + id.getPath() + ".nbt";
        try (InputStream in = Template.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("no template at " + path);
            }
            return parse(id, NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap()));
        } catch (IOException e) {
            throw new IllegalStateException("could not read template " + path, e);
        }
    }

    /** A template from its NBT, whichever way the NBT arrived. */
    private static Template parse(Identifier id, CompoundTag nbt) {
        {
            ListTag size = nbt.getListOrEmpty("size");
            ListTag palette = nbt.getListOrEmpty("palette");
            List<BlockState> states = new ArrayList<>(palette.size());
            for (int i = 0; i < palette.size(); i++) {
                states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK,
                        palette.getCompoundOrEmpty(i)));
            }
            List<Piece> pieces = new ArrayList<>();
            Direction front = null;
            int entranceY = 0;
            ListTag blocks = nbt.getListOrEmpty("blocks");
            for (int i = 0; i < blocks.size(); i++) {
                CompoundTag block = blocks.getCompoundOrEmpty(i);
                ListTag pos = block.getListOrEmpty("pos");
                BlockState state = states.get(block.getIntOr("state", 0));
                if (state.is(Blocks.JIGSAW)) {
                    // A jigsaw is village generation's connector. What it turns into once the
                    // village is generated is written in it, and the one named
                    // building_entrance also says which way the building faces - which is the
                    // table of forty-two door directions nobody has to write by hand.
                    CompoundTag meta = block.getCompoundOrEmpty("nbt");
                    if (meta.getStringOr("name", "").equals("minecraft:building_entrance")) {
                        front = state.getValue(JigsawBlock.ORIENTATION).front();
                        entranceY = pos.getIntOr(1, 0);
                    }
                    state = finalState(meta.getStringOr("final_state", "minecraft:air"));
                }
                if (state.isAir() || state.is(Blocks.STRUCTURE_VOID)) {
                    continue;   // saved air, or "leave the world alone here": we build neither
                }
                pieces.add(new Piece(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0),
                        state));
            }
            return new Template(id, size.getIntOr(0, 0), size.getIntOr(1, 0),
                    size.getIntOr(2, 0), pieces, front, entranceY);
        }
    }

    private static BlockState finalState(String text) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, text, false)
                    .blockState();
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("a jigsaw's final_state does not parse: " + text, e);
        }
    }

    public Identifier id() {
        return id;
    }

    public String name() {
        return id.getPath();
    }

    /**
     * Which way the building faces, from its {@code building_entrance} jigsaw; null if it has
     * none. Ours have none - they are placed by where the wall is, not by a street.
     */
    public @Nullable Direction front() {
        return front;
    }

    /**
     * How many blocks above the ground the lowest layer goes.
     *
     * <p>One for ours: a structure block save starts at the first block standing on the grass.
     * For a vanilla village building it is whatever puts its {@code building_entrance} jigsaw
     * one above the ground, because that is where a street piece's jigsaw is - the path blocks
     * are the piece's layer 0 and replace the surface, and its jigsaws stand on them. A plains
     * house has its entrance on layer 0, so it sits a block up with its doorstep stair on the
     * grass; a desert house has it on layer 1 and sits on the ground with its door where you
     * walk in. Placing every vanilla house at ground level put the plains doorsteps in a hole.
     */
    public int lift() {
        return front == null ? 1 : 1 - entranceY;
    }

    /** How many beds, counted by their heads. What a lot of it is worth to {@code Need}. */
    public int bedCount() {
        int beds = 0;
        for (Piece piece : pieces) {
            if (piece.state().getBlock() instanceof BedBlock
                    && piece.state().getValue(BedBlock.PART) == BedPart.HEAD) {
                beds++;
            }
        }
        return beds;
    }

    /** Whether the building comes with a workstation a villager could take a job at. */
    public boolean hasJobBlock() {
        for (Piece piece : pieces) {
            if (isJobSite(piece.state())) {
                return true;
            }
        }
        return false;
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

    /** The lowest block in a column, or -1 for none. */
    public int bottomOf(int x, int z) {
        int bottom = -1;
        for (Piece piece : pieces) {
            if (piece.x() == x && piece.z() == z && (bottom < 0 || piece.y() < bottom)) {
                bottom = piece.y();
            }
        }
        return bottom;
    }

    // ---- turned inside its own box, for a template placed by its corner rather than about
    // ---- the bell: a house on a lot.

    /** The turned box's width along x. A quarter turn swaps the two. */
    public int turnedWidth(Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90
                ? sizeZ : sizeX;
    }

    public int turnedDepth(Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90
                ? sizeX : sizeZ;
    }

    /**
     * A template column turned within the box, so the turned box still starts at (0, 0).
     *
     * <p>The same turn as {@link #turn}, then shifted back into the corner: clockwise takes
     * north to east, which for a box means the west edge becomes the north edge.
     */
    public int[] turnInBox(int x, int z, Rotation rotation) {
        return switch (rotation) {
            case NONE -> new int[] {x, z};
            case CLOCKWISE_90 -> new int[] {sizeZ - 1 - z, x};
            case CLOCKWISE_180 -> new int[] {sizeX - 1 - x, sizeZ - 1 - z};
            case COUNTERCLOCKWISE_90 -> new int[] {z, sizeX - 1 - x};
        };
    }

    /** Where one template column lands, for a template placed by its turned box's corner. */
    public BlockPos columnAt(BlockPos origin, Rotation rotation, int x, int z) {
        int[] v = turnInBox(x, z, rotation);
        return new BlockPos(origin.getX() + v[0], origin.getY(), origin.getZ() + v[1]);
    }

    /**
     * The template's blocks in the world, placed by its turned box's corner.
     *
     * @param base the world y its lowest layer goes at
     */
    public List<BuildOp> placeAt(BlockPos origin, Rotation rotation, Craft craft, int base) {
        List<BuildOp> ops = new ArrayList<>(pieces.size());
        for (Piece piece : pieces) {
            int[] v = turnInBox(piece.x(), piece.z(), rotation);
            ops.add(new BuildOp(new BlockPos(origin.getX() + v[0], base + piece.y(),
                    origin.getZ() + v[1]), remap(piece.state().rotate(rotation), craft)));
        }
        return ops;
    }

    /** Villager job sites. Their presence is the strongest signal of all: someone works here. */
    /** Whether a villager could take a job at this block. */
    public static boolean isJobSite(BlockState state) {
        return isWorkstation(state);
    }

    private static boolean isWorkstation(BlockState state) {
        return state.is(Blocks.COMPOSTER) || state.is(Blocks.BARREL)
                || state.is(Blocks.SMOKER) || state.is(Blocks.BLAST_FURNACE)
                || state.is(Blocks.CAULDRON) || state.is(Blocks.BREWING_STAND)
                || state.is(Blocks.FLETCHING_TABLE) || state.is(Blocks.SMITHING_TABLE)
                || state.is(Blocks.CARTOGRAPHY_TABLE) || state.is(Blocks.LOOM)
                || state.is(Blocks.STONECUTTER) || state.is(Blocks.GRINDSTONE)
                || state.is(Blocks.LECTERN);
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
}
