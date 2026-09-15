package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The whole state of one settlement.
 *
 * <p>Split into sub-records because {@code RecordCodecBuilder.group()} accepts at most 16
 * fields (DataFixerUpper 10.0.21 tops out at {@code Products$P16}). Flat, this record has 24
 * components and no codec could be written for it at all. The split follows document
 * boundaries: {@link DefenseState} belongs to docs/defense.md, {@link SimClock} to
 * docs/simulation.md.
 *
 * <p>There is one slot left before the limit. New state goes into a sub-record, not here.
 */
public record Settlement(
        SettlementId identity,
        ScaleTier scale,
        int scaleHoldSteps,
        List<Resident> residents,
        Map<UUID, Plot> plots,
        PlotGrid grid,
        Map<Item, Integer> stock,
        List<BuildJob> buildQueue,
        List<BuildOp> pendingOps,
        DefenseState defense,
        Chronicle chronicle,
        SimClock clock,
        Ruler ruler,
        Optional<UUID> parentId,
        boolean forceLoadCore) {

    public static final Codec<Settlement> CODEC = RecordCodecBuilder.create(i -> i.group(
            SettlementId.CODEC.fieldOf("identity").forGetter(Settlement::identity),
            ScaleTier.CODEC.fieldOf("scale").forGetter(Settlement::scale),
            Codec.INT.fieldOf("scale_hold_steps").forGetter(Settlement::scaleHoldSteps),
            Resident.CODEC.listOf().fieldOf("residents").forGetter(Settlement::residents),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Plot.CODEC).fieldOf("plots").forGetter(Settlement::plots),
            PlotGrid.CODEC.fieldOf("grid").forGetter(Settlement::grid),
            PlacitumCodecs.ITEM_COUNTS.fieldOf("stock").forGetter(Settlement::stock),
            BuildJob.CODEC.listOf().fieldOf("build_queue").forGetter(Settlement::buildQueue),
            BuildOp.CODEC.listOf().fieldOf("pending_ops").forGetter(Settlement::pendingOps),
            DefenseState.CODEC.fieldOf("defense").forGetter(Settlement::defense),
            Chronicle.CODEC.fieldOf("chronicle").forGetter(Settlement::chronicle),
            SimClock.CODEC.fieldOf("clock").forGetter(Settlement::clock),
            Ruler.CODEC.fieldOf("ruler").forGetter(Settlement::ruler),
            UUIDUtil.CODEC.optionalFieldOf("parent_id").forGetter(Settlement::parentId),
            Codec.BOOL.fieldOf("force_load_core").forGetter(Settlement::forceLoadCore)
    ).apply(i, Settlement::new));

    /**
     * Normalises iteration order. Residents sort by id and plots by id, so a step over either
     * produces the same sequence on every machine and in every session. Without this the
     * catch-up equivalence test fails intermittently and for reasons that look like magic.
     */
    public Settlement {
        residents = sortedResidents(residents);
        plots = sortedPlots(plots);
        stock = PlacitumCodecs.sortItems(stock);
    }

    private static List<Resident> sortedResidents(List<Resident> in) {
        List<Resident> out = new ArrayList<>(in);
        out.sort(Comparator.comparing(Resident::id));
        return Collections.unmodifiableList(out);
    }

    private static Map<UUID, Plot> sortedPlots(Map<UUID, Plot> in) {
        List<UUID> keys = new ArrayList<>(in.keySet());
        Collections.sort(keys);
        Map<UUID, Plot> out = new LinkedHashMap<>();
        for (UUID key : keys) {
            out.put(key, in.get(key));
        }
        return Collections.unmodifiableMap(out);
    }

    public static Settlement founding(SettlementId identity, List<Resident> residents,
            SimClock clock, int safetyWindowDays) {
        UUID headman = residents.isEmpty() ? identity.id() : residents.getFirst().id();
        return new Settlement(
                identity,
                ScaleTier.OUTPOST,
                0,
                residents,
                Map.of(),
                PlotGrid.empty(identity.center(), ScaleTier.OUTPOST.gridSize()),
                Map.of(),
                List.of(),
                List.of(),
                DefenseState.initial(safetyWindowDays),
                Chronicle.EMPTY,
                clock,
                new Ruler.Npc(headman),
                Optional.empty(),
                false);
    }

    // Delegating accessors. s.id() is read far more often than s.identity().id().

    public UUID id() {
        return identity.id();
    }

    public String name() {
        return identity.name();
    }

    public ResourceKey<Level> dimension() {
        return identity.dimension();
    }

    public BlockPos center() {
        return identity.center();
    }

    public AlertState alert() {
        return defense.alert();
    }

    public long lastSimTick() {
        return clock.lastSimTick();
    }

    public long simStep() {
        return clock.simStep();
    }

    /** Zombified residents are still records but do not count towards population. */
    public int population() {
        int n = 0;
        for (Resident r : residents) {
            if (r.counts()) {
                n++;
            }
        }
        return n;
    }

    public int residentCount() {
        return residents.size();
    }

    public int materializedCount() {
        int n = 0;
        for (Resident r : residents) {
            if (r.materialized()) {
                n++;
            }
        }
        return n;
    }

    public Optional<Resident> resident(UUID residentId) {
        for (Resident r : residents) {
            if (r.id().equals(residentId)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    public int stockOf(Item item) {
        return stock.getOrDefault(item, 0);
    }

    public int bedCount() {
        int n = 0;
        for (Plot p : plots.values()) {
            n += p.bedCount();
        }
        return n;
    }
}
