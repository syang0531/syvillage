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
import com.syang.placitum.build.CottagePlan;
import com.syang.placitum.build.HousePlanner;
import com.syang.placitum.data.CellPos;
import com.syang.placitum.data.CellState;
import com.syang.placitum.data.Plot;
import com.syang.placitum.data.PlotKind;
import java.util.UUID;
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
            // A job finished virtually has laid no blocks anywhere. Dropping it here left the
            // wall existing only as a record: /placitum info reported seven gates and the
            // ground had none, because promote replays ops[0, progress) by walking the build
            // queue and the queue was empty.
            //
            // So a completed job survives until the settlement has bodies again, which is the
            // one moment its blocks can actually be put down. Five fields, not the 1618 ops
            // they expand to - docs/data-model.md is firm that op lists are never stored, and
            // this is why the recipe has to be enough on its own.
            case COMPLETE -> settlement.anyMaterialized() ? null : job;
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
        // Finishing is checked before anything else, and at both levels of detail. BuildTick
        // raises progress while a player watches but owns no part of the stage machine, so if
        // completion were skipped here a wall laid in front of somebody would stand finished
        // and unrecorded - and an unrecorded wall gets ordered again.
        if (job.progress() >= total) {
            complete(settlement, job);
            settlement.record(EntryType.BUILD, settlement.identity.name(),
                    "finished a " + job.recipe().template().getPath() + " of " + total
                            + " blocks");
            Placitum.LOGGER.info("  step {}: '{}' finished a {} ({} blocks)",
                    settlement.simStep(), settlement.identity.name(),
                    job.recipe().template().getPath(), total);
            return job.withProgress(total).withStage(BuildStage.COMPLETE);
        }
        if (settlement.anyMaterialized()) {
            // BuildTick has this one - it is putting the blocks where they can be seen. Counting
            // here as well would build the wall at twice the rate it appears.
            return job;
        }
        int laid = Math.max(1, builders) * params.opsPerBuilderStep();
        return job.withProgress(Math.min(total, job.progress() + laid));
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
        if (job.recipe().template().equals(HousePlanner.COTTAGE)) {
            registerHouse(settlement, job);
            return;
        }
        List<BlockPos> ring = BuildPlanner.ringOf(job.recipe());
        settlement.defense = settlement.defense.withWall(
                new WallState(WallTier.PALISADE, ring, BuildPlanner.gatesOf(job.recipe()),
                        true));
    }

    /**
     * Puts a finished cottage on the books.
     *
     * <p>The plot is what carrying capacity counts - {@code bedCount()} reads plots, not the
     * world - so a house that is built and not registered raises capacity by nothing and the
     * settlement immediately orders another one. The cell goes to BUILT for the same reason the
     * plot exists: the next site search has to know this ground is taken.
     */
    private void registerHouse(SettlementMut settlement, BuildJob job) {
        CellPos cell = settlement.grid.cellAt(job.recipe().anchor());
        UUID plotId = UUID.nameUUIDFromBytes(("plot:" + job.id()).getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
        settlement.plots.put(plotId, new Plot(plotId, cell, 1, 1, job.recipe().rotation(),
                job.recipe().template(), PlotKind.HOUSE, CottagePlan.bedCount(), List.of()));
        settlement.grid = settlement.grid.with(cell, CellState.BUILT);
        Placitum.LOGGER.info("'{}' finished a house on cell {}: {} more bed(s)",
                settlement.identity.name(), cell.toKey(), CottagePlan.bedCount());
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
     * Yes - but only for the paperwork.
     *
     * <p>Drawing materials, moving from QUEUED to EXECUTING, noticing a job is finished: none of
     * that counts a resident twice, and all of it stalls for ever if it only happens in a
     * village nobody is standing in. That was the second deadlock of exactly this shape - the
     * stage machine advanced at one level of detail and the work happened at the other, so a
     * wall with materials in the stores sat at QUEUED while its builders stood next to it.
     *
     * <p>Laying blocks is the part that is genuinely LOD-specific, and {@link #execute} declines
     * it while bodies exist. That belongs to BuildTick, which places them where they can be
     * seen - the two share one counter, so both advancing it would double the wall.
     */
    @Override
    public boolean runsWhileEmbodied() {
        return true;
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
