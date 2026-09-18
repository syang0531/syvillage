package com.syang.placitum;

import com.syang.placitum.data.CellPos;
import com.syang.placitum.data.CellState;
import com.syang.placitum.data.Chronicle;
import com.syang.placitum.data.Craft;
import com.syang.placitum.data.ChronicleEntry;
import com.syang.placitum.data.EntryType;
import com.syang.placitum.data.Plot;
import com.syang.placitum.data.PlotGrid;
import com.syang.placitum.data.PlotKind;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.data.Stage;
import com.syang.placitum.data.SettlementId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

/**
 * Settlements for tests.
 *
 * <p>A great deal smaller than it was. It used to build residents with names, ages, morale,
 * hunger, gear and an opaque blob of vanilla NBT, because the mod kept its own copy of every
 * villager; none of that exists now. See docs/why-the-reset.md.
 */
public final class SettlementFixture {

    public static final long SEED = 0x5EEDL;

    private SettlementFixture() {}

    /** Deterministic ids, so a failing test reproduces exactly. */
    public static UUID id(int n) {
        return new UUID(0xA11CE0000L + n, 0xB0B0000L + n);
    }

    public static SettlementId identity() {
        return new SettlementId(id(0), "Hearthwood", Level.OVERWORLD,
                new BlockPos(112, 68, -304), 5);
    }

    /** A settlement with something in every field, so nothing round-trips by being empty. */
    public static Settlement standard() {
        Map<CellPos, CellState> cells = new LinkedHashMap<>();
        cells.put(new CellPos(0, 0), CellState.ROAD);
        cells.put(new CellPos(1, 0), CellState.BUILT);
        cells.put(new CellPos(-1, 2), CellState.BLOCKED);
        cells.put(new CellPos(2, -1), CellState.FORBIDDEN);

        Map<UUID, Plot> plots = new LinkedHashMap<>();
        plots.put(id(200), new Plot(id(200), new CellPos(1, 0), 1, 1, Rotation.CLOCKWISE_90,
                Identifier.fromNamespaceAndPath("placitum", "house/cottage"),
                PlotKind.HOUSE, 2, List.of()));

        Chronicle chronicle = Chronicle.EMPTY
                .with(new ChronicleEntry(900_000L, EntryType.BUILD, "Hearthwood",
                        "finished a road/cross"));

        return new Settlement(identity(),
                new PlotGrid(new BlockPos(112, 68, -304), 9, cells),
                plots, List.of(), Craft.PLAINS, Stage.WALLED, chronicle);
    }

    /** A settlement that has only just been registered: a bell and unread ground. */
    public static Settlement founded() {
        return Settlement.founding(identity(), Craft.PLAINS);
    }
}
