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
 * Everything a settlement does, once per tick, while somebody is there to see it.
 *
 * <p>There is no simulation behind this and no second path for when nobody is watching. A
 * village that is never visited never grows, which is the deal: the fun is watching it happen,
 * and in exchange the whole L0/L2 boundary disappears - six of the seven bugs the wall pipeline
 * produced lived on that boundary.
 *
 * <p>The order is the design. Roads first because nothing can be sited without one, then
 * light because the original complaint was mobs killing villagers at night, then fields and
 * houses - beds and food, which is everything vanilla breeding asks for.
 */
public final class SettlementTick {

    private SettlementTick() {}

    public static Settlement run(ServerLevel level, Settlement settlement) {
        Settlement out = resurvey(level, settlement);
        out = freeze(level, out);
        out = lay(level, out);
        return out;
    }

    /** Re-reads the ground now and then, so building notices what a player has changed. */
    private static Settlement resurvey(ServerLevel level, Settlement settlement) {
        if (level.getGameTime() % PlacitumConfig.SURVEY_INTERVAL_TICKS.get() != 0) {
            return settlement;
        }
        return settlement.withGrid(GridSurvey.run(level, settlement).grid());
    }

    /**
     * Decides what is missing and freezes a recipe for it.
     *
     * <p>One job at a time. A settlement with four things on order reports four things waiting
     * and starts none of them, which tells a player nothing about what is happening.
     */
    private static Settlement freeze(ServerLevel level, Settlement settlement) {
        if (!settlement.buildQueue().isEmpty()) {
            return settlement;
        }
        Optional<BuildRecipe> next = plan(level, settlement);
        if (next.isEmpty()) {
            return settlement;
        }
        BuildRecipe recipe = next.get();
        int blocks = BuildPlanner.expand(recipe).size();
        if (blocks == 0) {
            return settlement;
        }
        Placitum.LOGGER.info("'{}' starts a {} at {}: {} block(s)", settlement.name(),
                recipe.template().getPath(), recipe.anchor().toShortString(), blocks);
        return settlement.withBuildQueue(List.of(new BuildJob(UUID.randomUUID(),
                settlement.id(), recipe, 0, Map.of(), BuildStage.EXECUTING, 0)));
    }

    /**
     * What the settlement builds next.
     *
     * <p>Nothing here costs anything. Materials were a whole economy that existed to make
     * building take time, and building already takes time - one block every
     * buildOpIntervalTicks, in front of you.
     */
    private static Optional<BuildRecipe> plan(ServerLevel level, Settlement settlement) {
        if (settlement.grid().countOf(CellState.ROAD) == 0) {
            return RoadPlan.plan(level, settlement);
        }
        Optional<BuildRecipe> lamp = LampPlan.plan(level, settlement);
        if (lamp.isPresent()) {
            return lamp;   // light first: mobs spawning indoors is the original complaint
        }
        // One field per three cottages. Beds without a field is a village that will never
        // have a second generation: vanilla breeding needs villagers carrying food, and food
        // comes from a farmer harvesting a crop.
        int farms = countOf(settlement, PlotKind.FARM);
        if (farms == 0 || settlement.houseCount() >= farms * 3) {
            Optional<BuildRecipe> field = FarmPlan.plan(level, settlement);
            if (field.isPresent()) {
                return field;
            }
        }
        return HousePlanner.plan(level, settlement);
    }

    /** Lays the next block or two, on the interval, and finishes the job when it runs out. */
    private static Settlement lay(ServerLevel level, Settlement settlement) {
        if (settlement.buildQueue().isEmpty()
                || level.getGameTime() % PlacitumConfig.BUILD_OP_INTERVAL_TICKS.get() != 0) {
            return settlement;
        }
        BuildJob job = settlement.buildQueue().getFirst();
        List<BuildOp> ops = BuildPlanner.expand(job.recipe());
        if (job.progress() >= ops.size()) {
            return complete(level, settlement, job, ops.size());
        }
        BuildOp op = ops.get(job.progress());
        if (!level.isLoaded(op.pos())) {
            return settlement;   // that ground is not loaded; it comes round again
        }

        // Clients are told; neighbours are not. A door and a bed are two blocks each and go down
        // as separate writes, and vanilla's updateShape turns a half without its partner straight
        // into air - which is what UPDATE_KNOWN_SHAPE prevents.
        level.setBlock(op.pos(), op.state(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.playSound(null, op.pos(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.7F, 1.0F);
        return settlement.withBuildQueue(List.of(job.withProgress(job.progress() + 1)));
    }

    /** Writes the finished thing into the record, so the settlement stops wanting it. */
    private static Settlement complete(ServerLevel level, Settlement settlement, BuildJob job,
            int blocks) {
        Identifier template = job.recipe().template();
        Settlement out = settlement.withBuildQueue(List.of());

        if (template.equals(HousePlanner.COTTAGE)) {
            CellPos cell = out.grid().cellAt(job.recipe().anchor());
            UUID plotId = UUID.nameUUIDFromBytes(("plot:" + job.id()).getBytes(
                    java.nio.charset.StandardCharsets.UTF_8));
            Map<UUID, Plot> plots = new LinkedHashMap<>(out.plots());
            plots.put(plotId, new Plot(plotId, cell, 1, 1, job.recipe().rotation(), template,
                    PlotKind.HOUSE, CottagePlan.bedCount(), List.of()));
            out = out.withPlots(plots).withGrid(out.grid().with(cell, CellState.BUILT));
        } else if (template.equals(RoadPlan.CROSS)) {
            out = out.withGrid(RoadPlan.markCells(out.grid(), job.recipe()));
        } else if (template.equals(FarmPlan.FIELD)) {
            CellPos cell = out.grid().cellAt(job.recipe().anchor());
            UUID plotId = UUID.nameUUIDFromBytes(("plot:" + job.id()).getBytes(
                    java.nio.charset.StandardCharsets.UTF_8));
            Map<UUID, Plot> plots = new LinkedHashMap<>(out.plots());
            plots.put(plotId, new Plot(plotId, cell, 1, 1, Rotation.NONE, template,
                    PlotKind.FARM, 0, List.of()));
            out = out.withPlots(plots).withGrid(out.grid().with(cell, CellState.BUILT));
        }

        Placitum.LOGGER.info("'{}' finished a {} ({} blocks)", out.name(), template.getPath(),
                blocks);
        return out.record(EntryType.BUILD, out.name(),
                "finished a " + template.getPath(), level.getGameTime());
    }

    private static int countOf(Settlement settlement, PlotKind kind) {
        int n = 0;
        for (Plot plot : settlement.plots().values()) {
            if (plot.kind() == kind) {
                n++;
            }
        }
        return n;
    }
}
