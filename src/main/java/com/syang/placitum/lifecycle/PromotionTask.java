package com.syang.placitum.lifecycle;

import com.syang.placitum.Placitum;
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

    /**
     * How long to keep retrying residents whose chunks are not entity-ticking yet.
     *
     * <p>Arriving at a village outruns its chunks: the first spawning passes can place nobody
     * at all. Five seconds is far longer than that takes and still bounded, so a resident
     * stranded somewhere that never ticks cannot spin forever.
     */
    private static final int MAX_DEFERRED_PASSES = 100;

    private final java.util.UUID settlementId;
    private Phase phase = Phase.CATCHING_UP;
    private int spawnCursor;
    private int deferredPasses;

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
                    long steps = next.simStep() - settlement.simStep();
                    Placitum.LOGGER.info("  caught up {} step(s), {} pending op(s) to replay",
                            steps, next.pendingOps().size());
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
        return settlement.withPendingOps(List.copyOf(pending));
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
        int deferred = 0;

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
            } else {
                deferred++;   // its chunk is not entity-ticking yet
            }
        }

        if (materialized >= cap) {
            finish(settlement, materialized, true);
        } else if (spawnCursor >= ordered.size()) {
            // Finishing here while residents were only deferred is what made promotion churn:
            // the task declared success having placed nobody, the manager saw an empty village
            // with a player in it and started another task, and that repeated - each round
            // re-running catch-up - until the chunks finally caught up.
            if (deferred > 0 && ++deferredPasses < MAX_DEFERRED_PASSES) {
                spawnCursor = 0;   // same task, go round again next tick
            } else {
                finish(settlement, materialized, false);
            }
        }
        return SettlementManager.withResidents(settlement, updated);
    }

    private void finish(Settlement settlement, int materialized, boolean hitCap) {
        phase = Phase.DONE;
        String note = hitCap ? " (hit maxMaterializedResidents)"
                : deferredPasses >= MAX_DEFERRED_PASSES ? " (gave up waiting for chunks)" : "";
        Placitum.LOGGER.info("PROMOTE done: '{}' - {} of {} resident(s) materialized{}",
                settlement.name(), materialized, settlement.residentCount(), note);
    }

    private static void replaceById(List<Resident> list, Resident replacement) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(replacement.id())) {
                list.set(i, replacement);
                return;
            }
        }
    }


}
