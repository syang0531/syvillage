package com.syang.placitum.lifecycle;

import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.sim.SimParams;
import com.syang.placitum.sim.Simulation;
import com.syang.placitum.store.SettlementManager;
import com.syang.placitum.store.SettlementMut;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Promotion, spread across ticks.
 *
 * <p>A three-day catch-up is 360 steps and the pending op queue can hold thousands of block
 * changes. Doing that in one tick blows the budget by orders of magnitude, so it runs as a
 * state machine that stops on step boundaries.
 *
 * <p>The phase order is not negotiable: settling the simulation before spawning is what stops
 * a resident who died three days ago from appearing and then vanishing.
 */
public final class PromotionTask {

    public enum Phase { CATCHING_UP, REPLAYING, SPAWNING, DONE }

    private final java.util.UUID settlementId;
    private Phase phase = Phase.CATCHING_UP;
    private int spawnCursor;

    public PromotionTask(java.util.UUID settlementId) {
        this.settlementId = settlementId;
    }

    public Phase phase() {
        return phase;
    }

    public java.util.UUID settlementId() {
        return settlementId;
    }

    public boolean done() {
        return phase == Phase.DONE;
    }

    /** Advances within the given deadline. Returns the settlement as it now stands. */
    public Settlement advance(ServerLevel level, SettlementManager manager, Settlement settlement,
            long now, long deadlineNanos) {
        switch (phase) {
            case CATCHING_UP -> {
                SettlementMut mut = SettlementMut.of(settlement);
                boolean finished = Simulation.catchUp(level.getServer().overworld().getSeed(),
                        mut, SimParams.fromConfig(), now, deadlineNanos);
                Settlement next = mut.freeze();
                if (finished) {
                    phase = Phase.REPLAYING;
                }
                return next;
            }
            case REPLAYING -> {
                return replay(level, settlement);
            }
            case SPAWNING -> {
                return spawn(level, manager, settlement);
            }
            case DONE -> {
                return settlement;
            }
        }
        return settlement;
    }

    /**
     * Applies queued world changes a few per tick.
     *
     * <p>The player sees builders apparently mid-job on arrival. That is the intended effect,
     * not a symptom of lag.
     */
    private Settlement replay(ServerLevel level, Settlement settlement) {
        int budget = PlacitumConfig.REPLAY_OPS_PER_TICK.get();
        List<BuildOp> pending = new ArrayList<>(settlement.pendingOps());
        int applied = 0;
        while (applied < budget && !pending.isEmpty()) {
            BuildOp op = pending.removeFirst();
            if (level.isLoaded(op.pos())) {
                level.setBlock(op.pos(), op.state(), 3);
            }
            applied++;
        }
        if (pending.isEmpty()) {
            phase = Phase.SPAWNING;
        }
        return withPendingOps(settlement, List.copyOf(pending));
    }

    /**
     * Spawns residents nearest the player first, up to the materialized cap.
     *
     * <p>Residents over the cap stay VIRTUAL and keep being simulated - they do not stop
     * existing, they just have no body. The cap is reported rather than applied silently,
     * because an invisible villager looks exactly like a bug.
     */
    private Settlement spawn(ServerLevel level, SettlementManager manager, Settlement settlement) {
        int cap = PlacitumConfig.MAX_MATERIALIZED_RESIDENTS.get();
        BlockPos center = settlement.center();

        List<Resident> ordered = new ArrayList<>(settlement.residents());
        ordered.sort(Comparator.comparingDouble(r -> r.coarsePos().distSqr(center)));

        List<Resident> updated = new ArrayList<>(settlement.residents());
        int materialized = settlement.materializedCount();
        int examined = 0;

        while (spawnCursor < ordered.size() && examined < 8) {
            Resident target = ordered.get(spawnCursor++);
            examined++;
            if (target.materialized() || target.stage() == com.syang.placitum.data.LifeStage.INFANT) {
                continue;
            }
            if (materialized >= cap) {
                break;
            }
            Resident promoted = Lifecycle.promote(level, manager, target);
            if (promoted.materialized()) {
                materialized++;
                replaceById(updated, promoted);
            }
        }

        if (spawnCursor >= ordered.size() || materialized >= cap) {
            phase = Phase.DONE;
        }
        return SettlementManager.withResidents(settlement, updated);
    }

    private static void replaceById(List<Resident> list, Resident replacement) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(replacement.id())) {
                list.set(i, replacement);
                return;
            }
        }
    }

    private static Settlement withPendingOps(Settlement s, List<BuildOp> ops) {
        return new Settlement(s.identity(), s.scale(), s.scaleHoldSteps(), s.residents(), s.plots(),
                s.grid(), s.stock(), s.buildQueue(), ops, s.defense(), s.chronicle(), s.clock(),
                s.ruler(), s.parentId(), s.forceLoadCore());
    }
}
