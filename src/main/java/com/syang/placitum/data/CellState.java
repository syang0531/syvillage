package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** State of one plot-grid cell. Stored, never rescanned from the world. */
public enum CellState implements StringRepresentable {
    FREE,
    ROAD,
    RESERVED,
    BUILT,
    BLOCKED;

    public static final Codec<CellState> CODEC = StringRepresentable.fromEnum(CellState::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
