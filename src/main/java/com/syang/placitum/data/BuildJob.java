package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.Item;

/**
 * One queued build.
 *
 * <p>{@code progress} is the whole bridge between L0 and L2: the virtual step increments it,
 * the real builder increments it, and promote replays ops[0, progress).
 */
public record BuildJob(
        UUID id,
        UUID plotId,
        BuildRecipe recipe,
        int progress,
        Map<Item, Integer> cost,
        BuildStage stage) {

    public static final Codec<BuildJob> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(BuildJob::id),
            UUIDUtil.CODEC.fieldOf("plot_id").forGetter(BuildJob::plotId),
            BuildRecipe.CODEC.fieldOf("recipe").forGetter(BuildJob::recipe),
            Codec.INT.fieldOf("progress").forGetter(BuildJob::progress),
            PlacitumCodecs.ITEM_COUNTS.fieldOf("cost").forGetter(BuildJob::cost),
            BuildStage.CODEC.fieldOf("stage").forGetter(BuildJob::stage)
    ).apply(i, BuildJob::new));

    public BuildJob withProgress(int newProgress) {
        return new BuildJob(id, plotId, recipe, newProgress, cost, stage);
    }

    public BuildJob withStage(BuildStage newStage) {
        return new BuildJob(id, plotId, recipe, progress, cost, newStage);
    }
}
