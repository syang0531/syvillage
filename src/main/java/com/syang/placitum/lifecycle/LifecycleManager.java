package com.syang.placitum.lifecycle;

import com.syang.placitum.Placitum;
import com.syang.placitum.defense.DefenseTick;
import com.syang.placitum.settlement.AnchorScan;
import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.data.SimClock;
import com.syang.placitum.sim.SimParams;
import com.syang.placitum.sim.Simulation;
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

        if (settlement.materializedCount() > 0) {
            settlement = defenceTick(level, manager, settlement);
        }

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
     * Everything that only makes sense while a settlement has bodies.
     *
     * <p>Anchors are re-scanned here, not during simulation: finding beds means reading POIs,
     * and POIs need loaded chunks. This is the one place where chunks are guaranteed loaded.
     */
    private Settlement defenceTick(ServerLevel level, SettlementManager manager, Settlement settlement) {
        long now = level.getGameTime();
        Settlement out = settlement;

        if (out.anchors().staleAt(now, PlacitumConfig.ANCHOR_REFRESH_TICKS.get())) {
            out = out.withAnchors(AnchorScan.scan(level, out));
            manager.put(out);
        }
        out = DefenseTick.run(level, manager, out);
        if (out != settlement) {
            manager.put(out);
        }
        return out;
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
        return skipTimeSpentMaterialized(
                SettlementManager.withResidents(settlement, updated), level.getGameTime());
    }

    /**
     * Settles the virtual stretch before anybody gets a body.
     *
     * <p>Catch-up has to happen while the residents are still VIRTUAL, because the modules skip
     * anyone who is materialized while settlement-wide consumption still counts every mouth.
     * Settle it a moment too late and the whole absence is simulated as though nobody worked
     * through it - the food a village earned while you were away is spent instead of banked.
     *
     * <p>Promote already does this. The path that did not was the quiet one: walk back and the
     * saved villagers load from their own chunks and rebind themselves, so materializedCount is
     * never zero, no promotion task ever starts, and the absence sits unsettled until the next
     * command happens to trigger it - by which time everyone has a body again.
     */
    public static Settlement settleBeforeMaterializing(ServerLevel level, Settlement settlement) {
        if (settlement.materializedCount() > 0) {
            return settlement;   // the first one through already settled it
        }
        return Simulation.catchUp(level.getServer().overworld().getSeed(), settlement,
                SimParams.fromConfig(level), level.getGameTime());
    }

    /**
     * Moves the clock past the stretch the settlement spent with bodies.
     *
     * <p>While residents are MATERIALIZED they live as entities and the virtual modules skip
     * them - but settlement-wide consumption still counts every mouth. Leaving that stretch on
     * the clock means the next catch-up simulates it as though nobody had been working, so a
     * village quietly starves in proportion to how long the player stood in it. The longer you
     * care for it, the worse it does.
     *
     * <p>The time is skipped, not simulated: what happened during it already happened, in the
     * world, to real entities, and was captured on write-back.
     *
     * <p>Skipped in whole steps so the remainder still carries and slice-independence holds.
     */
    public static Settlement skipTimeSpentMaterialized(Settlement settlement, long now) {
        if (settlement.materializedCount() > 0) {
            return settlement;   // someone is still up; the clock keeps its place
        }
        int stepTicks = PlacitumConfig.STEP_TICKS.get();
        long elapsed = now - settlement.lastSimTick();
        long whole = elapsed / stepTicks * stepTicks;
        if (whole <= 0) {
            return settlement;
        }
        Placitum.LOGGER.debug("Skipping {} tick(s) spent materialized in '{}'", whole,
                settlement.name());
        return settlement.withClock(settlement.clock().skipped(whole));
    }

    /**
     * Unregistering: every villager keeps its body and stops being ours.
     *
     * <p>The entities outlive the settlement. That is the whole point of the command.
     */
    public static void releaseAll(ServerLevel level, SettlementManager manager, Settlement settlement) {
        for (Resident r : settlement.residents()) {
            Lifecycle.release(level, manager, r);
        }
        Placitum.LOGGER.info("Released {} villager(s) of '{}' back to vanilla",
                settlement.residentCount(), settlement.name());
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
        if (!changed) {
            return settlement;
        }
        return skipTimeSpentMaterialized(
                SettlementManager.withResidents(settlement, updated), level.getGameTime());
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
