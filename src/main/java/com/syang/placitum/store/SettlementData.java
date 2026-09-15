package com.syang.placitum.store;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.Settlement;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * One settlement in its own save file.
 *
 * <p>Stored in the overworld's data storage regardless of which dimension the settlement is
 * in. Settlement state is pure data, so reading or writing it must never require a level - let
 * alone a loaded chunk - in that dimension.
 */
public class SettlementData extends SavedData {

    private @Nullable Settlement settlement;

    public SettlementData() {}

    private SettlementData(Settlement settlement) {
        this.settlement = settlement;
    }

    public static SavedDataType<SettlementData> typeFor(UUID id) {
        return new SavedDataType<>(
                Identifier.fromNamespaceAndPath(Placitum.MODID, "village_" + id.toString().replace('-', '_')),
                SettlementData::new,
                Settlement.CODEC.xmap(SettlementData::new, SettlementData::settlementOrThrow));
    }

    public @Nullable Settlement settlement() {
        return settlement;
    }

    public Settlement settlementOrThrow() {
        if (settlement == null) {
            throw new IllegalStateException("SettlementData saved before a settlement was set");
        }
        return settlement;
    }

    public void set(Settlement next) {
        this.settlement = next;
        setDirty();
    }
}
