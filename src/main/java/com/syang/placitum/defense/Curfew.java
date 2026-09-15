package com.syang.placitum.defense;

import com.syang.placitum.Placitum;
import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.AnchorSet;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.lifecycle.Lifecycle;
import com.syang.placitum.store.SettlementManager;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
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

    /** Who has already been told to go home tonight. */
    private static final java.util.Set<java.util.UUID> announced = new java.util.HashSet<>();

    private Curfew() {}

    /**
     * Nudges everyone indoors if dusk is close.
     *
     * @return true when the curfew is currently in force
     */
    public static boolean enforce(ServerLevel level, SettlementManager manager, Settlement settlement) {
        if (!curfewActive(level)) {
            announced.clear();   // next dusk gets a fresh set of announcements
            return false;
        }
        AnchorSet anchors = settlement.anchors();
        if (anchors.shelters().isEmpty()) {
            return true;   // nowhere to send anyone; M3 gives the settlement houses of its own
        }

        // Shelters claimed so far tonight. Without this every villager is sent to whichever
        // shelter happens to be nearest - which, from the bell they all gather at, is the same
        // one for all of them. Six villagers piling onto one bed is not "everyone got indoors",
        // and it looks exactly like the code doing nothing.
        Set<BlockPos> taken = new HashSet<>();
        for (Resident resident : settlement.residents()) {
            if (!resident.materialized()) {
                continue;
            }
            Entity entity = Lifecycle.findEntity(level, manager, resident);
            if (entity instanceof Villager villager) {
                sendHome(level, villager, anchors, taken);
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

    private static void sendHome(ServerLevel level, Villager villager, AnchorSet anchors,
            Set<BlockPos> taken) {
        if (villager.isSleeping()) {
            return;
        }
        boolean ownBed = villager.getBrain().getMemory(MemoryModuleType.HOME).isPresent();
        BlockPos target = shelterFor(villager, anchors, taken).orElse(null);
        if (target == null) {
            return;
        }
        boolean sharing = !ownBed && taken.contains(target);
        taken.add(target);
        if (villager.blockPosition().closerThan(target, HOME_ENOUGH)) {
            return;   // near enough; vanilla takes it from here
        }
        // Logged once per villager per night, not once per pass. The first version wrote six
        // lines a second and drowned the log it was meant to explain.
        // Says which of the three cases this is. "Sent to a position" on its own cannot
        // distinguish a villager walking to its own bed from one with nowhere to sleep, and
        // that distinction is the entire point of the feature.
        if (announced.add(villager.getUUID())) {
            Placitum.LOGGER.debug("Curfew: {} -> {} ({})", villager.getUUID(),
                    target.toShortString(),
                    ownBed ? "own bed" : sharing ? "sharing, no bed free" : "nearest free shelter");
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
    private static Optional<BlockPos> shelterFor(Villager villager, AnchorSet anchors,
            Set<BlockPos> taken) {
        Optional<GlobalPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME);
        if (home.isPresent()) {
            return Optional.of(home.get().pos());   // its own bed always wins
        }

        // Nearest shelter nobody has been sent to yet, so a bedless crowd spreads out instead
        // of following each other to the same doorway.
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos shelter : anchors.shelters()) {
            if (taken.contains(shelter)) {
                continue;
            }
            double dist = shelter.distSqr(villager.blockPosition());
            if (dist < bestDist) {
                bestDist = dist;
                best = shelter;
            }
        }
        // More villagers than shelters is normal and not a reason to leave anyone outside:
        // sharing a roof beats standing in the dark.
        return best != null ? Optional.of(best) : anchors.nearestShelter(villager.blockPosition());
    }
}
