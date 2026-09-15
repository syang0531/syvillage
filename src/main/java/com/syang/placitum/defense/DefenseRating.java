package com.syang.placitum.defense;

import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.AlertState;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.Settlement;

/**
 * One number both halves of the game agree on.
 *
 * <p>If the real fight and the formula gave systematically different answers, players would
 * learn the difference and play around it - "my village does better when I am not there", or
 * worse, the reverse. So L0 and L2 share this scalar: the formula divides threat by it, and a
 * real raid's size is derived from the same threat value.
 *
 * <p>The coefficients are not derived from anything. docs/testing.md section 4 measures real
 * fights and tunes them to match; until then they are a starting guess and are config, not
 * constants.
 */
public final class DefenseRating {

    private DefenseRating() {}

    public static int of(Settlement settlement) {
        int militia = eligibleCount(settlement);
        int armable = Math.min(militia, Armoury.armableCount(settlement));
        int tier = Armoury.bestAvailableTier(settlement);

        return armable * tier * PlacitumConfig.MILITIA_WEIGHT.get()
                + settlement.defense().wall().tier().grade() * PlacitumConfig.WALL_WEIGHT.get()
                + settlement.anchors().watchPoints().size() * PlacitumConfig.WATCHTOWER_WEIGHT.get()
                + (settlement.alert() == AlertState.COMBAT ? PlacitumConfig.COMBAT_READY_BONUS.get() : 0);
    }

    /**
     * Who could be called up.
     *
     * <p>Capped by {@code militiaRatioCap}: arming everyone stops production dead, so the
     * player gets a trade-off rather than a free army.
     */
    public static int eligibleCount(Settlement settlement) {
        int eligible = 0;
        for (Resident r : settlement.residents()) {
            if (r.militiaEligible() && r.counts() && r.vitals().health() > 0) {
                eligible++;
            }
        }
        int cap = (int) Math.floor(settlement.population() * PlacitumConfig.MILITIA_RATIO_CAP.get());
        return Math.min(eligible, Math.max(1, cap));
    }
}
