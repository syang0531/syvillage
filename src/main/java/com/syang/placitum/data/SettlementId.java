package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Identity and footprint of a settlement. Never changes after registration except the name. */
public record SettlementId(
        UUID id,
        String name,
        ResourceKey<Level> dimension,
        BlockPos center,
        int claimRadiusChunks) {

    public static final Codec<SettlementId> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(SettlementId::id),
            Codec.STRING.fieldOf("name").forGetter(SettlementId::name),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(SettlementId::dimension),
            BlockPos.CODEC.fieldOf("center").forGetter(SettlementId::center),
            Codec.INT.fieldOf("claim_radius_chunks").forGetter(SettlementId::claimRadiusChunks)
    ).apply(i, SettlementId::new));

    public SettlementId withName(String newName) {
        return new SettlementId(id, newName, dimension, center, claimRadiusChunks);
    }
}
