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

/** Farmers put food into the stock. Order 10 - first, so consumption sees it. */
public class ProductionModule implements SimModule {

    @Override
    public void step(SettlementMut settlement, SimParams params, RandomSource rng) {
        int yield = params.yieldRate();
        int produced = 0;
        int farmers = 0;
        for (Resident r : settlement.residents) {
            // Materialized residents are acting as real entities and would be counted twice.
            if (r.materialized() || !r.counts()) {
                continue;
            }
            if (!r.assignment().job().equals(Assignment.FARMER)) {
                continue;
            }
            if (r.stage() == LifeStage.INFANT || r.stage() == LifeStage.CHILD) {
                continue;
            }
            farmers++;
            produced += r.stage() == LifeStage.ELDER ? Math.max(1, yield / 2) : yield;
        }
        if (produced > 0) {
            settlement.addStock(Items.WHEAT, produced);
        }
        if (Placitum.LOGGER.isDebugEnabled()) {
            Placitum.LOGGER.debug("  step {}: {} farmer(s) of {} resident(s) produced {} wheat",
                    settlement.simStep(), farmers, settlement.residents.size(), produced);
        }
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
