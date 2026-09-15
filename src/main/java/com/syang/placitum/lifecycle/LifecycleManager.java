package com.syang.placitum.lifecycle;

import com.syang.placitum.Placitum;
import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.data.SettlementId;
import com.syang.placitum.store.SettlementManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Decides which settlements have bodies right now.
 *
 * <p>The thresholds are asymmetric on purpose. With a single radius, a player standing on the
 * boundary would spawn and despawn a village several times a second.
 */
public final class LifecycleManager {

    private final Map<UUID, PromotionTask> promoting = new LinkedHashMap<>();
    private final Map<UUID, Long> pendingDemote = new HashMap<>();

    /**
     * Settlements a player has manually demoted and told to stay down.
     *
     * <p>Without this, {@code /placitum demote} is undone on the very next tick: the player
     * issuing it is standing in the village, so the distance check immediately promotes it
     * again. The command would appear to do nothing, which makes it useless for exactly the
     * job it exists for - watching the virtual formula run.
     *
     * <p>Runtime only. A restart releases every hold, because a hold is a debugging stance,
     * not settlement state.
     */
    private final Set<UUID> heldVirtual = new HashSet<>();

    public void tick(MinecraftServer server) {
        SettlementManager manager = SettlementManager.get(server);
        long budget = PlacitumConfig.TICK_BUDGET_NANOS.get();
        long deadline = System.nanoTime() + budget;

        for (SettlementId entry : manager.listed()) {
            if (System.nanoTime() >= deadline) {
                return;   // the rest carry over; determinism makes that safe
            }
            Settlement settlement = manager.find(entry.id()).orElse(null);
            if (settlement == null) {
                continue;
            }
            ServerLevel level = server.getLevel(settlement.dimension());
            if (level == null) {
                continue;
            }
            tickOne(level, manager, settlement, deadline);
        }
    }

    private void tickOne(ServerLevel level, SettlementManager manager, Settlement settlement, long deadline) {
        double nearest = nearestPlayerDistance(level, settlement.center());
        int promoteRadius = PlacitumConfig.PROMOTE_RADIUS.get();
        int demoteRadius = PlacitumConfig.DEMOTE_RADIUS.get();
        long now = level.getGameTime();
        UUID id = settlement.id();

        PromotionTask task = promoting.get(id);
        if (task != null) {
            Settlement next = task.advance(level, manager, settlement, now, deadline);
            manager.put(next);
            if (task.done()) {
                promoting.remove(id);
            }
            return;
        }

        if (heldVirtual.contains(id)) {
            return;
        }

        if (nearest <= promoteRadius && settlement.materializedCount() == 0
                && !settlement.residents().isEmpty()) {
            pendingDemote.remove(id);
            Placitum.LOGGER.info("PROMOTE start: {} '{}' - nearest player {} blocks, {} resident(s)",
                    SettlementManager.shortId(id), settlement.name(), (int) nearest,
                    settlement.residentCount());
            promoting.put(id, new PromotionTask(id));
            return;
        }

        if (nearest > demoteRadius && settlement.materializedCount() > 0) {
            long since = pendingDemote.computeIfAbsent(id, key -> now);
            if (now - since >= PlacitumConfig.DEMOTE_DELAY_TICKS.get()) {
                pendingDemote.remove(id);
                Placitum.LOGGER.info("DEMOTE: {} '{}' - nearest player {} blocks, {} materialized",
                        SettlementManager.shortId(id), settlement.name(), (int) nearest,
                        settlement.materializedCount());
                manager.put(demoteAll(level, manager, settlement));
            }
        } else if (nearest <= demoteRadius) {
            pendingDemote.remove(id);
        }
    }

    /**
     * Distance to the closest player. In multiplayer any player inside the promote radius is
     * enough to keep a settlement up, and every player must leave before it goes down.
     */
    private static double nearestPlayerDistance(ServerLevel level, BlockPos center) {
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            best = Math.min(best, Math.sqrt(player.blockPosition().distSqr(center)));
        }
        return best;
    }

    public static Settlement demoteAll(ServerLevel level, SettlementManager manager, Settlement settlement) {
        List<Resident> updated = new ArrayList<>();
        int count = 0;
        for (Resident r : settlement.residents()) {
            if (r.materialized()) {
                updated.add(Lifecycle.demote(level, manager, r));
                count++;
            } else {
                updated.add(r);
            }
        }
        if (count > 0) {
            Placitum.LOGGER.info("  wrote back {} resident(s) of '{}'", count, settlement.name());
        }
        return SettlementManager.withResidents(settlement, updated);
    }

    /**
     * Demotes only the residents inside an unloading chunk, with no delay.
     *
     * <p>Waiting is not an option here: once the unload completes the entity is gone and its
     * health, progress and trades go with it.
     */
    public Settlement demoteInChunk(ServerLevel level, SettlementManager manager, Settlement settlement,
            int chunkX, int chunkZ) {
        List<Resident> updated = new ArrayList<>();
        boolean changed = false;
        for (Resident r : settlement.residents()) {
            BlockPos pos = r.coarsePos();
            boolean inChunk = (pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ;
            if (r.materialized() && inChunk) {
                Placitum.LOGGER.info("DEMOTE (chunk {},{} unloading): {} of '{}'", chunkX, chunkZ,
                        r.lineage().fullName(), settlement.name());
                updated.add(Lifecycle.demote(level, manager, r));
                changed = true;
            } else {
                updated.add(r);
            }
        }
        return changed ? SettlementManager.withResidents(settlement, updated) : settlement;
    }

    public void forget(UUID settlementId) {
        promoting.remove(settlementId);
        pendingDemote.remove(settlementId);
        heldVirtual.remove(settlementId);
    }

    /** Keeps a settlement virtual until something explicitly releases it. */
    public void hold(UUID settlementId) {
        promoting.remove(settlementId);
        heldVirtual.add(settlementId);
    }

    public void release(UUID settlementId) {
        heldVirtual.remove(settlementId);
    }

    public boolean isHeld(UUID settlementId) {
        return heldVirtual.contains(settlementId);
    }

    public void reset() {
        promoting.clear();
        pendingDemote.clear();
        heldVirtual.clear();
    }

    public @Nullable PromotionTask promotionOf(UUID settlementId) {
        return promoting.get(settlementId);
    }

    public void beginPromotion(UUID settlementId) {
        heldVirtual.remove(settlementId);
        promoting.put(settlementId, new PromotionTask(settlementId));
    }
}
