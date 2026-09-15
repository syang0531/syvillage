package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** LOD tier of one resident. The flag lives on the resident, not the settlement. */
public enum ResidentState implements StringRepresentable {
    VIRTUAL,
    MATERIALIZED;

    public static final Codec<ResidentState> CODEC = StringRepresentable.fromEnum(ResidentState::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
