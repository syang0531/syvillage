package com.syang.placitum.store;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.syang.placitum.Placitum;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.data.SettlementId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The list of registered settlements: id, name, centre, dimension. Nothing else.
 *
 * <p>Kept separate from the settlements themselves because SavedData's dirty flag is
 * per-file. One index plus one file per settlement means a change to a single village
 * rewrites a single village, not the whole world's worth of them.
 */
public class SettlementIndex extends SavedData {

    public static final SavedDataType<SettlementIndex> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Placitum.MODID, "index"),
            SettlementIndex::new,
            codec());

    private final Map<UUID, SettlementId> entries = new LinkedHashMap<>();

    public SettlementIndex() {}

    private SettlementIndex(List<SettlementId> loaded) {
        for (SettlementId entry : loaded) {
            entries.put(entry.id(), entry);
        }
    }

    private static Codec<SettlementIndex> codec() {
        return RecordCodecBuilder.create(i -> i.group(
                SettlementId.CODEC.listOf().fieldOf("settlements").forGetter(SettlementIndex::sortedEntries)
        ).apply(i, SettlementIndex::new));
    }

    /** Sorted so the saved file is stable and diffs stay readable. */
    public List<SettlementId> sortedEntries() {
        List<SettlementId> out = new ArrayList<>(entries.values());
        out.sort((a, b) -> a.id().compareTo(b.id()));
        return out;
    }

    public Map<UUID, SettlementId> entries() {
        return Collections.unmodifiableMap(entries);
    }

    public void put(SettlementId entry) {
        entries.put(entry.id(), entry);
        setDirty();
    }

    public void remove(UUID id) {
        if (entries.remove(id) != null) {
            setDirty();
        }
    }

    public boolean contains(UUID id) {
        return entries.containsKey(id);
    }

    public void refresh(Settlement settlement) {
        put(settlement.identity());
    }
}
