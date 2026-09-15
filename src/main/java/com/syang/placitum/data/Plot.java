package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;

/** One building and the cells it occupies. */
public record Plot(
        UUID id,
        CellPos anchor,
        int cellW,
        int cellH,
        Rotation rotation,
        Identifier template,
        PlotKind kind,
        int bedCount,
        List<UUID> occupants) {

    public static final Codec<Plot> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Plot::id),
            CellPos.CODEC.fieldOf("anchor").forGetter(Plot::anchor),
            Codec.INT.fieldOf("cell_w").forGetter(Plot::cellW),
            Codec.INT.fieldOf("cell_h").forGetter(Plot::cellH),
            Rotation.CODEC.fieldOf("rotation").forGetter(Plot::rotation),
            Identifier.CODEC.fieldOf("template").forGetter(Plot::template),
            PlotKind.CODEC.fieldOf("kind").forGetter(Plot::kind),
            Codec.INT.fieldOf("bed_count").forGetter(Plot::bedCount),
            UUIDUtil.CODEC.listOf().fieldOf("occupants").forGetter(Plot::occupants)
    ).apply(i, Plot::new));
}
