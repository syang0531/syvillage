package com.syang.placitum.sim.module;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.Assignment;
import com.syang.placitum.data.LifeStage;
import com.syang.placitum.data.Resident;
import com.syang.placitum.sim.SimModule;
import com.syang.placitum.sim.SimParams;
import com.syang.placitum.store.SettlementMut;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;

/**
 * Work that turns into stock. Order 10 - first, so consumption sees this step's harvest.
 *
 * <p>Farmers grow food and woodcutters cut timber. The second half exists because
 * docs/construction.md promises it and nothing delivered it: the first real wall cost 1761 logs
 * and the mod produced no logs at all, so a settlement could want a wall for ever and never be
 * able to pay for one. A village that can only build what a player carries in is not a village
 * that grows by itself.
 */
public class ProductionModule implements SimModule {

    @Override
    public void step(SettlementMut settlement, SimParams params, RandomSource rng) {
        int wheat = 0;
        int logs = 0;
        int farmers = 0;
        int woodcutters = 0;

        for (Resident r : settlement.residents) {
            // Materialized residents are acting as real entities and would be counted twice.
            if (r.materialized() || !r.counts()) {
                continue;
            }
            if (r.stage() == LifeStage.INFANT || r.stage() == LifeStage.CHILD) {
                continue;
            }
            if (r.assignment().job().equals(Assignment.FARMER)) {
                farmers++;
                wheat += output(r, params.yieldRate());
            } else if (r.assignment().job().equals(Assignment.WOODCUTTER)) {
                woodcutters++;
                logs += output(r, params.timberRate());
            }
        }
        if (wheat > 0) {
            settlement.addStock(Items.WHEAT, wheat);
        }
        if (logs > 0) {
            settlement.addStock(Items.OAK_LOG, logs);
        }
        if (Placitum.LOGGER.isDebugEnabled()) {
            Placitum.LOGGER.debug("  step {}: {} farmer(s) made {} wheat, {} woodcutter(s) made"
                            + " {} log(s), of {} resident(s)", settlement.simStep(), farmers,
                    wheat, woodcutters, logs, settlement.residents.size());
        }
    }

    /** An elder does half a day's work, rounded up so it is never nothing. */
    private static int output(Resident r, int rate) {
        return r.stage() == LifeStage.ELDER ? Math.max(1, rate / 2) : rate;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public String name() {
        return "production";
    }
}
