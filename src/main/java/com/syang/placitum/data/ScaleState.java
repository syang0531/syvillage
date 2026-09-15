package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Size tier plus the hysteresis counter that guards it.
 *
 * <p>They travel together: the counter is meaningless without the tier, and promoting on the
 * tier alone is what makes a settlement on a population boundary tear its own walls down and
 * rebuild them forever.
 *
 * <p>Grouping them also buys back a slot against the 16-field codec limit, which Settlement
 * was sitting on.
 */
public record ScaleState(ScaleTier tier, int holdSteps) {

    public static final Codec<ScaleState> CODEC = RecordCodecBuilder.create(i -> i.group(
            ScaleTier.CODEC.fieldOf("tier").forGetter(ScaleState::tier),
            Codec.INT.optionalFieldOf("hold_steps", 0).forGetter(ScaleState::holdSteps)
    ).apply(i, ScaleState::new));

    public static final ScaleState OUTPOST = new ScaleState(ScaleTier.OUTPOST, 0);

    public ScaleState holding(int steps) {
        return new ScaleState(tier, steps);
    }

    public ScaleState movedTo(ScaleTier newTier) {
        return new ScaleState(newTier, 0);
    }
}
