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
    /** The survey judged it unbuildable: water, or too much relief. Revised on every survey. */
    BLOCKED,
    /**
     * The player said no.
     *
     * <p>Distinct from {@link #BLOCKED} because the survey must be free to change its own mind
     * and must never change the player's. Sharing one state meant a cell the survey rejected
     * once was rejected for ever - it skipped anything already BLOCKED to protect manual marks,
     * so a cell blocked by a tree stayed blocked after the tree was gone, after the bug that
     * saw it was fixed, and after the player levelled the ground.
     */
    FORBIDDEN;

    public static final Codec<CellState> CODEC = StringRepresentable.fromEnum(CellState::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
