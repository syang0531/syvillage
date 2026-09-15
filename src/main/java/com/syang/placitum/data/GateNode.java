package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A gap in the wall ring that pathfinding is allowed through.
 *
 * <p>V1 gates are wooden doors or fence gates. Iron doors are not an option: villagers cannot
 * open them and vanilla pathfinding treats them as solid, so a farmer would stand in front of
 * one forever - see docs/defense.md.
 */
public record GateNode(BlockPos pos, Direction facing, boolean open) {

    public static final Codec<GateNode> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(GateNode::pos),
            Direction.CODEC.fieldOf("facing").forGetter(GateNode::facing),
            Codec.BOOL.fieldOf("open").forGetter(GateNode::open)
    ).apply(i, GateNode::new));
}
