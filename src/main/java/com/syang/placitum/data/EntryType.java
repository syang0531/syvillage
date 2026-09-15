package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Kind of chronicle entry. The chronicle is for the player, never a simulation input. */
public enum EntryType implements StringRepresentable {
    BIRTH,
    DEATH,
    BUILD,
    RAID_REPELLED,
    RAID_LOST,
    SCALE_UP,
    SCALE_DOWN,
    FAMINE,
    ZOMBIFIED,
    CURED,
    IMMIGRATION,
    GAP;

    public static final Codec<EntryType> CODEC = StringRepresentable.fromEnum(EntryType::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
