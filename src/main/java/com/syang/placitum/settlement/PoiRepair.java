package com.syang.placitum.settlement;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.lifecycle.Lifecycle;
import com.syang.placitum.store.SettlementManager;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Frees beds and workstations still claimed by villagers that no longer exist.
 *
 * <p>Earlier builds discarded entities without releasing their POI claims, so every
 * promote/demote round trip left one more bed held by a ghost. The leak is fixed at source, but
 * the claims it already made are permanent: a villager whose HOME memory was erased in the
 * meantime has nothing left to release, and no amount of correct behaviour from here on gives
 * the bed back.
 *
 * <p>Which makes this the repair, not a workaround. A player whose village has been visited a
 * few times on an older build has a village that can never grow, and telling them to start a
 * new one is not an answer.
 *
 * <p>Deliberately conservative: it compares tickets taken against villagers that actually
 * remember the place, and releases only the surplus. Releasing blindly would let two villagers
 * claim one bed.
 */
public final class PoiRepair {

    /** Roughly every five seconds; a brain check, not a scan. */
    private static final int FORGET_INTERVAL_TICKS = 100;

    /** What one repair pass found. */
    public record Result(int scanned, int freed) {}

    private PoiRepair() {}

    /**
     * Makes villagers forget places that are no longer there.
     *
     * <p>The other direction from {@link #run}, and the one a player actually causes. Break a
     * bed and its owner keeps the memory of it: vanilla only looks for a new home when that
     * memory is empty, so the villager goes on walking to an empty patch of floor every night
     * and the settlement never regains the bed it lost.
     *
     * <p>It also stops a broken bed taking the server down. PoiManager.release throws outright
     * on a position it has no record of, and demote releases every claim a villager holds - so
     * one bed broken at the wrong moment crashed the tick loop.
     */
    public static void forgetMissing(ServerLevel level, SettlementManager manager,
            Settlement settlement) {
        if (level.getGameTime() % FORGET_INTERVAL_TICKS != 0) {
            return;
        }
        for (Resident resident : settlement.residents()) {
            if (!resident.materialized()) {
                continue;
            }
            UUID entityId = manager.entityOf(resident.id());
            if (entityId != null
                    && level.getEntity(entityId) instanceof Villager villager) {
                com.syang.placitum.lifecycle.Lifecycle.forgetMissingPois(level, villager);
            }
        }
    }

    public static Result run(ServerLevel level, SettlementManager manager, Settlement settlement) {
        int radius = settlement.identity().claimRadiusChunks() * 16;
        PoiManager poi = level.getPoiManager();

        Set<BlockPos> remembered = rememberedHomes(level, manager, settlement);
        List<PoiRecord> homes = poi
                .getInRange(holder -> holder.is(PoiTypes.HOME), settlement.center(), radius,
                        PoiManager.Occupancy.IS_OCCUPIED)
                .toList();

        int freed = 0;
        for (PoiRecord record : homes) {
            if (remembered.contains(record.getPos())) {
                continue;   // a living resident still claims this one
            }
            // Occupied, and nobody alive remembers it. That is a ghost's bed.
            if (poi.release(record.getPos())) {
                freed++;
                Placitum.LOGGER.info("Freed an abandoned bed at {} in '{}'",
                        record.getPos().toShortString(), settlement.name());
            }
        }
        return new Result(homes.size(), freed);
    }

    /** Beds that a currently embodied resident still has in its brain. */
    private static Set<BlockPos> rememberedHomes(ServerLevel level, SettlementManager manager,
            Settlement settlement) {
        Set<BlockPos> homes = new HashSet<>();
        for (Resident resident : settlement.residents()) {
            Entity entity = Lifecycle.findEntity(level, manager, resident);
            if (!(entity instanceof Villager villager)) {
                continue;
            }
            villager.getBrain().getMemory(MemoryModuleType.HOME)
                    .map(GlobalPos::pos)
                    .ifPresent(homes::add);
        }
        return homes;
    }
}
