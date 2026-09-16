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

    /** Settlements already told they have no builder. */
    private static final java.util.Set<java.util.UUID> WARNED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * How a settlement writes a block.
     *
     * <p>Clients are told; neighbours are not. That second half matters more than it sounds: a
     * door and a bed are two blocks each, they go down as two separate writes, and vanilla's
     * updateShape turns a half without its partner straight into air. With neighbour updates on,
     * placing the lower door half destroyed it before the upper half existed, and the house ended
     * up with the top of a door floating over a gap nobody could walk through. Read back out of
     * the world it was unmistakable - "#...B.." where the south wall should be.
     *
     * <p>UPDATE_CLIENTS alone was not enough, which cost a second round of half-built beds:
     * it silences the neighbour notification but updateNeighbourShapes still runs, so laying
     * a wall beside a bed asked the bed to check for its other half and it removed itself
     * when the answer was no. UPDATE_KNOWN_SHAPE is the half that actually closes that door.
     *
     * <p>This is what vanilla's own structure placement uses, for the same reason.
     */
    public static final int PLACE_FLAGS =
            net.minecraft.world.level.block.Block.UPDATE_CLIENTS
                    | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE;

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
            // Same rate rule as the virtual side: one builder's worth if nobody holds the
            // job. Two paths that built at different speeds would make walking away from a
            // half-built wall a way to finish it faster.
            BuildJob advanced = job;
            int hands = Math.max(1, countBuilders(settlement));
            for (int i = 0; i < hands; i++) {
                BuildJob stepped = lay(level, settlement, advanced, builders);
                if (stepped == advanced) {
                    break;
                }
                advanced = stepped;
            }
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

        // Loaded chunks are the only thing that actually has to be true, and it was checked
        // above. Villager proximity was tried twice as a gate and failed twice: at arm's reach
        // the wall stopped after 24 blocks of 1618, because the vanilla brain drops a walk
        // target the moment it would rather farm; at 48 blocks it stopped after 97, because the
        // ring is 168 across and the far side is outside any radius centred on the village.
        //
        // The settlement is building its wall. Where its people happen to be standing is
        // presentation, so the nearest is still sent over and swings if it arrives - but a
        // village that cannot build because nobody will stand still is the complaint this mod
        // exists to remove, wearing a hat. A BuilderEntity with AI of its own is the honest fix
        // and is not this milestone.
        Villager hand = nearestWithin(builders, op.pos(), PlacitumConfig.BUILDER_REACH.get());
        if (hand != null) {
            hand.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        } else {
            walkSomebodyOver(builders, op.pos());
        }

        level.setBlock(op.pos(), op.state(), PLACE_FLAGS);
        level.playSound(null, op.pos(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
        return job.withProgress(job.progress() + 1);
    }


    private static int countBuilders(Settlement settlement) {
        int n = 0;
        for (Resident resident : settlement.residents()) {
            if (resident.counts() && resident.assignment().job().equals(Assignment.BUILDER)) {
                n++;
            }
        }
        return n;
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
            // Once a settlement, not once a block. The same line every ten ticks buries
            // everything worth reading, which this project has paid to learn twice.
            if (WARNED.add(settlement.id())) {
                Placitum.LOGGER.info("'{}' has no builder; the nearest resident is laying blocks",
                        settlement.name());
            }
            return anyone;
        }
        return builders;
    }
}
