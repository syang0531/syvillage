package com.syang.placitum.store;

import com.syang.placitum.data.AlertState;
import com.syang.placitum.data.BuildJob;
import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.Chronicle;
import com.syang.placitum.data.ChronicleEntry;
import com.syang.placitum.data.DefenseState;
import com.syang.placitum.data.EntryType;
import com.syang.placitum.data.PlacitumCodecs;
import com.syang.placitum.data.Plot;
import com.syang.placitum.data.PlotGrid;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.Ruler;
import com.syang.placitum.data.ScaleTier;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.data.SettlementId;
import com.syang.placitum.data.SimClock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.item.Item;

/**
 * Mutable mirror of a {@link Settlement}, alive for the duration of a simulation step.
 *
 * <p>Rebuilding the immutable record on every field change would allocate furiously at a few
 * thousand residents. This is allocation avoidance, not a retreat from the immutability rule:
 * the mirror never leaves the store package, and {@link #freeze()} is the only way out.
 */
public final class SettlementMut {

    public SettlementId identity;
    public ScaleTier scale;
    public int scaleHoldSteps;
    public final List<Resident> residents;
    public final Map<UUID, Plot> plots;
    public PlotGrid grid;
    public final Map<Item, Integer> stock;
    public final List<BuildJob> buildQueue;
    public final List<BuildOp> pendingOps;
    public DefenseState defense;
    public SimClock clock;
    public Ruler ruler;
    public Optional<UUID> parentId;
    public boolean forceLoadCore;

    /** Events raised during this step. Appended to the chronicle by {@link #freeze()}. */
    public final List<ChronicleEntry> newEntries = new ArrayList<>();

    private Chronicle chronicle;

    private SettlementMut(Settlement s) {
        this.identity = s.identity();
        this.scale = s.scale();
        this.scaleHoldSteps = s.scaleHoldSteps();
        this.residents = new ArrayList<>(s.residents());
        this.plots = new LinkedHashMap<>(s.plots());
        this.grid = s.grid();
        this.stock = new LinkedHashMap<>(s.stock());
        this.buildQueue = new ArrayList<>(s.buildQueue());
        this.pendingOps = new ArrayList<>(s.pendingOps());
        this.defense = s.defense();
        this.clock = s.clock();
        this.ruler = s.ruler();
        this.parentId = s.parentId();
        this.forceLoadCore = s.forceLoadCore();
        this.chronicle = s.chronicle();
    }

    public static SettlementMut of(Settlement s) {
        return new SettlementMut(s);
    }

    public Settlement freeze() {
        Chronicle out = chronicle;
        for (ChronicleEntry entry : newEntries) {
            out = out.with(entry);
        }
        newEntries.clear();
        chronicle = out;
        // The canonical constructor re-sorts residents, plots and stock, so iteration order is
        // restored even if a module appended in arbitrary order.
        return new Settlement(identity, scale, scaleHoldSteps, residents, plots, grid,
                PlacitumCodecs.sortItems(stock), buildQueue, pendingOps, defense, out, clock,
                ruler, parentId, forceLoadCore);
    }

    public UUID id() {
        return identity.id();
    }

    public long simStep() {
        return clock.simStep();
    }

    public long lastSimTick() {
        return clock.lastSimTick();
    }

    public AlertState alert() {
        return defense.alert();
    }

    public void record(EntryType type, String subject, String detail) {
        newEntries.add(new ChronicleEntry(clock.lastSimTick(), type, subject, detail));
    }

    /** Head count excluding the zombified, who are records but not people for now. */
    public int population() {
        int n = 0;
        for (Resident r : residents) {
            if (r.counts()) {
                n++;
            }
        }
        return n;
    }

    /** True while any resident has a body, which means elapsed time is not virtual time. */
    public boolean anyMaterialized() {
        for (Resident r : residents) {
            if (r.materialized()) {
                return true;
            }
        }
        return false;
    }

    public int stockOf(Item item) {
        return stock.getOrDefault(item, 0);
    }

    public void addStock(Item item, int amount) {
        int next = stockOf(item) + amount;
        if (next <= 0) {
            stock.remove(item);
        } else {
            stock.put(item, next);
        }
    }

    /** Removes up to {@code amount} and reports what was actually taken. */
    public int takeStock(Item item, int amount) {
        int have = stockOf(item);
        int taken = Math.min(have, Math.max(0, amount));
        if (taken > 0) {
            addStock(item, -taken);
        }
        return taken;
    }
}
