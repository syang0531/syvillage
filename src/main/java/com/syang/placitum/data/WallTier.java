package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Wall grade. V1 implements up to PALISADE; see docs/construction.md. */
public enum WallTier implements StringRepresentable {
    NONE(0),
    FENCE(1),
    PALISADE(2),
    STONE(3),
    RAMPART(4);

    public static final Codec<WallTier> CODEC = StringRepresentable.fromEnum(WallTier::values);

    private final int grade;

    WallTier(int grade) {
        this.grade = grade;
    }

    /**
     * Feeds defenseRating. An explicit number rather than ordinal() so that inserting a
     * tier later cannot silently rebalance every settlement in every existing save.
     */
    public int grade() {
        return grade;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
