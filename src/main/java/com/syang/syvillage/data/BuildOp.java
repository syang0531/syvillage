package com.syang.syvillage.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** One block placement. The unit of everything the settlement does to the world. */
public record BuildOp(BlockPos pos, BlockState state) {

    public static final Codec<BuildOp> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(BuildOp::pos),
            BlockState.CODEC.fieldOf("state").forGetter(BuildOp::state)
    ).apply(i, BuildOp::new));
}
