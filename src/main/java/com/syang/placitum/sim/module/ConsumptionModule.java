package com.syang.placitum.sim.module;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.Vitals;
import com.syang.placitum.sim.SimModule;
import com.syang.placitum.sim.SimParams;
import com.syang.placitum.store.SettlementMut;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;

/**
 * Everyone eats. Order 20.
 *
 * <p>Without real consumption the food stock is decoration and none of the growth chain has a
 * reason to exist. Famine itself (morale collapse, deaths, warnings) is M2; this module only
 * establishes that food is finite and that hunger moves when it runs out.
 */
public class ConsumptionModule implements SimModule {

    @Override
    public void step(SettlementMut settlement, SimParams params, RandomSource rng) {
        int perHead = params.consumptionPerHead();
        // Settlement-wide totals count everyone: existing is enough to need feeding.
        int mouths = settlement.population();
        if (mouths == 0 || perHead == 0) {
            return;
        }

        int needed = mouths * perHead;
        int eaten = settlement.takeStock(Items.WHEAT, needed);
        boolean fed = eaten >= needed;
        if (Placitum.LOGGER.isDebugEnabled()) {
            Placitum.LOGGER.debug("  step {}: {} mouth(s) needed {}, ate {}, {} wheat left",
                    settlement.simStep(), mouths, needed, eaten,
                    settlement.stockOf(Items.WHEAT));
        }

        for (int i = 0; i < settlement.residents.size(); i++) {
            Resident r = settlement.residents.get(i);
            if (r.materialized() || !r.counts()) {
                continue;
            }
            Vitals v = r.vitals();
            settlement.residents.set(i, r.withVitals(
                    v.withHunger(fed ? v.hunger() + 5 : v.hunger() - 5)));
        }
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public String name() {
        return "consumption";
    }
}
