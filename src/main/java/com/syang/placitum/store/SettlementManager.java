package com.syang.placitum.store;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.ResidentState;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.data.SettlementId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.jspecify.annotations.Nullable;

/**
 * Owns every settlement for one server.
 *
 * <p>The index stays in memory; individual settlements load on first touch. The
 * resident-to-entity binding is deliberately NOT saved - entity UUIDs are a per-session
 * detail, and persisting one would invert principle 1 by making the data point at the entity.
 */
public final class SettlementManager {

    private static @Nullable SettlementManager current;

    private final MinecraftServer server;
    private final SettlementIndex index;
    private final Map<UUID, Settlement> loaded = new LinkedHashMap<>();

    /** residentId -> entity UUID. Runtime only; empty at boot. */
    private final Map<UUID, UUID> residentToEntity = new HashMap<>();
    private final Map<UUID, UUID> entityToResident = new HashMap<>();

    private SettlementManager(MinecraftServer server) {
        this.server = server;
        this.index = storage(server).computeIfAbsent(SettlementIndex.TYPE);
    }

    public static SettlementManager get(MinecraftServer server) {
        SettlementManager manager = current;
        if (manager == null || manager.server != server) {
            manager = new SettlementManager(server);
            current = manager;
        }
        return manager;
    }

    public static @Nullable SettlementManager peek() {
        return current;
    }

    public static void clear() {
        current = null;
    }

    private static SavedDataStorage storage(MinecraftServer server) {
        return server.overworld().getDataStorage();
    }

    public MinecraftServer server() {
        return server;
    }

    public List<SettlementId> listed() {
        return index.sortedEntries();
    }

    public boolean isRegistered(UUID id) {
        return index.contains(id);
    }

    public Optional<Settlement> find(UUID id) {
        Settlement cached = loaded.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        if (!index.contains(id)) {
            return Optional.empty();
        }
        SettlementData data = storage(server).computeIfAbsent(SettlementData.typeFor(id));
        Settlement settlement = data.settlement();
        if (settlement == null) {
            Placitum.LOGGER.warn("Settlement {} is in the index but its data file is empty", id);
            return Optional.empty();
        }
        loaded.put(id, settlement);
        return Optional.of(settlement);
    }

    /** Loads every registered settlement. Used by commands and by the boot resync. */
    public List<Settlement> all() {
        List<Settlement> out = new ArrayList<>();
        for (SettlementId entry : index.sortedEntries()) {
            find(entry.id()).ifPresent(out::add);
        }
        return out;
    }

    public void put(Settlement settlement) {
        loaded.put(settlement.id(), settlement);
        index.refresh(settlement);
        storage(server).computeIfAbsent(SettlementData.typeFor(settlement.id())).set(settlement);
    }

    public void unregister(UUID id) {
        loaded.remove(id);
        index.remove(id);
        // The data file is left on disk on purpose: /placitum unregister is undo, and a
        // mis-click should not destroy a settlement's history.
    }

    // --- Entity binding (runtime only) ---

    public void bind(UUID residentId, UUID entityId) {
        residentToEntity.put(residentId, entityId);
        entityToResident.put(entityId, residentId);
    }

    public void unbind(UUID residentId) {
        UUID entityId = residentToEntity.remove(residentId);
        if (entityId != null) {
            entityToResident.remove(entityId);
        }
    }

    public @Nullable UUID entityOf(UUID residentId) {
        return residentToEntity.get(residentId);
    }

    public @Nullable UUID residentOf(UUID entityId) {
        return entityToResident.get(entityId);
    }

    public boolean isBound(UUID residentId) {
        return residentToEntity.containsKey(residentId);
    }

    public void clearBindings() {
        residentToEntity.clear();
        entityToResident.clear();
    }

    /**
     * Boot-time resync.
     *
     * <p>A crash can leave residents saved as MATERIALIZED while their entities either never
     * made it to disk or did. Either way the data is lying, so everyone starts VIRTUAL and
     * entities rebind as their chunks load.
     */
    public int resetMaterializedState() {
        clearBindings();
        int reset = 0;
        for (Settlement settlement : all()) {
            int before = settlement.materializedCount();
            if (before > 0) {
                put(allVirtual(settlement));
                reset += before;
            }
        }
        return reset;
    }

    /**
     * Pure half of the boot resync, so it can be tested without a server.
     *
     * <p>Nobody is removed. The residents are all still there - they simply have no body until
     * their chunks load and the entities rebind.
     */
    public static Settlement allVirtual(Settlement settlement) {
        List<Resident> next = new ArrayList<>();
        for (Resident r : settlement.residents()) {
            next.add(r.materialized() ? r.withState(ResidentState.VIRTUAL) : r);
        }
        return withResidents(settlement, next);
    }

    public static Settlement withResidents(Settlement s, List<Resident> residents) {
        return new Settlement(s.identity(), s.scale(), s.scaleHoldSteps(), residents, s.plots(),
                s.grid(), s.stock(), s.buildQueue(), s.pendingOps(), s.defense(), s.chronicle(),
                s.clock(), s.ruler(), s.parentId(), s.forceLoadCore());
    }
}
