package com.syang.placitum.sim.module;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.Assignment;
import com.syang.placitum.data.BuildJob;
import com.syang.placitum.data.BuildStage;
import com.syang.placitum.data.LifeStage;
import com.syang.placitum.data.Resident;
import com.syang.placitum.population.Capacity;
import com.syang.placitum.sim.SimModule;
import com.syang.placitum.sim.SimParams;
import com.syang.placitum.store.SettlementMut;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/**
 * Puts the idle to work. Order 35.
 *
 * <p>Without this nothing is ever a woodcutter or a builder. Those two jobs have no vanilla
 * profession behind them, so adoption can never produce one, and a settlement full of unemployed
 * villagers would wait for a wall whose timber nobody was cutting.
 *
 * <p>Only residents with no job at all. A villager that arrived a librarian stays a librarian:
 * its profession is a real thing in the world with trades attached, and reassigning it would
 * destroy something the player may have spent hours building up.
 */
public class LabourModule implements SimModule {

    @Override
    public void step(SettlementMut settlement, SimParams params, RandomSource rng) {
        Identifier wanted = mostNeeded(settlement.freezeView(), params);

        // One a step. A settlement that reassigned everyone at once would swing from all
        // woodcutters to all farmers and back as each shortage relieved the other.
        for (int i = 0; i < settlement.residents.size(); i++) {
            Resident r = settlement.residents.get(i);
            if (!employable(r)) {
                continue;
            }
            settlement.residents.set(i, r.withAssignment(r.assignment().withJob(wanted)));
            Placitum.LOGGER.debug("  step {}: {} becomes a {}", settlement.simStep(),
                    r.lineage().fullName(), wanted.getPath());
            return;
        }
    }

    /** Adults and elders with no job. Children are not put to work. */
    private static boolean employable(Resident r) {
        return r.counts()
                && r.assignment().job().equals(Assignment.NONE)
                && r.stage() != LifeStage.INFANT
                && r.stage() != LifeStage.CHILD;
    }

    /**
     * What the settlement is shortest of.
     *
     * <p>Food first, always: hunger kills and nothing else on this list does. Then whatever the
     * build queue is stuck on, because a queue that cannot move is a settlement that has stopped
     * growing - and the two ways it gets stuck want different people.
     */
    public static Identifier mostNeeded(com.syang.placitum.data.Settlement settlement,
            SimParams params) {
        Capacity capacity = Capacity.of(settlement, params);
        if (capacity.bottleneck() == Capacity.Bottleneck.FOOD) {
            return Assignment.FARMER;
        }
        for (BuildJob job : settlement.buildQueue()) {
            if (job.stage() == BuildStage.WAITING_MATERIALS) {
                return Assignment.WOODCUTTER;
            }
            if (job.stage() == BuildStage.EXECUTING) {
                return Assignment.BUILDER;
            }
        }
        return Assignment.FARMER;
    }

    /**
     * Yes. Assigning a job counts nobody twice.
     *
     * <p>The work a resident does is already skipped while it has a body - that is what the
     * embodied guard is for. Deciding what work it should do is bookkeeping, and leaving it to
     * happen only in unvisited settlements is how this milestone deadlocked twice already.
     */
    @Override
    public boolean runsWhileEmbodied() {
        return true;
    }

    @Override
    public int order() {
        return 35;
    }

    @Override
    public String name() {
        return "labour";
    }
}
