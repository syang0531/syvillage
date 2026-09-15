package com.syang.placitum.build;

import com.syang.placitum.Placitum;
import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.Assignment;
import com.syang.placitum.data.BuildJob;
import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.BuildStage;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.store.SettlementManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Building where somebody can see it.
 *
 * <p>The L0 half of {@link com.syang.placitum.sim.module.ConstructionModule}: same op list, same
 * progress counter, blocks placed one at a time instead of a number going up. Which of the two
 * runs is decided by whether the residents have bodies, and exactly one of them ever does - the
 * counter is shared, so both would double it.
 *
 * <p>No BuilderEntity. docs/construction.md names one, but the entity swap M1 needed for militia
 * was for combat AI that vanilla villagers refuse to have, and it cost three rounds of losing
 * professions across the swap. A builder needs to walk somewhere and put a block down, and a
 * walk target does that to an ordinary villager without replacing it.
 */
public final class BuildTick {

    private BuildTick() {}

    /** Places at most one block, on the interval, for each job being worked. */
    public static Settlement run(ServerLevel level, SettlementManager manager,
            Settlement settlement) {
        if (settlement.buildQueue().isEmpty()) {
            return settlement;
        }
        int interval = PlacitumConfig.BUILD_OP_INTERVAL_TICKS.get();
        if (level.getGameTime() % interval != 0) {
            return settlement;
        }

        List<Villager> builders = buildersOf(level, manager, settlement);
        List<BuildJob> next = new ArrayList<>(settlement.buildQueue().size());
        boolean changed = false;

        for (BuildJob job : settlement.buildQueue()) {
            if (job.stage() != BuildStage.EXECUTING) {
                next.add(job);
                continue;
            }
            BuildJob advanced = lay(level, settlement, job, builders);
            changed |= advanced != job;
            next.add(advanced);
        }
        return changed ? settlement.withBuildQueue(List.copyOf(next)) : settlement;
    }

    /**
     * One block, if somebody can reach it.
     *
     * <p>Out of reach, the builder is sent walking instead and progress waits. That is the whole
     * of the reach problem: ops are sorted Y-ascending, so by the time a course is high the
     * builder is standing on the one below it, and docs/construction.md leans on that rather
     * than on scaffolding nobody would enjoy debugging.
     */
    private static BuildJob lay(ServerLevel level, Settlement settlement, BuildJob job,
            List<Villager> builders) {
        List<BuildOp> ops = BuildPlanner.expand(job.recipe());
        if (job.progress() >= ops.size()) {
            return job;
        }
        BuildOp op = ops.get(job.progress());
        if (!level.isLoaded(op.pos())) {
            return job;   // the far side of the ring is not loaded; it will come round again
        }

        double reach = PlacitumConfig.BUILDER_REACH.get();
        Villager hand = nearestWithin(builders, op.pos(), reach);
        if (hand == null) {
            walkSomebodyOver(builders, op.pos());
            return job;
        }

        level.setBlock(op.pos(), op.state(), 3);
        level.playSound(null, op.pos(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
        hand.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        return job.withProgress(job.progress() + 1);
    }

    private static Villager nearestWithin(List<Villager> builders, BlockPos pos, double reach) {
        Villager best = null;
        double bestDistance = reach * reach;
        for (Villager villager : builders) {
            double distance = villager.distanceToSqr(
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = villager;
            }
        }
        return best;
    }

    /**
     * Sends the nearest builder towards the work.
     *
     * <p>A walk target, not a teleport and not a new entity. The villager keeps its own brain,
     * its trades and its profession - and this is the same mechanism curfew already uses to send
     * people indoors, so there is one way in this mod to tell a villager where to be.
     */
    private static void walkSomebodyOver(List<Villager> builders, BlockPos pos) {
        Villager nearest = nearestWithin(builders, pos, Double.MAX_VALUE);
        if (nearest == null) {
            return;
        }
        nearest.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(pos, (float) (double) PlacitumConfig.BUILDER_WALK_SPEED.get(), 2));
    }

    /**
     * The villagers holding the builder job, as entities.
     *
     * <p>Anyone at all if nobody holds it. A settlement that queued a wall and then had no
     * builder would otherwise stand and watch it not get built, which looks precisely like the
     * bug this milestone is meant to remove - and the virtual side already works the same way.
     */
    private static List<Villager> buildersOf(ServerLevel level, SettlementManager manager,
            Settlement settlement) {
        List<Villager> builders = new ArrayList<>();
        List<Villager> anyone = new ArrayList<>();
        for (Resident resident : settlement.residents()) {
            if (!resident.materialized()) {
                continue;
            }
            Entity entity = level.getEntity(manager.entityOf(resident.id()));
            if (!(entity instanceof Villager villager)) {
                continue;
            }
            anyone.add(villager);
            if (resident.assignment().job().equals(Assignment.BUILDER)) {
                builders.add(villager);
            }
        }
        if (builders.isEmpty() && !anyone.isEmpty()) {
            Placitum.LOGGER.debug("'{}' has no builder; the nearest resident is laying blocks",
                    settlement.name());
            return anyone;
        }
        return builders;
    }
}
