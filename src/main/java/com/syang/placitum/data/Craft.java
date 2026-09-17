package com.syang.placitum.data;

import com.mojang.serialization.Codec;
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

    /** Where every village starts. Dirt paths and oak. */
    TIMBER("timber", Blocks.DIRT_PATH.defaultBlockState()),

    /** A mason lives here. */
    STONE("stone", Blocks.COBBLESTONE.defaultBlockState());

    public static final Codec<Craft> CODEC = StringRepresentable.fromEnum(Craft::values);

    private final String name;
    private final BlockState paving;

    Craft(String name, BlockState paving) {
        this.name = name;
        this.paving = paving;
    }

    /** What the streets are made of. */
    public BlockState paving() {
        return paving;
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

    @Override
    public String getSerializedName() {
        return name;
    }
}
