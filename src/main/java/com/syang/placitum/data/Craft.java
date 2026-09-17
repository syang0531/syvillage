package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the village knows how to build with.
 *
 * <p>A village builds in stone because somebody in it works stone. That is the whole rule: the
 * standard is read off the trades its villagers have taken, which vanilla decides from the
 * workstations the player put down. Nothing here is counted or simulated - placing a stonecutter
 * and a spare villager taking the job is the player's move, and the paving changing colour is
 * the village's answer to it.
 *
 * <p>Deliberately not the population. Population is something a player waits for rather than
 * something they do, and it goes down when a zombie gets in; a trade is one object placed and
 * one obvious consequence.
 *
 * <p>It never falls. A settlement keeps the best standard it has ever reached, so losing the
 * mason to a creeper does not turn the high street back into mud.
 */
public enum Craft implements StringRepresentable {

    /** Where every village starts. Dirt tracks and oak. */
    TIMBER("timber",
            Blocks.DIRT_PATH.defaultBlockState(),
            Blocks.OAK_PLANKS.defaultBlockState(),
            Blocks.OAK_PLANKS.defaultBlockState(),
            Blocks.COBBLESTONE.defaultBlockState()),

    /**
     * A mason lives here.
     *
     * <p>Cobblestone walls under an oak roof, which is what vanilla's own village houses look
     * like - and the reason this is the middle rung rather than the top. A village that reached
     * its best standard the moment one villager picked up a stonecutter would not have much of a
     * story left.
     */
    STONE("stone",
            Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.OAK_PLANKS.defaultBlockState(),
            Blocks.STONE_BRICKS.defaultBlockState());

    public static final Codec<Craft> CODEC = StringRepresentable.fromEnum(Craft::values);

    private final String name;
    private final BlockState paving;
    private final BlockState wall;
    private final BlockState roof;
    private final BlockState foundation;

    Craft(String name, BlockState paving, BlockState wall, BlockState roof,
            BlockState foundation) {
        this.name = name;
        this.paving = paving;
        this.wall = wall;
        this.roof = roof;
        this.foundation = foundation;
    }

    /** What the streets are made of. */
    public BlockState paving() {
        return paving;
    }

    /** Walls, and the floor inside them. */
    public BlockState wall() {
        return wall;
    }

    public BlockState floor() {
        return wall;
    }

    /** The roof, which stays timber long after the walls stop being it. */
    public BlockState roof() {
        return roof;
    }

    /** What holds a house up where the ground falls away under it. */
    public BlockState foundation() {
        return foundation;
    }

    /** The better of two standards. Used to keep the high-water mark. */
    public Craft or(Craft other) {
        return ordinal() >= other.ordinal() ? this : other;
    }

    public boolean betterThan(Craft other) {
        return ordinal() > other.ordinal();
    }

    /** Whether this block is paving from any standard - ours to replace when we improve. */
    public static boolean isPaving(BlockState state) {
        for (Craft craft : values()) {
            if (state.is(craft.paving().getBlock())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The standard as it is frozen into a build recipe.
     *
     * <p>A recipe carries its standard the way it carries its ground profile: read once, written
     * down, never looked up again. A street half laid when the mason arrives finishes in the
     * stone it started in, and expand stays a pure function of its recipe rather than of
     * whoever happens to live here when it runs.
     */
    public Identifier paletteId() {
        return Identifier.fromNamespaceAndPath("placitum", "craft/" + name);
    }

    /** The standard a recipe was frozen with, or timber for anything unrecognised. */
    public static Craft fromPalette(Identifier palette) {
        for (Craft craft : values()) {
            if (craft.paletteId().equals(palette)) {
                return craft;
            }
        }
        return TIMBER;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
