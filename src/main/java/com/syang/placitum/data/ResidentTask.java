package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

/** What a resident is currently doing, and how far along they are. */
public record ResidentTask(Identifier kind, int progress, Optional<UUID> target) {

    public static final Codec<ResidentTask> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("kind").forGetter(ResidentTask::kind),
            Codec.INT.fieldOf("progress").forGetter(ResidentTask::progress),
            UUIDUtil.CODEC.optionalFieldOf("target").forGetter(ResidentTask::target)
    ).apply(i, ResidentTask::new));

    public static final Identifier IDLE_KIND = Identifier.fromNamespaceAndPath("placitum", "idle");
    public static final ResidentTask IDLE = new ResidentTask(IDLE_KIND, 0, Optional.empty());
}
