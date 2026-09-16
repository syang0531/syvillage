package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * Axis 1 - size. Grows automatically with population; the player is not involved.
 * Axis 2 (standing) is V2 and is not implemented - see docs/v2-deferred.md.
 */
public enum ScaleTier implements StringRepresentable {
    OUTPOST(1, 4, 3),
    HAMLET(5, 11, 5),
    VILLAGE(12, 24, 9),
    TOWN(25, 44, 13),
    CITY(45, 999, 17);

    public static final Codec<ScaleTier> CODEC = StringRepresentable.fromEnum(ScaleTier::values);

    private final int minPop;
    private final int maxPop;
    private final int gridSize;   // now a radius; see buildRadiusCells

    ScaleTier(int minPop, int maxPop, int gridSize) {
        this.minPop = minPop;
        this.maxPop = maxPop;
        this.gridSize = gridSize;
    }

    public int minPop() {
        return minPop;
    }

    public int maxPop() {
        return maxPop;
    }

    /**
     * How far from the bell a settlement of this size may build, in cells.
     *
     * <p>A radius, not a side length. These numbers were written when the plot grid was sized
     * from the tier, so they were widths - and reading a width of 3 as a radius gives an
     * OUTPOST eight blocks of buildable ground, all of it its own market square. Every house
     * order was dropped for nowhere to put it.
     *
     * <p>The grid maps the whole claim now; this is the budget on spreading out.
     * docs/open-questions.md 3.
     */
    public int buildRadiusCells() {
        return gridSize;
    }

    public ScaleTier next() {
        return this == CITY ? this : values()[ordinal() + 1];
    }

    public ScaleTier previous() {
        return this == OUTPOST ? this : values()[ordinal() - 1];
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
