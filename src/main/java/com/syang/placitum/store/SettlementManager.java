package com.syang.placitum.store;

import com.syang.placitum.Placitum;
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
 * <p>The index stays in memory; individual settlements load on first touch.
 */
public final class SettlementManager {

    private static @Nullable SettlementManager current;

    private final MinecraftServer server;
    private final SettlementIndex index;
    private final Map<UUID, Settlement> loaded = new LinkedHashMap<>();

    /** Settlements already reported as unreadable, so the log says it once. */
    private final java.util.Set<UUID> unreadable = new java.util.HashSet<>();


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

    public static String shortId(UUID id) {
        return id.toString().substring(0, 8);
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
            // Either genuinely empty or refused by the strict codec. Either way it stays out of
            // memory: a settlement that cannot be read is not the same as a settlement that is
            // empty, and treating them alike is how the file gets overwritten with nothing.
            //
            // Reported once. Every tick that touches the settlement list comes through here, so
            // the first run of this printed the same line 1147 times and buried everything else
            // in the log - including whatever the player actually needed to see.
            if (unreadable.add(id)) {
                Placitum.LOGGER.error("Settlement {} is indexed but could not be read. It will be "
                        + "skipped until this is fixed; the file has not been modified.", shortId(id));
            }
            return Optional.empty();
        }
        unreadable.remove(id);
        loaded.put(id, settlement);
        // Logged because this is the only visible evidence that a settlement survived a
        // restart: it gets read back from disk the first time anything touches it.
        Placitum.LOGGER.info("Loaded settlement {} '{}' from disk - {} house(s)",
                shortId(id), settlement.name(), settlement.houseCount());
        return Optional.of(settlement);
    }

    /**
     * Forgets a settlement. The buildings stay standing.
     *
     * <p>Nothing is handed back, because nothing was taken: the villagers were never ours and
     * the blocks are just blocks.
     */
    public void remove(UUID id) {
        loaded.remove(id);
        unreadable.remove(id);
        index.remove(id);
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
}
