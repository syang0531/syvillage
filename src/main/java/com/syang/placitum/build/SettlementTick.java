package com.syang.placitum.build;

import com.syang.placitum.Placitum;
import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.BuildJob;
import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.BuildStage;
import com.syang.placitum.data.CellPos;
import com.syang.placitum.data.CellState;
import com.syang.placitum.data.EntryType;
import com.syang.placitum.data.Plot;
import com.syang.placitum.data.PlotKind;
import com.syang.placitum.data.Settlement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;

/**
 * Everything a settlement does, while somebody is there to see it.
 *
 * <p>There is no simulation behind this and no second path for when nobody is watching. A
 * village that is never visited never grows, which is the deal: the fun is watching it happen,
 * and in exchange there is no second, invisible code path for it to disagree with.
 *
 * <p>A phase at a time, and within a phase: street, then light, then buildings. Phase 0 is the
 * four city blocks that meet at the bell - sixteen lots - and it is finished before phase 1 is
 * looked at at all. Phase 1 is the twelve blocks around those, phase 2 the twenty around those.
 * The last phase of all is street and lamps with no buildings, left open at its outer edge, so
 * the town has a road going somewhere rather than a ring road round a compound.
 *
 * <p>The phases are re-checked from 0 every pass, so a lot the player levels near the bell while
 * the town is out at phase 3 gets built on next. Nothing is ever written off.
 *
 * <p>Nothing is built where nobody can walk: see {@link Reach}. Buildings additionally need flat
 * ground and wait for it rather than cutting the hill down to size.
 *
 * <p>When nothing is missing, nothing happens. That sounds obvious and was not: an earlier
 * version finished its roads, lit the place, and then laid the roads again.
 */
public final class SettlementTick {

    private SettlementTick() {}

    public static Settlement run(ServerLevel level, Settlement settlement) {
        Settlement out = resurvey(level, settlement);
        out = start(level, out);
        return lay(level, out);
    }

    /**
     * Re-reads the ground now and then, so building notices what a player has changed - and says
     * why it is not building, if it is not.
     *
     * <p>An idle settlement used to log nothing at all, which reads as a bug whether or not it
     * is one. The most expensive thing this project has learnt is that "nothing is happening"
     * always has more than one explanation; the tool has to say which.
     */
    private static Settlement resurvey(ServerLevel level, Settlement settlement) {
        if (level.getGameTime() % PlacitumConfig.SURVEY_INTERVAL_TICKS.get() != 0) {
            return settlement;
        }
        Settlement out = forgetCleared(level, settlement)
                .withCraft(Trades.earned(level, settlement))
                .withWall(Trades.hasLord(level, settlement));
        if (out.craft() != settlement.craft()) {
            Placitum.LOGGER.info("'{}' now builds in {}", out.name(),
                    out.craft().getSerializedName());
            out = out.record(EntryType.BUILD, out.name(),
                    "now builds in " + out.craft().getSerializedName(), level.getGameTime());
        }
        if (out.walled() && !settlement.walled()) {
            Placitum.LOGGER.info("'{}' has a lord, and may wall itself", out.name());
            out = out.record(EntryType.BUILD, out.name(), "began its wall", level.getGameTime());
        }
        settlement = out;

        if (settlement.buildQueue().isEmpty()) {
            Reach reach = Reach.from(level, settlement, TownPlan.outerPhase(settlement));
            Placitum.LOGGER.info("'{}' is building nothing. {} column(s) of street reach the"
                            + " bell, {} of ground reach a street. Lots out to phase {}: {}",
                    settlement.name(), reach.streetSize(), reach.size(),
                    TownPlan.maxPhase(settlement),
                    Lots.describe(Lots.tally(level, settlement, reach)));
        }
        return settlement.withGrid(GridSurvey.run(level, settlement).grid());
    }

