package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * How far a settlement has come, as the three things it is allowed to do.
 *
 * <p>Each stage is a verb, not a material. That is the whole reason there are three of them
 * rather than four: the ladder this replaces had a rung called <em>cobblestone</em> and a rung
 * called <em>stone brick</em>, neither of which was anything a town could newly do, and one of
 * which belonged to a vanilla mason rather than to anything of ours.
 *
 * <ul>
 *   <li>{@link #LIT} - the bell has been rung. The settlement lights its ground, and that is all.
 *       It is also everything the mod promises: monsters kill villagers at night, and the answer
 *       is light. The other two stages are built on top of that, not instead of it.
 *   <li>{@link #HEADED} - somebody holds the village head's table. Streets get laid and lots get
 *       built on. A street is a construction and light is a utility: the grid of roads is the
 *       one visible sign that someone is organising this place, and that someone is the head.
 *   <li>{@link #WALLED} - somebody holds the lord's table. Wall, gates, towers, steps.
 * </ul>
 *
 * <p>A ratchet. Every entitlement is read off a living village, and a head can be eaten; a town
 * does not tear up its own streets because nobody is at the table this afternoon.
 */
public enum Stage implements StringRepresentable {

    LIT("lit"),
    HEADED("headed"),
    WALLED("walled");

    public static final Codec<Stage> CODEC = StringRepresentable.fromEnum(Stage::values);

    private final String name;

    Stage(String name) {
        this.name = name;
    }

    /** The further along of two. Used to keep the high-water mark. */
    public Stage or(Stage other) {
        return ordinal() >= other.ordinal() ? this : other;
    }

    public boolean atLeast(Stage other) {
        return ordinal() >= other.ordinal();
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
