package com.syang.syvillage.data;

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
        List<Integer> groundProfile,
        BlockPos extent,
        List<Integer> gates) {

    /**
     * Width, height and depth in blocks.
     *
     * <p>A structure template carries its own dimensions, but a wall is not a template - it is a
     * ring computed from wherever the settlement happens to have spread. Without its extent in
     * the recipe, expansion would have to consult the plot grid, and the grid changes. That is
     * exactly the dependency groundProfile exists to remove: the recipe has to be sufficient on
     * its own or expansion stops being a pure function of it.
     */
    public int width() {
        return extent.getX();
    }

    public int height() {
        return extent.getY();
    }

    public int depth() {
        return extent.getZ();
    }

    public static final Codec<BuildRecipe> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("template").forGetter(BuildRecipe::template),
            BlockPos.CODEC.fieldOf("anchor").forGetter(BuildRecipe::anchor),
            Rotation.CODEC.fieldOf("rotation").forGetter(BuildRecipe::rotation),
            Identifier.CODEC.fieldOf("palette").forGetter(BuildRecipe::palette),
            SyVillageCodecs.INT_LIST.fieldOf("ground_profile").forGetter(BuildRecipe::groundProfile),
            // Optional with a default: no save has ever held a build job, but the rule is the
            // rule, and a required field added to a stored shape is how a roster gets emptied.
            BlockPos.CODEC.optionalFieldOf("extent", BlockPos.ZERO).forGetter(BuildRecipe::extent),
            // Frozen alongside the ground for the same reason: where the roads cross the
            // ring is a fact about the grid, and the grid moves. Expansion may not consult it.
            SyVillageCodecs.INT_LIST.optionalFieldOf("gates", List.of()).forGetter(BuildRecipe::gates)
    ).apply(i, BuildRecipe::new));
}
