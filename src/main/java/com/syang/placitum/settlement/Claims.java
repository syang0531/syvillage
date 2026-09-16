package com.syang.placitum.settlement;

import com.syang.placitum.data.AnchorSet;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.store.SettlementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Which settlement, if any, owns a position.
 *
 * <p>Exists so that things happening in the world can find the settlement they happened to
 * rather than the settlement having to go looking. A player places a bed, a villager wanders in:
 * the event knows where it was, and that is the cheap direction to search from.
 */
public final class Claims {

    private Claims() {}

    public static @Nullable Settlement at(SettlementManager manager, ResourceKey<Level> dimension,
            BlockPos pos) {
        for (Settlement settlement : manager.all()) {
            if (!settlement.dimension().equals(dimension)) {
                continue;
            }
            int radius = settlement.identity().claimRadiusChunks() * 16;
            if (settlement.center().closerThan(pos, radius)) {
                return settlement;
            }
        }
        return null;
    }

    /**
     * Marks a settlement's picture of the world out of date, so the next tick re-reads it.
     *
     * <p>The anchor scan and the plot survey are on a five-minute timer, which is right for a
     * village nobody is touching and wrong the moment somebody is. A player who places a bed and
     * watches nothing happen for five minutes has no way to tell a slow refresh from a broken
     * one - and this project has already paid several times over for exactly that ambiguity.
     */
    public static Settlement stale(Settlement settlement) {
        AnchorSet anchors = settlement.anchors();
        return settlement.withAnchors(new AnchorSet(anchors.shelters(), anchors.watchPoints(),
                anchors.muster(), anchors.bedCount(), 0L));
    }
}
