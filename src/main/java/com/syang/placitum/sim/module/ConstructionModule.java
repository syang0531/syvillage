package com.syang.placitum.sim.module;

import com.syang.placitum.Placitum;
import com.syang.placitum.build.BuildPlanner;
import com.syang.placitum.data.Assignment;
import com.syang.placitum.data.BuildJob;
import com.syang.placitum.data.BuildStage;
import com.syang.placitum.data.EntryType;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.WallState;
import com.syang.placitum.data.WallTier;
import net.minecraft.core.BlockPos;
import com.syang.placitum.sim.SimModule;
import com.syang.placitum.sim.SimParams;
import com.syang.placitum.store.SettlementMut;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;

/**
 * Building, while nobody is watching. Order 50.
 *
 * <p>Last, so a step's deaths and births are already on the books: a settlement that lost its
 * builders this step should not lay their blocks, and one that just grew should count the new
 * arrival before deciding it needs another house.
 *
 * <p>Nothing here touches the world. A single integer - {@code progress} - is the entire bridge
 * between this and the builder who does: it counts up the same op list either way, and promote
 * replays ops[0, progress). That is principle 2 in its entirety.
 */
public class ConstructionModule implements SimModule {

    @Override
    public void step(SettlementMut settlement, SimParams params, RandomSource rng) {
        if (settlement.buildQueue.isEmpty()) {
            return;
        }
        int builders = countBuilders(settlement);
        List<BuildJob> next = new ArrayList<>(settlement.buildQueue.size());

        for (BuildJob job : settlement.buildQueue) {
            BuildJob advanced = advance(settlement, job, builders, params);
            if (advanced != null) {
                next.add(advanced);
            }
        }
        settlement.buildQueue.clear();
        settlement.buildQueue.addAll(next);
    }

    /** Null means the job is finished and leaves the queue. */
    private BuildJob advance(SettlementMut settlement, BuildJob job, int builders,
            SimParams params) {
        return switch (job.stage()) {
            case PLANNED, RESERVED ->
                    // Both wait on the world. The recipe cannot be frozen without reading the
                    // ground, and reading the ground needs loaded chunks, so a virtual
                    // settlement holds here until somebody walks into it. Sitting still is the
                    // correct behaviour, not a stall.
                    job;
            case QUEUED -> reserveMaterials(settlement, job);
            case WAITING_MATERIALS -> reserveMaterials(settlement, job);
            case EXECUTING -> execute(settlement, job, builders, params);
            case COMPLETE -> null;
        };
    }

    /**
     * Takes the cost out of the stores, or waits.
     *
     * <p>Waiting is a state with a name, not a failure: docs/construction.md is firm that the
     * shortfall must reach the player, because "the smithy is waiting for twelve iron" is a
     * reason to go and do something, which is the exact opposite of the helplessness this mod
     * exists to answer.
     */
    private BuildJob reserveMaterials(SettlementMut settlement, BuildJob job) {
        for (Map.Entry<Item, Integer> entry : job.cost().entrySet()) {
            if (settlement.stockOf(entry.getKey()) < entry.getValue()) {
                if (job.stage() != BuildStage.WAITING_MATERIALS) {
                    settlement.record(EntryType.BUILD, settlement.identity.name(),
                            "waiting to build - short of "
                                    + (entry.getValue() - settlement.stockOf(entry.getKey()))
                                    + " " + entry.getKey().getDescriptionId());
                }
                return job.withStage(BuildStage.WAITING_MATERIALS);
            }
        }
        for (Map.Entry<Item, Integer> entry : job.cost().entrySet()) {
            settlement.takeStock(entry.getKey(), entry.getValue());
        }
        return job.withStage(BuildStage.EXECUTING);
    }

    /**
     * Lays blocks, virtually.
     *
     * <p>Capped at the op count. Without the cap progress runs past the end of the list and
     * replay walks off it - docs/construction.md names this one explicitly, which is usually a
     * sign somebody has already done it.
     */
    private BuildJob execute(SettlementMut settlement, BuildJob job, int builders,
            SimParams params) {
        int total = BuildPlanner.expand(job.recipe()).size();
        if (total == 0) {
            Placitum.LOGGER.debug("  step {}: build {} expands to nothing, dropping it",
                    settlement.simStep(), job.id());
            return null;
        }
        int laid = Math.max(1, builders) * params.opsPerBuilderStep();
        int progress = Math.min(total, job.progress() + laid);

        if (progress < total) {
            return job.withProgress(progress);
        }
        complete(settlement, job);
        settlement.record(EntryType.BUILD, settlement.identity.name(),
                "finished a " + job.recipe().template().getPath() + " of " + total + " blocks");
        Placitum.LOGGER.debug("  step {}: build {} complete ({} blocks)", settlement.simStep(),
                job.id(), total);
        return job.withProgress(total).withStage(BuildStage.COMPLETE);
    }

    /**
     * Writes the finished thing into the settlement.
     *
     * <p>The COMPLETE stage of docs/construction.md, and leaving it out cost 1761 logs a lap: a
     * wall that finished without setting WallState left the tier at NONE, NeedsModule saw a
     * settlement with no wall, and ordered another one. For ever, and the stores paid for every
     * lap of it.
     *
     * <p>A build that leaves no trace is indistinguishable from a build that never happened -
     * and the thing that decides whether to build is looking at exactly that trace.
     */
    private void complete(SettlementMut settlement, BuildJob job) {
        List<BlockPos> ring = BuildPlanner.ringOf(job.recipe());
        settlement.defense = settlement.defense.withWall(
                new WallState(WallTier.PALISADE, ring, List.of(), true));
        // Gates are left empty on purpose. Cutting them means knowing where the roads cross the
        // ring and registering each one for pathfinding, and a gate that is not registered is a
        // farmer standing in front of a wall for ever. That is its own piece of work.
    }

    /**
     * Who is actually building.
     *
     * <p>A settlement with no builder still creeps forward at one builder's rate. That is
     * deliberate: a village where nobody happens to hold the job would otherwise queue a wall
     * and never lay a single log, which looks exactly like the thing being broken.
     */
    private int countBuilders(SettlementMut settlement) {
        int n = 0;
        for (Resident r : settlement.residents) {
            if (r.counts() && r.assignment().job().equals(Assignment.BUILDER)) {
                n++;
            }
        }
        return n;
    }

    /**
     * No, deliberately, and this one costs something.
     *
     * <p>Laying blocks while the residents have bodies is the builder's job, and the builder is
     * stage 5. Until then a settlement with a player standing in it will hold at whatever
     * progress it had - which looks like a stall and is not one. {@code /placitum build} says
     * so rather than leaving it to be guessed.
     *
     * <p>Returning true here instead would double-count the moment the builder exists: both the
     * formula and the entity would advance the same counter.
     */
    @Override
    public boolean runsWhileEmbodied() {
        return false;
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public String name() {
        return "construction";
    }
}
