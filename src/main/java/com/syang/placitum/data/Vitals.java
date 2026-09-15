package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Health, morale, hunger. All integers - see docs/open-questions.md on floating point. */
public record Vitals(int health, int morale, int hunger) {

    public static final Codec<Vitals> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("health").forGetter(Vitals::health),
            Codec.INT.fieldOf("morale").forGetter(Vitals::morale),
            Codec.INT.fieldOf("hunger").forGetter(Vitals::hunger)
    ).apply(i, Vitals::new));

    public static final Vitals HEALTHY = new Vitals(20, 50, 50);

    public Vitals withHealth(int newHealth) {
        return new Vitals(newHealth, morale, hunger);
    }

    public Vitals withMorale(int newMorale) {
        return new Vitals(health, Math.clamp(newMorale, 0, 100), hunger);
    }

    public Vitals withHunger(int newHunger) {
        return new Vitals(health, morale, Math.clamp(newHunger, 0, 100));
    }
}
