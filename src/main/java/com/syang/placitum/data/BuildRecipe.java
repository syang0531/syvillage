package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;

/**
 * The inputs an op list is expanded from. Ops themselves are never stored: a house is a few
 * thousand of them, and the settlement's save file is rewritten whole every time it changes.
 *
 * <p>{@code groundProfile} is sampled once at QUEUE time and frozen. Without it, expansion
 * would depend on the current terrain and stop being a pure function, so a replay weeks later
 * would not match what the virtual simulation already counted as built.
 */
public record BuildRecipe(
        Identifier template,
        BlockPos anchor,
        Rotation rotation,
        Identifier palette,
        List<Integer> groundProfile) {

    public static final Codec<BuildRecipe> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("template").forGetter(BuildRecipe::template),
            BlockPos.CODEC.fieldOf("anchor").forGetter(BuildRecipe::anchor),
            Rotation.CODEC.fieldOf("rotation").forGetter(BuildRecipe::rotation),
            Identifier.CODEC.fieldOf("palette").forGetter(BuildRecipe::palette),
            PlacitumCodecs.INT_LIST.fieldOf("ground_profile").forGetter(BuildRecipe::groundProfile)
    ).apply(i, BuildRecipe::new));
}