    /**
     * Drops the record of anything the player has pulled down.
     *
     * <p>A lot the settlement has built on is off the list for good otherwise, so demolishing a
     * cottage left a hole the village would never fill. Forgetting it puts the lot back in the
     * plan, and the replacement goes up to whatever standard the village builds to now - which
     * is the whole upgrade path for houses, and needs no rule about overwriting somebody's
     * walls, because there are no walls left to overwrite.
     *
     * <p>Only when the lot is completely clear and completely loaded. Half a cottage is still a
     * cottage, and a chunk nobody has loaded has not told us anything.
     */
    private static Settlement forgetCleared(ServerLevel level, Settlement settlement) {
        Map<UUID, Plot> keep = new LinkedHashMap<>();
        for (Map.Entry<UUID, Plot> entry : settlement.plots().entrySet()) {
            if (standing(level, settlement, entry.getValue().anchor())) {
                keep.put(entry.getKey(), entry.getValue());
            } else {
                Placitum.LOGGER.info("'{}' lost its {} on lot {}; the lot is free again",
                        settlement.name(), entry.getValue().kind(),
                        entry.getValue().anchor().toKey());
            }
        }
        return keep.size() == settlement.plots().size() ? settlement
                : settlement.withPlots(keep).withGrid(settlement.grid());
    }

