package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** What a plot is for. */
public enum PlotKind implements StringRepresentable {
    HOUSE,
    FARM,
    WORKSHOP,
    ARMORY,
    WATCHTOWER,
    GRAVEYARD;

    public static final Codec<PlotKind> CODEC = StringRepresentable.fromEnum(PlotKind::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
