package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Settlement-wide alarm state. See docs/defense.md. */
public enum AlertState implements StringRepresentable {
    PEACE,
    ALERT,
    COMBAT,
    ROUT;

    public static final Codec<AlertState> CODEC = StringRepresentable.fromEnum(AlertState::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
