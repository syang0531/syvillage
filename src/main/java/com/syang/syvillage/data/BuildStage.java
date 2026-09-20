package com.syang.syvillage.data;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Stage of the six-step construction pipeline. See docs/construction.md. */
public enum BuildStage implements StringRepresentable {
    PLANNED,
    RESERVED,
    QUEUED,
    EXECUTING,
    WAITING_MATERIALS,
    COMPLETE;

    public static final Codec<BuildStage> CODEC = StringRepresentable.fromEnum(BuildStage::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
