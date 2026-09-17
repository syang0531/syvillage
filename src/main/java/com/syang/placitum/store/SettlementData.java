package com.syang.placitum.store;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
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
                strictCodec(id));
    }

    /**
     * Refuses a partially decoded settlement.
     *
     * <p>{@code SavedDataStorage} reads with {@code resultOrPartial}, so a codec error does not
     * stop the load - it logs and hands back whatever decoded. DataFixerUpper's list and map
     * codecs drop the elements they could not read, so one bad field silently returns a
     * settlement with no plots on it. Load that, touch it once, and the next save writes the
     * emptiness over the real file.
     *
     * <p>It happened: a field rename evaporated a settlement's whole roster behind a single
     * ERROR line. "Everything is gone and nobody knows why" is the exact failure this mod exists
     * to end, so it may not be how the mod itself fails.
     *
     * <p>Returning an error with no partial value makes the storage layer hand back null. The
     * file on disk is then left untouched and stays recoverable once the codec is fixed.
     */
    private static Codec<SettlementData> strictCodec(UUID id) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<SettlementData, T>> decode(DynamicOps<T> ops, T input) {
                DataResult<Pair<Settlement, T>> parsed = Settlement.CODEC.decode(ops, input);
                if (parsed.isError()) {
                    String message = parsed.error().map(DataResult.Error::message).orElse("unknown");
                    Placitum.LOGGER.error(
                            "Settlement {} did not decode cleanly and will NOT be loaded. The file is "
                                    + "left untouched, so fixing the codec recovers it. Cause: {}",
                            id, message);
                    return DataResult.error(() -> "Refusing a partially decoded settlement: " + message);
                }
                return parsed.map(pair -> pair.mapFirst(SettlementData::new));
            }

            @Override
            public <T> DataResult<T> encode(SettlementData input, DynamicOps<T> ops, T prefix) {
                return Settlement.CODEC.encode(input.settlementOrThrow(), ops, prefix);
            }
        };
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
