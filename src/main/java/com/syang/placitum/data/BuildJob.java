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
        BuildStage stage,
        int attempts) {

    /**
     * How many times this job has had to start a stretch over.
     *
     * <p>Counted so that a player who keeps clearing a site is eventually taken at their word.
     * Without it, sample verification is an infinite loop with extra steps: the settlement
     * rebuilds, the player breaks it, for ever.
     */
    public BuildJob withAttempt() {
        return new BuildJob(id, plotId, recipe, progress, cost, stage, attempts + 1);
    }

    public static final Codec<BuildJob> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(BuildJob::id),
            UUIDUtil.CODEC.fieldOf("plot_id").forGetter(BuildJob::plotId),
            BuildRecipe.CODEC.fieldOf("recipe").forGetter(BuildJob::recipe),
            Codec.INT.fieldOf("progress").forGetter(BuildJob::progress),
            PlacitumCodecs.ITEM_COUNTS.fieldOf("cost").forGetter(BuildJob::cost),
            BuildStage.CODEC.fieldOf("stage").forGetter(BuildJob::stage),
            Codec.INT.optionalFieldOf("attempts", 0).forGetter(BuildJob::attempts)
    ).apply(i, BuildJob::new));

    public BuildJob withProgress(int newProgress) {
        return new BuildJob(id, plotId, recipe, newProgress, cost, stage, attempts);
    }

    public BuildJob withStage(BuildStage newStage) {
        return new BuildJob(id, plotId, recipe, progress, cost, newStage, attempts);
    }
}
