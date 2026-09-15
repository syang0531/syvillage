package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** INFANT is never spawned as an entity - see docs/population.md. */
public enum LifeStage implements StringRepresentable {
    INFANT,
    CHILD,
    ADULT,
    ELDER;

    public static final Codec<LifeStage> CODEC = StringRepresentable.fromEnum(LifeStage::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
