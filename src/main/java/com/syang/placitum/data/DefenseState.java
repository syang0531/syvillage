package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything docs/defense.md owns.
 *
 * <p>{@code recentCasualties} is a per-game-day ring buffer rather than a query over the
 * chronicle: the chronicle is trimmed at 200 entries, so counting deaths there would make a
 * frequently raided settlement look safer than a quiet one.
 */
public record DefenseState(
        AlertState alert,
        long alertSince,
        WallState wall,
        int lightingScore,
        List<Integer> recentCasualties) {

    public static final Codec<DefenseState> CODEC = RecordCodecBuilder.create(i -> i.group(
            AlertState.CODEC.fieldOf("alert").forGetter(DefenseState::alert),
            Codec.LONG.fieldOf("alert_since").forGetter(DefenseState::alertSince),
            WallState.CODEC.fieldOf("wall").forGetter(DefenseState::wall),
            Codec.INT.fieldOf("lighting_score").forGetter(DefenseState::lightingScore),
            PlacitumCodecs.INT_LIST.fieldOf("recent_casualties").forGetter(DefenseState::recentCasualties)
    ).apply(i, DefenseState::new));

    public static DefenseState initial(int windowDays) {
        return new DefenseState(AlertState.PEACE, 0L, WallState.NONE, 0, PlacitumCodecs.zeros(windowDays));
    }

    public int casualtiesInWindow() {
        int sum = 0;
        for (int v : recentCasualties) {
            sum += v;
        }
        return sum;
    }

    public DefenseState withCasualty(int gameDay, int count) {
        if (recentCasualties.isEmpty()) {
            return this;
        }
        List<Integer> next = new ArrayList<>(recentCasualties);
        int slot = Math.floorMod(gameDay, next.size());
        next.set(slot, next.get(slot) + count);
        return new DefenseState(alert, alertSince, wall, lightingScore, List.copyOf(next));
    }

    /** Clears the slot a new game day is about to occupy, so the window really is a window. */
    public DefenseState rolledTo(int gameDay) {
        if (recentCasualties.isEmpty()) {
            return this;
        }
        List<Integer> next = new ArrayList<>(recentCasualties);
        next.set(Math.floorMod(gameDay, next.size()), 0);
        return new DefenseState(alert, alertSince, wall, lightingScore, List.copyOf(next));
    }
}