    /** Whether anything at all is still built on this lot, as far as we can see. */
    private static boolean standing(ServerLevel level, Settlement settlement, CellPos cell) {
        for (BlockPos column : TownPlan.lotColumns(cell, settlement.center())) {
            if (!level.hasChunkAt(column)) {
                return true;   // not loaded, so not demolished as far as anyone here knows
            }
            if (GridSurvey.builtOn(level, column.getX(), column.getZ())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Picks the next thing to build, if anything is missing.
     *
     * <p>One job at a time. Four things on order reports four things waiting and starts none of
     * them, which tells a player nothing about what is happening.
     *
     * <p>Only on the interval. Asking costs a read of the ground under every lot of the plan,
     * and a village that finished building an hour ago would pay it sixty times a second.
     */
    private static Settlement start(ServerLevel level, Settlement settlement) {
        if (!settlement.buildQueue().isEmpty()
                || level.getGameTime() % PlacitumConfig.PLAN_INTERVAL_TICKS.get() != 0) {
            return settlement;
        }
        // Walked once and shared. Every planner asks the same question of the same ground, and
        // three separate walks of it would be three chances for them to disagree about where the
        // town ends.
        Reach reach = Reach.from(level, settlement, TownPlan.outerPhase(settlement));

        Optional<BuildRecipe> next = Optional.empty();
        int phase = 0;
        for (; phase <= TownPlan.outerPhase(settlement) && next.isEmpty(); phase++) {
            next = RoadPlan.plan(level, settlement, phase, reach);
            if (next.isEmpty()) {
                next = LampPlan.plan(level, settlement, phase, reach);
            }
            if (next.isEmpty() && TownPlan.housing(settlement, phase)) {
                next = building(level, settlement, phase, reach);
            }
        }
        if (next.isEmpty()) {
            // Last, and outside every phase. A wall round a town that has not finished building
            // itself is a wall round a building site.
            next = WallPlan.plan(level, settlement, reach);
        }
        if (next.isEmpty()) {
            return settlement;   // nothing missing, so nothing happens
        }
        BuildRecipe recipe = next.get();
        int blocks = BuildPlanner.expand(recipe).size();
        if (blocks == 0) {
            return settlement;
        }
        Placitum.LOGGER.info("'{}' starts a {} at {} in phase {}: {} block(s)",
                settlement.name(), recipe.template().getPath(),
                recipe.anchor().toShortString(), phase - 1, blocks);
        return settlement.withBuildQueue(List.of(new BuildJob(UUID.randomUUID(),
                settlement.id(), recipe, 0, Map.of(), BuildStage.EXECUTING, 0)));
    }

    /**
     * A house or a field on the nearest empty, level lot.
     *
     * <p>Which of the two comes from the world: beds against villagers, both counted by vanilla.
     * Empty means no lot is built on twice; level means the settlement waits rather than
     * terracing a hillside, and picks the lot up again if somebody flattens it.
     */
    private static Optional<BuildRecipe> building(ServerLevel level, Settlement settlement,
            int phase, Reach reach) {
        for (CellPos cell : TownPlan.lotsInPhase(phase)) {
            if (!Lots.buildable(level, settlement, cell, reach)) {
                continue;
            }
            // Asked only once there is somewhere to put the answer. It counts villagers with an
            // entity scan over the claim, which is not a thing to do while deciding there is
            // nowhere to build.
            return Need.next(level, settlement) == Need.Kind.HOUSE
                    ? HousePlanner.plan(level, settlement, cell)
                    : FarmPlan.plan(level, settlement, cell);
        }
        return Optional.empty();
    }

    /**
     * Lays the next few blocks, on the interval, and finishes the job when it runs out.
     *
     * <p>One key, not two. There used to be an interval and a batch size, and a config file left
     * over from an earlier world put the interval back to 10 - so ten blocks every ten ticks
     * came out at exactly the old speed, and the change looked like it had done nothing.
     *
     * <p>One sound for the batch. Ten wood-place sounds in the same tick is a crack, not a
     * building site.
     */
    private static Settlement lay(ServerLevel level, Settlement settlement) {
        if (settlement.buildQueue().isEmpty()) {
            return settlement;
        }
        BuildJob job = settlement.buildQueue().getFirst();
        List<BuildOp> ops = BuildPlanner.expand(job.recipe());
        if (job.progress() >= ops.size()) {
            return complete(level, settlement, job, ops.size());
        }
        int laid = 0;
        int at = job.progress();
        for (int n = PlacitumConfig.BUILD_BLOCKS_PER_TICK.get();
                laid < n && at < ops.size(); at++) {
            BuildOp op = ops.get(at);
            if (!level.isLoaded(op.pos())) {
                break;   // that ground is not loaded; it comes round again
            }

            // Clients are told; neighbours are not. A door and a bed are two blocks each and go
            // down as separate writes, and vanilla's updateShape turns a half without its partner
            // straight into air - which is what UPDATE_KNOWN_SHAPE prevents.
            level.setBlock(op.pos(), op.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            laid++;
        }
        if (laid == 0) {
            return settlement;
        }
        level.playSound(null, ops.get(job.progress()).pos(), SoundEvents.WOOD_PLACE,
                SoundSource.BLOCKS, 0.7F, 1.0F);
        return settlement.withBuildQueue(List.of(job.withProgress(job.progress() + laid)));
    }

    /** Writes the finished thing into the record, so the settlement stops wanting it. */
    private static Settlement complete(ServerLevel level, Settlement settlement, BuildJob job,
            int blocks) {
        Identifier template = job.recipe().template();
        Settlement out = settlement.withBuildQueue(List.of());

        if (template.equals(HousePlanner.COTTAGE) || template.equals(FarmPlan.FIELD)) {
            boolean house = template.equals(HousePlanner.COTTAGE);
            CellPos cell = TownPlan.cellAt(job.recipe().anchor(), out.center());
            UUID plotId = UUID.nameUUIDFromBytes(("plot:" + job.id()).getBytes(
                    java.nio.charset.StandardCharsets.UTF_8));
            Map<UUID, Plot> plots = new LinkedHashMap<>(out.plots());
            plots.put(plotId, new Plot(plotId, cell, 1, 1,
                    house ? job.recipe().rotation() : Rotation.NONE, template,
                    house ? PlotKind.HOUSE : PlotKind.FARM,
                    house ? CottagePlan.bedCount() : 0, List.of()));
            out = out.withPlots(plots).withGrid(out.grid().with(cell, CellState.BUILT));
        }

        Placitum.LOGGER.info("'{}' finished a {} ({} blocks)", out.name(), template.getPath(),
                blocks);
        return out.record(EntryType.BUILD, out.name(), "finished a " + template.getPath(),
                level.getGameTime());
    }
}
