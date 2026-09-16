package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A settlement: a bell, the ground around it, and what has been built on it.
 *
 * <p>No residents. Villagers are vanilla's - it decides their professions from workstations,
 * breeds them from beds and food, and kills them when something gets in. Every one of those was
 * simulated here once, and the simulation is what made the mod impossible to balance and
 * invisible to play. See docs/why-the-reset.md.
 *
 * <p>What is left is the part a player can see: a village that builds itself roads, lamps,
 * houses and fields, one block at a time, while they watch.
 */
public record Settlement(
        SettlementId identity,
        PlotGrid grid,
        Map<UUID, Plot> plots,
        List<BuildJob> buildQueue,
        List<BuildOp> pendingOps,
        Chronicle chronicle) {

    public static final Codec<Settlement> CODEC = RecordCodecBuilder.create(i -> i.group(
            SettlementId.CODEC.fieldOf("identity").forGetter(Settlement::identity),
            PlotGrid.CODEC.fieldOf("grid").forGetter(Settlement::grid),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Plot.CODEC).fieldOf("plots")
                    .forGetter(Settlement::plots),
            BuildJob.CODEC.listOf().fieldOf("build_queue").forGetter(Settlement::buildQueue),
            BuildOp.CODEC.listOf().optionalFieldOf("pending_ops", List.of())
                    .forGetter(Settlement::pendingOps),
            Chronicle.CODEC.fieldOf("chronicle").forGetter(Settlement::chronicle)
    ).apply(i, Settlement::new));

    /**
     * Normalises iteration order, so plots are walked the same way on every machine.
     *
     * <p>Determinism matters less than it did without a simulation to keep reproducible, but a
     * build that picks a different site depending on HashMap ordering is still a build nobody
     * can test.
     */
    public Settlement {
        plots = sorted(plots);
        buildQueue = List.copyOf(buildQueue);
        pendingOps = List.copyOf(pendingOps);
    }

    private static Map<UUID, Plot> sorted(Map<UUID, Plot> in) {
        List<UUID> keys = new ArrayList<>(in.keySet());
        Collections.sort(keys);
        Map<UUID, Plot> out = new LinkedHashMap<>();
        for (UUID key : keys) {
            out.put(key, in.get(key));
        }
        return Collections.unmodifiableMap(out);
    }

    public static Settlement founding(SettlementId identity) {
        return new Settlement(identity,
                PlotGrid.empty(identity.center(), PlotGrid.sizeForClaim(identity.claimRadiusChunks())),
                Map.of(), List.of(), List.of(), Chronicle.EMPTY);
    }

    public UUID id() {
        return identity.id();
    }

    public String name() {
        return identity.name();
    }

    public BlockPos center() {
        return identity.center();
    }

    public ResourceKey<Level> dimension() {
        return identity.dimension();
    }

    public int houseCount() {
        int n = 0;
        for (Plot plot : plots.values()) {
            if (plot.kind() == PlotKind.HOUSE) {
                n++;
            }
        }
        return n;
    }

    // Copy helpers. Callers do not rebuild the record by hand.

    public Settlement withGrid(PlotGrid newGrid) {
        return new Settlement(identity, newGrid, plots, buildQueue, pendingOps, chronicle);
    }

    public Settlement withPlots(Map<UUID, Plot> newPlots) {
        return new Settlement(identity, grid, newPlots, buildQueue, pendingOps, chronicle);
    }

    public Settlement withBuildQueue(List<BuildJob> newQueue) {
        return new Settlement(identity, grid, plots, newQueue, pendingOps, chronicle);
    }

    public Settlement withPendingOps(List<BuildOp> newOps) {
        return new Settlement(identity, grid, plots, buildQueue, newOps, chronicle);
    }

    public Settlement withChronicle(Chronicle newChronicle) {
        return new Settlement(identity, grid, plots, buildQueue, pendingOps, newChronicle);
    }

    public Settlement record(EntryType type, String subject, String detail, long gameTime) {
        return withChronicle(chronicle.with(new ChronicleEntry(gameTime, type, subject, detail)));
    }
}
