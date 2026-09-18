package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the village builds with: a palette, chosen once by the biome the bell stands in.
 *
 * <p>This used to be a ladder - timber, then cobblestone when a mason moved in, then stone brick
 * when a village head did - and the ladder betrayed itself at the top. A walled town was stone
 * brick from the road to the roof, and the thing put in as evidence of growth became, at the
 * end of growth, a town nobody would want to live in. Growth is now the stages in {@link Stage},
 * which are things a town can do; this is only what it looks like doing them.
 *
 * <p>Streets and fortifications only. Houses will bring their own materials with the vanilla
 * templates they are built from, so there is no house palette here to be thrown away then; the
 * cottage that stands in until that day reads {@link #foundation()} for its walls, which is the
 * cobblestone-under-a-timber-roof look of a vanilla village house.
 *
 * <p>The fortification templates are built in stone brick and oak, because that is what a
 * creative player builds in; {@link #wallStairs()}, {@link #fence()} and {@link #fenceGate()}
 * are what those become here, family for family, so a stair stays a stair and keeps its facing.
 *
 * <p>The last three constants are not palettes anybody chooses. They are the rungs of the old
 * ladder, kept because saved settlements and queued recipes still name them, and a save that
 * names a palette that does not exist is a save that does not load.
 */
public enum Craft implements StringRepresentable {

    PLAINS("plains", Blocks.DIRT_PATH, Blocks.STONE_BRICKS, Blocks.OAK_PLANKS, Blocks.COBBLESTONE,
            Blocks.STONE_BRICK_STAIRS, Blocks.OAK_FENCE, Blocks.OAK_FENCE_GATE),

    TAIGA("taiga", Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.SPRUCE_PLANKS, Blocks.COBBLESTONE,
            Blocks.STONE_BRICK_STAIRS, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_FENCE_GATE),

    SNOWY("snowy", Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.SPRUCE_PLANKS, Blocks.COBBLESTONE,
            Blocks.STONE_BRICK_STAIRS, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_FENCE_GATE),

    SAVANNA("savanna", Blocks.DIRT_PATH, Blocks.STONE_BRICKS, Blocks.ACACIA_PLANKS, Blocks.COBBLESTONE,
            Blocks.STONE_BRICK_STAIRS, Blocks.ACACIA_FENCE, Blocks.ACACIA_FENCE_GATE),

    DESERT("desert", Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE_SLAB,
            Blocks.SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.OAK_FENCE, Blocks.OAK_FENCE_GATE),

    /** Legacy. The bottom rung of the old ladder; only ever read from a save. */
    TIMBER("timber", Blocks.DIRT_PATH, Blocks.OAK_PLANKS, Blocks.OAK_PLANKS, Blocks.COBBLESTONE,
            Blocks.OAK_STAIRS, Blocks.OAK_FENCE, Blocks.OAK_FENCE_GATE),

    /** Legacy. The mason's rung. */
    STONE("stone", Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.STONE_BRICKS,
            Blocks.COBBLESTONE_STAIRS, Blocks.OAK_FENCE, Blocks.OAK_FENCE_GATE),

    /** Legacy. The village head's rung, and the one every test world is saved in. */
    MASONRY("masonry", Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB,
            Blocks.STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.OAK_FENCE, Blocks.OAK_FENCE_GATE);

    public static final Codec<Craft> CODEC = StringRepresentable.fromEnum(Craft::values);

    private final String name;
    private final BlockState paving;
    private final BlockState wall;
    private final BlockState roof;
    private final BlockState foundation;
    private final BlockState wallStairs;
    private final BlockState fence;
    private final BlockState fenceGate;

    Craft(String name, Block paving, Block wall, Block roof, Block foundation, Block wallStairs,
            Block fence, Block fenceGate) {
        this.name = name;
        this.paving = paving.defaultBlockState();
        this.wall = wall.defaultBlockState();
        this.roof = roof.defaultBlockState();
        this.foundation = foundation.defaultBlockState();
        this.wallStairs = wallStairs.defaultBlockState();
        this.fence = fence.defaultBlockState();
        this.fenceGate = fenceGate.defaultBlockState();
    }

    /**
     * The palette for a biome, decided the way vanilla decides which village to generate there.
     *
     * <p>The {@code has_village_*} tags are exactly that decision, which is why they come first:
     * a place where vanilla would put a snowy village gets snowy streets. The broader {@code
     * is_*} tags catch the biomes vanilla has no village for at all, and the rest is plains.
     */
    public static Craft of(Holder<Biome> biome) {
        if (biome.is(BiomeTags.HAS_VILLAGE_DESERT) || biome.is(BiomeTags.IS_BADLANDS)) {
            return DESERT;
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY)) {
            return SNOWY;
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_SAVANNA) || biome.is(BiomeTags.IS_SAVANNA)) {
            return SAVANNA;
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_TAIGA) || biome.is(BiomeTags.IS_TAIGA)) {
            return TAIGA;
        }
        return PLAINS;
    }

    /** What the streets are made of. */
    public BlockState paving() {
        return paving;
    }

    /** Fortifications: the rampart, the gatehouses, the towers. */
    public BlockState wall() {
        return wall;
    }

    /** A stair of the fortification's material, for the templates' stairs. */
    public BlockState wallStairs() {
        return wallStairs;
    }

    /** The fence of the palette's wood, for the templates' fences. */
    public BlockState fence() {
        return fence;
    }

    /** And its gate - the ones across the gatehouse's way through. */
    public BlockState fenceGate() {
        return fenceGate;
    }

    /** The floor of a house, which is the same block as what holds it up. */
    public BlockState floor() {
        return foundation;
    }

    /** The roof of a house. */
    public BlockState roof() {
        return roof;
    }

    /**
     * What holds a house up where the ground falls away under it - and, until the templates
     * arrive, what its walls are made of.
     */
    public BlockState foundation() {
        return foundation;
    }

    /**
     * Whether this block is paving of any palette, ours to leave alone.
     *
     * <p>Any palette, the legacy ones included: a town does not take up its own street because
     * it has since been told it is a plains town, and a queued recipe from the old ladder still
     * lays what it was frozen with.
     */
    public static boolean isPaving(BlockState state) {
        for (Craft craft : values()) {
            if (state.is(craft.paving().getBlock())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The palette as it is frozen into a build recipe.
     *
     * <p>A recipe carries its palette the way it carries its ground profile: read once, written
     * down, never looked up again, so that expand stays a pure function of its recipe.
     */
    public Identifier paletteId() {
        return Identifier.fromNamespaceAndPath("placitum", "craft/" + name);
    }

    /** The palette a recipe was frozen with, or plains for anything unrecognised. */
    public static Craft fromPalette(Identifier palette) {
        for (Craft craft : values()) {
            if (craft.paletteId().equals(palette)) {
                return craft;
            }
        }
        return PLAINS;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
