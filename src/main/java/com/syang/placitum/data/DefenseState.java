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
/**
 * {@code famineSteps} counts how long the stores have been empty, and {@code foodWarned}
 * whether the low-food warning is standing. Both are saved, and the second one had to be
 * argued into it.
 *
 * <p>It was briefly a transient field on the mutable mirror, on the reasoning that losing it
 * across a save costs at most one extra warning. That reasoning was wrong, and the catch-up
 * equivalence test said so immediately: the mirror is rebuilt on every catch-up, so one run
 * of 1000 ticks warned once and ten runs of 100 warned ten times.
 *
 * <p><b>Anything that changes what a step produces is state.</b> There is no such thing as a
 * transient flag inside a deterministic simulation.
 */
public record DefenseState(
        AlertState alert,
        long alertSince,
        WallState wall,
        int lightingScore,
        List<Integer> recentCasualties,
        int famineSteps,
        boolean foodWarned) {

    public static final Codec<DefenseState> CODEC = RecordCodecBuilder.create(i -> i.group(
            AlertState.CODEC.fieldOf("alert").forGetter(DefenseState::alert),
            Codec.LONG.fieldOf("alert_since").forGetter(DefenseState::alertSince),
            WallState.CODEC.fieldOf("wall").forGetter(DefenseState::wall),
            Codec.INT.fieldOf("lighting_score").forGetter(DefenseState::lightingScore),
            PlacitumCodecs.INT_LIST.fieldOf("recent_casualties").forGetter(DefenseState::recentCasualties),
            Codec.INT.optionalFieldOf("famine_steps", 0).forGetter(DefenseState::famineSteps),
            Codec.BOOL.optionalFieldOf("food_warned", false).forGetter(DefenseState::foodWarned)
    ).apply(i, DefenseState::new));

    public static DefenseState initial(int windowDays) {
        return new DefenseState(AlertState.PEACE, 0L, WallState.NONE, 0,
                PlacitumCodecs.zeros(windowDays), 0, false);
    }

    public int casualtiesInWindow() {
        int sum = 0;
        for (int v : recentCasualties) {
            sum += v;
        }
        return sum;
    }

    public DefenseState withWall(WallState newWall) {
        return new DefenseState(alert, alertSince, newWall, lightingScore, recentCasualties,
                famineSteps, foodWarned);
    }

    public DefenseState withCasualty(int gameDay, int count) {
        if (recentCasualties.isEmpty()) {
            return this;
        }
        List<Integer> next = new ArrayList<>(recentCasualties);
        int slot = Math.floorMod(gameDay, next.size());
        next.set(slot, next.get(slot) + count);
        return new DefenseState(alert, alertSince, wall, lightingScore, List.copyOf(next),
                famineSteps, foodWarned);
    }

    /** Clears the slot a new game day is about to occupy, so the window really is a window. */
    public DefenseState rolledTo(int gameDay) {
        if (recentCasualties.isEmpty()) {
            return this;
        }
        List<Integer> next = new ArrayList<>(recentCasualties);
        next.set(Math.floorMod(gameDay, next.size()), 0);
        return new DefenseState(alert, alertSince, wall, lightingScore, List.copyOf(next),
                famineSteps, foodWarned);
    }
}
