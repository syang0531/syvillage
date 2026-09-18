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
import net.minecraft.resources.Identifier;
import com.syang.placitum.data.Stage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    /** The last job each settlement started, and which ones have already been warned about. */
    private static final Map<UUID, BuildRecipe> LAST_STARTED = new java.util.HashMap<>();
    private static final java.util.Set<UUID> REPEATED = new java.util.HashSet<>();

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
                .withStage(Trades.earned(level, settlement));
        if (out.stage() != settlement.stage()) {
            String what = out.stage() == Stage.WALLED
                    ? "has a lord, and may wall itself"
                    : "has a head, and may lay its streets";
            Placitum.LOGGER.info("'{}' {}", out.name(), what);
            out = out.record(EntryType.BUILD, out.name(), what, level.getGameTime());
        }
        settlement = out;

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
        // Streets and lots wait for a village head; light does not. A street is a construction
        // and light is a utility: the grid of roads is the one visible sign that somebody is
        // organising this place, and monsters do not ask whether anybody is.
        boolean headed = settlement.headed();
        for (; phase <= TownPlan.outerPhase(settlement) && next.isEmpty(); phase++) {
            if (headed) {
                next = RoadPlan.plan(level, settlement, phase, reach);
            }
            if (next.isEmpty()) {
                next = LampPlan.plan(level, settlement, phase, reach);
            }
            if (next.isEmpty() && headed && TownPlan.housing(settlement, phase)) {
                next = building(level, settlement, phase, reach);
            }
        }
        if (next.isEmpty()) {
            // Last, and outside every phase. A wall round a town that has not finished building
            // itself is a wall round a building site.
            //
            // Gates before the rampart: the wall leaves their ground alone, so a gatehouse can
            // go up whenever its own footprint is ready, and a town that gets one gate and three
            // gaps is further along than a town with a ring and no way through it.
            for (Direction side : GatePlan.sides()) {
                next = GatePlan.plan(level, settlement, side, reach);
                if (next.isPresent()) {
                    break;
                }
            }
        }
        if (next.isEmpty()) {
            for (int[] corner : TowerPlan.corners()) {
                next = TowerPlan.plan(level, settlement, corner, reach);
                if (next.isPresent()) {
                    break;
                }
            }
        }
        if (next.isEmpty()) {
            next = WallPlan.plan(level, settlement, reach);
        }
        if (next.isEmpty()) {
            reportIdle(level, settlement, reach);
            return settlement;   // nothing missing, so nothing happens
        }
        BuildRecipe recipe = next.get();
        int blocks = BuildPlanner.expand(recipe).size();
        if (blocks == 0) {
            return settlement;
        }
        // The loop the tenth principle is about, said out loud. A structure that is planned
        // again straight after being finished is one the plan and the world disagree about,
        // and it never shows up in the idle report - the settlement is never idle. Once, not
        // every five seconds: the alarm is the first line, the rest would be the same line.
        // The whole recipe, not the template and anchor: every gatehouse is anchored on the
        // bell now, so north followed by east looked like a repeat, and so did one batch of
        // rampart after another. A true loop plans the identical recipe - same ground, same
        // spans - and that is what equality on the record asks.
        BuildRecipe last = LAST_STARTED.put(settlement.id(), recipe);
        if (last != null && last.equals(recipe) && !REPEATED.contains(settlement.id())) {
            REPEATED.add(settlement.id());
            Placitum.LOGGER.warn("'{}' plans a {} at {} again straight after finishing one. The"
                    + " world has it standing and the plan does not; whatever the plan asks to"
                    + " decide that is looking at the wrong block", settlement.name(),
                    recipe.template().getPath(), recipe.anchor().toShortString());
        } else if (last != null && !last.equals(recipe)) {
            REPEATED.remove(settlement.id());
        }
        if (recipe.template().equals(LampPlan.LAMPS)) {
            // Remembered as soon as they are planned, so that a post the player knocks down
            // later is a post that stays down. Streets are relaid when they go missing; light
            // is the player's to refuse, one post at a time.
            java.util.List<Long> posts = new java.util.ArrayList<>();
            for (Spans post : Spans.decode(recipe.gates())) {
                posts.add(Reach.key(post.x(), post.z()));
            }
            settlement = settlement.withLamps(posts);
        }
        Placitum.LOGGER.info("'{}' starts a {} at {} in phase {}: {} block(s)",
                settlement.name(), recipe.template().getPath(),
                recipe.anchor().toShortString(), phase - 1, blocks);
        return settlement.withBuildQueue(List.of(new BuildJob(UUID.randomUUID(),
                settlement.id(), recipe, 0, Map.of(), BuildStage.EXECUTING, 0)));
    }

    /**
     * Says why nothing is being built, now and then.
     *
     * <p>Reported from here rather than from the survey, because the survey runs before anything
     * has been chosen: the queue is empty at that moment on every pass, so it announced that the
     * settlement was building nothing while fourteen lots were waiting and one was about to go
     * up. A message that is wrong twice a minute is worse than no message.
     */
    private static void reportIdle(ServerLevel level, Settlement settlement, Reach reach) {
        if (level.getGameTime() % PlacitumConfig.SURVEY_INTERVAL_TICKS.get() != 0) {
            return;
        }
        Placitum.LOGGER.info("'{}' is building nothing. {} column(s) of street reach the bell,"
                        + " {} of ground reach a street. Lots out to phase {}: {}",
                settlement.name(), reach.streetSize(), reach.size(),
                TownPlan.maxPhase(settlement),
                Lots.describe(Lots.tally(level, settlement, reach)));
        if (!settlement.walled()) {
            return;
        }
        // "Standing" before anything else. A finished gatehouse reads as sixteen unreachable
        // columns, because a finished gatehouse is eight blocks tall and the walk cannot climb
        // it - so the line that was meant to say why nothing was built said the same thing
        // whether it had been built or not, and it fooled me once within a minute of writing it.
        for (Direction side : GatePlan.sides()) {
            Placitum.LOGGER.info("  gate {}: {}", side,
                    GatePlan.status(level, settlement, side, reach));
        }
        for (int[] corner : TowerPlan.corners()) {
            Placitum.LOGGER.info("  tower {},{}: {}", corner[0], corner[1],
                    TowerPlan.status(level, settlement, corner, reach));
        }
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
            Need.Kind need = Need.next(level, settlement);
            Optional<Identifier> building = Houses.pick(settlement.craft(), need, cell);
            return building.isPresent()
                    ? HousePlan.plan(level, settlement, cell, building.get())
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
        // A job planned before a restart names a vanilla template nobody has loaded yet in
        // this game. Expansion cannot ask the level, so the level is asked here, once.
        for (BuildJob job : settlement.buildQueue()) {
            if (Houses.isVanilla(job.recipe().template())) {
                Template.ensure(level.getStructureManager(), job.recipe().template());
            }
        }
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

        if (Houses.isVanilla(template) || template.equals(FarmPlan.FIELD)) {
            boolean field = template.equals(FarmPlan.FIELD);
            CellPos cell = TownPlan.cellAt(job.recipe().anchor(), out.center());
            UUID plotId = UUID.nameUUIDFromBytes(("plot:" + job.id()).getBytes(
                    java.nio.charset.StandardCharsets.UTF_8));
            Map<UUID, Plot> plots = new LinkedHashMap<>(out.plots());
            plots.put(plotId, new Plot(plotId, cell, 1, 1,
                    field ? Rotation.NONE : job.recipe().rotation(), template,
                    field ? PlotKind.FARM : Houses.kindOf(template),
                    field ? 0 : Template.of(template).bedCount(), List.of()));
            out = out.withPlots(plots).withGrid(out.grid().with(cell, CellState.BUILT));
        }

        Placitum.LOGGER.info("'{}' finished a {} ({} blocks)", out.name(), template.getPath(),
                blocks);
        return out.record(EntryType.BUILD, out.name(), "finished a " + template.getPath(),
                level.getGameTime());
    }
}
