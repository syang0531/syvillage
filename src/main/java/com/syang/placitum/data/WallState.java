package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;

/** The wall ring and its gates. */
public record WallState(WallTier tier, List<BlockPos> ring, List<GateNode> gates, boolean intact) {

    public static final Codec<WallState> CODEC = RecordCodecBuilder.create(i -> i.group(
            WallTier.CODEC.fieldOf("tier").forGetter(WallState::tier),
            BlockPos.CODEC.listOf().fieldOf("ring").forGetter(WallState::ring),
            GateNode.CODEC.listOf().fieldOf("gates").forGetter(WallState::gates),
            Codec.BOOL.fieldOf("intact").forGetter(WallState::intact)
    ).apply(i, WallState::new));

    public static final WallState NONE = new WallState(WallTier.NONE, List.of(), List.of(), true);
}
