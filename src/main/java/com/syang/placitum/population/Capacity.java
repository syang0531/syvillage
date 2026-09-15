package com.syang.placitum.population;

import com.syang.placitum.data.Resident;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.defense.DefenseRating;
import com.syang.placitum.sim.SimParams;

/**
 * How many people a settlement can support, and which of the three reasons is the binding one.
 *
 * <p>The minimum is the whole design. Spare beds do not help a starving village and a full
 * granary does not help one that keeps getting raided, so growth stops at whichever is
 * scarcest - and the player gets a question with an answer: which one is it.
 *
 * <p>Reporting the bottleneck is not a nicety bolted on afterwards. "My village will not grow
 * and I cannot tell why" is the complaint this milestone exists to answer.
 */
public record Capacity(int beds, int food, int safety) {

    /** Which of the three is holding growth back. */
    public enum Bottleneck { BEDS, FOOD, SAFETY }

    public static Capacity of(Settlement settlement, SimParams params) {
        return new Capacity(
                settlement.bedCount(),
                foodCapacity(settlement, params),
                safetyCapacity(settlement, params));
    }

    public int value() {
        return Math.min(beds, Math.min(food, safety));
    }

    public Bottleneck bottleneck() {
        int limit = value();
        if (beds == limit) {
            return Bottleneck.BEDS;
        }
        return food == limit ? Bottleneck.FOOD : Bottleneck.SAFETY;
    }

    /**
     * How many mouths the fields can keep fed.
     *
     * <p>Production per step divided by what one resident eats. A settlement with no farmers
     * supports nobody, which is harsh and correct: it is exactly the state a player needs to
     * notice before the food runs out rather than after.
     */
    private static int foodCapacity(Settlement settlement, SimParams params) {
        if (params.consumptionPerHead() <= 0) {
            return Integer.MAX_VALUE;
        }
        int production = 0;
        for (Resident r : settlement.residents()) {
            if (r.counts() && r.assignment().job().equals(
                    com.syang.placitum.data.Assignment.FARMER)) {
                production += params.yieldRate();
            }
        }
        return production / params.consumptionPerHead();
    }

    /**
     * How many people will settle somewhere this dangerous.
     *
     * <p>Falls with recent combat deaths, counted from the ring buffer rather than the
     * chronicle - the chronicle is trimmed, so counting deaths there would make a frequently
     * raided settlement look progressively safer.
     *
     * <p>This is the line that ties M1 to M2: walls, militia and watch points raise it, so a
     * village that keeps being overrun stops growing, and defending it is how you fix that.
     */
    private static int safetyCapacity(Settlement settlement, SimParams params) {
        int base = params.baseSafety() + DefenseRating.of(settlement) / params.safetyRatingDivisor();
        int recentDeaths = settlement.defense().casualtiesInWindow();
        return Math.max(params.minSafety(), base - recentDeaths * params.safetyDeathPenalty());
    }
}
