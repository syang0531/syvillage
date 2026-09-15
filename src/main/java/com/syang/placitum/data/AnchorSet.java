package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;

/**
 * The places a settlement uses: where to shelter, where to watch from, where to gather.
 *
 * <p>Defence (M1) and population (M2) both need these, and by design they come from the plot
 * grid - which construction does not fill until M3. Reading them through one record instead
 * lets the source change underneath without rewriting either milestone.
 *
 * <p>Until M3 they are derived from what a registered vanilla village already has: claimed
 * beds, and the bell. Afterwards they come from plots. Nothing upstream has to know which.
 *
 * <p><b>Cached, not queried.</b> Finding these means reading POIs, which needs loaded chunks,
 * which principle 2 forbids during virtual simulation. So they are scanned while the settlement
 * has bodies and stored; the virtual side reads the stored copy, however stale. A slightly old
 * shelter list is a much smaller problem than a settlement that cannot be simulated without
 * loading its chunks.
 */
public record AnchorSet(
        List<BlockPos> shelters,
        List<BlockPos> watchPoints,
        Optional<BlockPos> muster,
        int bedCount,
        long refreshedAt) {

    public static final Codec<AnchorSet> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.listOf().fieldOf("shelters").forGetter(AnchorSet::shelters),
            BlockPos.CODEC.listOf().fieldOf("watch_points").forGetter(AnchorSet::watchPoints),
            BlockPos.CODEC.optionalFieldOf("muster").forGetter(AnchorSet::muster),
            Codec.INT.optionalFieldOf("bed_count", 0).forGetter(AnchorSet::bedCount),
            Codec.LONG.optionalFieldOf("refreshed_at", 0L).forGetter(AnchorSet::refreshedAt)
    ).apply(i, AnchorSet::new));

    public static final AnchorSet EMPTY =
            new AnchorSet(List.of(), List.of(), Optional.empty(), 0, 0L);

    public boolean isEmpty() {
        return shelters.isEmpty() && watchPoints.isEmpty() && muster.isEmpty();
    }

    /** Nearest place to get indoors. Empty when the settlement has nowhere to shelter at all. */
    public Optional<BlockPos> nearestShelter(BlockPos from) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos shelter : shelters) {
            double dist = shelter.distSqr(from);
            if (dist < bestDist) {
                bestDist = dist;
                best = shelter;
            }
        }
        return Optional.ofNullable(best);
    }

    public boolean staleAt(long now, long maxAgeTicks) {
        return refreshedAt == 0L || now - refreshedAt > maxAgeTicks;
    }
}
