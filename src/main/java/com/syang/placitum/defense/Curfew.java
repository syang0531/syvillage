package com.syang.placitum.defense;

import com.syang.placitum.Placitum;
import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.AnchorSet;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.lifecycle.Lifecycle;
import com.syang.placitum.store.SettlementManager;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Getting everyone indoors before dark.
 *
 * <p>The cheapest thing in docs/defense.md and the one that saves the most villagers. Vanilla
 * villagers do head home at dusk, but only if they have a bed they can reach - and a villager
 * who does not simply stands outside until something kills it. That is the single commonest
 * way a village quietly empties out.
 *
 * <p>Two changes, both small. Start earlier than vanilla does, and give the ones with nowhere
 * to go somewhere to go: the nearest shelter, not necessarily their own.
 *
 * <p>No brain surgery. Setting a walk target is something vanilla behaviours already compete
 * over politely, so the villager keeps its own schedule, panic and pathfinding intact.
 */
public final class Curfew {

    /** Villagers already asleep or already home need no help. */
    private static final int HOME_ENOUGH = 6;

    private Curfew() {}

    /**
     * Nudges everyone indoors if dusk is close.
     *
     * @return true when the curfew is currently in force
     */
    public static boolean enforce(ServerLevel level, SettlementManager manager, Settlement settlement) {
        if (!curfewActive(level)) {
            return false;
        }
        AnchorSet anchors = settlement.anchors();
        if (anchors.shelters().isEmpty()) {
            return true;   // nowhere to send anyone; M3 gives the settlement houses of its own
        }

        for (Resident resident : settlement.residents()) {
            if (!resident.materialized()) {
                continue;
            }
            Entity entity = Lifecycle.findEntity(level, manager, resident);
            if (entity instanceof Villager villager) {
                sendHome(level, villager, anchors);
            }
        }
        return true;
    }

    /** A vanilla day, for turning the clock into a time of day. */
    private static final long DAY_LENGTH = 24000L;

    /** Roughly when the light gives out and hostile mobs start surviving above ground. */
    private static final long DUSK = 12000L;

    /**
     * True from {@code curfewLeadTicks} before dusk until sunrise.
     *
     * <p>26.2 renamed the day clock: {@code getDayTime()} is gone and the overworld clock is
     * read through {@link net.minecraft.world.level.Level#getOverworldClockTime()}. Dimensions
     * with a fixed time have no dusk to be early for.
     */
    public static boolean curfewActive(ServerLevel level) {
        if (level.dimensionType().hasFixedTime()) {
            return false;
        }
        long timeOfDay = Math.floorMod(level.getOverworldClockTime(), DAY_LENGTH);
        return timeOfDay >= DUSK - PlacitumConfig.CURFEW_LEAD_TICKS.get();
    }

    private static void sendHome(ServerLevel level, Villager villager, AnchorSet anchors) {
        if (villager.isSleeping()) {
            return;
        }
        BlockPos target = shelterFor(villager, anchors).orElse(null);
        if (target == null) {
            return;
        }
        if (villager.blockPosition().closerThan(target, HOME_ENOUGH)) {
            return;   // near enough; vanilla takes it from here
        }
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(target, (float) (double) PlacitumConfig.CURFEW_WALK_SPEED.get(),
                        HOME_ENOUGH));
    }

    /**
     * Its own bed if it has one, otherwise the nearest shelter.
     *
     * <p>The fallback is the whole point. A villager with no bed is exactly the one that dies,
     * and any roof will do.
     */
    private static Optional<BlockPos> shelterFor(Villager villager, AnchorSet anchors) {
        Optional<GlobalPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME);
        if (home.isPresent()) {
            return Optional.of(home.get().pos());
        }
        Optional<BlockPos> nearest = anchors.nearestShelter(villager.blockPosition());
        if (nearest.isPresent()) {
            Placitum.LOGGER.debug("{} has no bed; sending it to {}", villager.getUUID(),
                    nearest.get().toShortString());
        }
        return nearest;
    }
}
