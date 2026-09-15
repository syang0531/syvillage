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

    private static final Codec<ScaleState> FULL = RecordCodecBuilder.create(i -> i.group(
            ScaleTier.CODEC.fieldOf("tier").forGetter(ScaleState::tier),
            Codec.INT.optionalFieldOf("hold_steps", 0).forGetter(ScaleState::holdSteps)
    ).apply(i, ScaleState::new));

    /** How M0 wrote it: the bare tier name, before the counter existed. */
    private static final Codec<ScaleState> LEGACY_TIER_ONLY =
            ScaleTier.CODEC.xmap(tier -> new ScaleState(tier, 0), ScaleState::tier);

    /**
     * Reads both shapes and writes the new one.
     *
     * <p>Changing a stored field's type breaks old saves exactly as surely as renaming it -
     * CLAUDE.md bans the rename and this is the same sin in different clothes. It was committed
     * anyway, and caught only because SettlementData refuses partial decodes: a whole settlement
     * declined to load with "Not a map: outpost" rather than quietly coming back empty.
     *
     * <p>The rule the incident actually teaches: a stored field may gain a shape, never swap
     * one. {@code withAlternative} is how it gains one.
     */
    public static final Codec<ScaleState> CODEC = Codec.withAlternative(FULL, LEGACY_TIER_ONLY);

    public static final ScaleState OUTPOST = new ScaleState(ScaleTier.OUTPOST, 0);

    public ScaleState holding(int steps) {
        return new ScaleState(tier, steps);
    }

    public ScaleState movedTo(ScaleTier newTier) {
        return new ScaleState(newTier, 0);
    }
}
