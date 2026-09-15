package com.syang.placitum.population;

import com.syang.placitum.data.LifeStage;
import com.syang.placitum.data.Resident;
import com.syang.placitum.sim.SimParams;
import com.syang.placitum.store.SettlementMut;

/**
 * Who can have children, and how likely it is this step.
 *
 * <p>Logistic, not exponential. Exponential growth ends with five hundred villagers and a dead
 * server; logistic converges on the carrying capacity and stays there, which is also how a real
 * settlement behaves.
 *
 * <p>Births are always a formula, never vanilla breeding. Unlike combat there is nothing to
 * watch, so there is no L0/L2 equivalence problem to solve - and vanilla breeding is exactly
 * the mechanism that stalls silently and leaves the player with no idea why.
 */
public final class Demographics {

    private Demographics() {}

    /** Adults who are not elderly, not zombified, and in reasonable health. */
    public static int fertileCount(SettlementMut settlement, SimParams params) {
        int n = 0;
        for (Resident r : settlement.residents) {
            if (r.stage() == LifeStage.ADULT && r.counts()
                    && r.ageDays() < params.elderThresholdDays()
                    && r.vitals().health() > 0) {
                n++;
            }
        }
        return n;
    }

    /** Pairs, because it takes two. Odd numbers round down, as they must. */
    public static int fertilePairs(SettlementMut settlement, SimParams params) {
        return fertileCount(settlement, params) / 2;
    }

    /**
     * Chance of a birth this step.
     *
     * <p>Zero at or above capacity, and scaled by morale so a miserable village grows slowly
     * even with room to spare.
     */
    public static double birthChance(SettlementMut settlement, Capacity capacity, SimParams params) {
        int population = settlement.population();
        int limit = capacity.value();
        if (population >= limit || fertilePairs(settlement, params) == 0) {
            return 0.0;
        }
        double headroom = 1.0 - (double) population / Math.max(1, limit);
        return params.baseBirthRate() * headroom * moraleFactor(settlement);
    }

    /** Average morale, mapped to a multiplier between 0.5 and 1.5. */
    private static double moraleFactor(SettlementMut settlement) {
        if (settlement.residents.isEmpty()) {
            return 1.0;
        }
        int total = 0;
        int counted = 0;
        for (Resident r : settlement.residents) {
            if (r.counts()) {
                total += r.vitals().morale();
                counted++;
            }
        }
        if (counted == 0) {
            return 1.0;
        }
        double average = (double) total / counted;
        return 0.5 + average / 100.0;
    }
}
