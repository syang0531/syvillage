package com.syang.placitum;

import com.syang.placitum.data.Assignment;
import com.syang.placitum.data.BuildJob;
import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.BuildStage;
import com.syang.placitum.data.CellPos;
import com.syang.placitum.data.CellState;
import com.syang.placitum.data.AnchorSet;
import com.syang.placitum.data.Chronicle;
import com.syang.placitum.data.ChronicleEntry;
import com.syang.placitum.data.DefenseState;
import com.syang.placitum.data.EntryType;
import com.syang.placitum.data.GateNode;
import com.syang.placitum.data.GearSet;
import com.syang.placitum.data.LifeStage;
import com.syang.placitum.data.Lineage;
import com.syang.placitum.data.Plot;
import com.syang.placitum.data.PlotGrid;
import com.syang.placitum.data.PlotKind;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.ResidentState;
import com.syang.placitum.data.ResidentTask;
import com.syang.placitum.data.Ruler;
import com.syang.placitum.data.ScaleState;
import com.syang.placitum.data.ScaleTier;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.data.SettlementId;
import com.syang.placitum.data.SimClock;
import com.syang.placitum.data.Vitals;
import com.syang.placitum.data.WallState;
import com.syang.placitum.data.WallTier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

/**
 * Settlements for tests.
 *
 * <p>Every field is deliberately set to something other than its default. A fixture full of
 * zeroes and empty lists would round-trip perfectly while a real settlement leaked, which is
 * the exact failure the round-trip test exists to prevent.
 */
public final class SettlementFixture {

    public static final long SEED = 0x5EEDL;
    public static final long START_TICK = 1_000_000L;

    private SettlementFixture() {}

    /** Deterministic ids, so a failing test reproduces exactly. */
    public static UUID id(int n) {
        return new UUID(0xA11CE0000L + n, 0xB0B0000L + n);
    }

    public static SettlementId identity() {
        return new SettlementId(id(0), "Hearthwood", Level.OVERWORLD, new BlockPos(112, 68, -304), 5);
    }

    public static Resident resident(int n, ResidentState state) {
        // The vanilla blob is stored opaquely, so the fixture can fill it with a stand-in
        // rather than real MerchantOffers and VillagerData. That is deliberate: since 26.2 item
        // data components bind at datapack reload, an ItemStack cannot be built in a unit test.
        CompoundTag vanillaState = new CompoundTag();
        if (n % 2 == 0) {
            CompoundTag inner = new CompoundTag();
            inner.putString("stub", "offer-" + n);
            inner.putInt("uses", 3 + n);
            vanillaState.put("offers", inner);
            CompoundTag data = new CompoundTag();
            data.putString("profession", "minecraft:farmer");
            data.putInt("level", 2);
            vanillaState.put("villager_data", data);
        }
        return new Resident(
                id(100 + n),
                new Lineage("Johann" + n, "Bernhardt", Optional.of(id(900)), Optional.of(id(901))),
                n % 3 == 0 ? LifeStage.ELDER : LifeStage.ADULT,
                25 + n,
                new Assignment(n % 2 == 0 ? Assignment.FARMER : Assignment.BUILDER,
                        Optional.of(id(200)), Optional.of(id(201))),
                new Vitals(18 - n % 4, 40 + n, 55 + n),
                n % 2 == 0,
                n == 4,
                new GearSet(Optional.of(Items.IRON_SWORD), Optional.of(Items.LEATHER_HELMET),
                        Optional.empty(), Optional.empty(), Optional.of(Items.LEATHER_BOOTS), 12 + n),
                new ResidentTask(Identifier.fromNamespaceAndPath("placitum", "harvest"), 7 + n,
                        Optional.of(id(300))),
                new BlockPos(112 + n, 68, -304 + n),
                state,
                vanillaState);
    }

    public static List<Resident> residents(int count, ResidentState state) {
        List<Resident> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(resident(i, state));
        }
        return out;
    }

    /** A settlement with every field populated and nothing left at its default. */
    public static Settlement full(int residentCount, ResidentState state) {
        Map<CellPos, CellState> cells = new LinkedHashMap<>();
        cells.put(new CellPos(0, 0), CellState.ROAD);
        cells.put(new CellPos(1, 0), CellState.BUILT);
        cells.put(new CellPos(-1, 2), CellState.BLOCKED);
        cells.put(new CellPos(2, -1), CellState.RESERVED);

        Map<UUID, Plot> plots = new LinkedHashMap<>();
        plots.put(id(200), new Plot(id(200), new CellPos(1, 0), 2, 1, Rotation.CLOCKWISE_90,
                Identifier.fromNamespaceAndPath("placitum", "house/cottage_2x1"),
                PlotKind.HOUSE, 3, List.of(id(100), id(101))));
        plots.put(id(201), new Plot(id(201), new CellPos(2, -1), 1, 1, Rotation.NONE,
                Identifier.fromNamespaceAndPath("placitum", "farm/field_1x1"),
                PlotKind.FARM, 0, List.of()));

        Map<net.minecraft.world.item.Item, Integer> stock = new LinkedHashMap<>();
        stock.put(Items.WHEAT, 340);
        stock.put(Items.OAK_LOG, 96);
        stock.put(Items.IRON_INGOT, 7);

        BuildJob job = new BuildJob(id(400), id(201),
                new BuildRecipe(Identifier.fromNamespaceAndPath("placitum", "wall/palisade"),
                        new BlockPos(120, 68, -300), Rotation.COUNTERCLOCKWISE_90,
                        Identifier.fromNamespaceAndPath("placitum", "biome_palette/plains"),
                        List.of(68, 69, 69, 70, 71)),
                42, Map.of(Items.OAK_LOG, 64), BuildStage.EXECUTING);

        WallState wall = new WallState(WallTier.PALISADE,
                List.of(new BlockPos(100, 68, -300), new BlockPos(101, 68, -300)),
                List.of(new GateNode(new BlockPos(104, 68, -300), Direction.NORTH, false)),
                true);

        DefenseState defense = new DefenseState(
                com.syang.placitum.data.AlertState.ALERT, 999_000L, wall, 61,
                List.of(0, 2, 0, 1, 0, 0, 3),
                11,
                true);

        Chronicle chronicle = Chronicle.EMPTY
                .with(new ChronicleEntry(900_000L, EntryType.BUILD, "Smithy", "completed"))
                .with(new ChronicleEntry(950_000L, EntryType.DEATH, "Johann Bernhardt", "killed at the gate"));

        return new Settlement(
                identity(),
                new ScaleState(ScaleTier.VILLAGE, 17),
                residents(residentCount, state),
                plots,
                new PlotGrid(new BlockPos(112, 68, -304), 9, cells),
                stock,
                List.of(job),
                List.of(new BuildOp(new BlockPos(118, 69, -302), Blocks.OAK_FENCE.defaultBlockState())),
                defense,
                new AnchorSet(
                        List.of(new BlockPos(110, 68, -300), new BlockPos(118, 68, -308)),
                        List.of(new BlockPos(112, 68, -304), new BlockPos(140, 70, -280)),
                        Optional.of(new BlockPos(112, 68, -304)),
                        5,
                        990_000L),
                chronicle,
                new SimClock(START_TICK, 4321L),
                new Ruler.Npc(id(100)),
                Optional.of(id(777)),
                true);
    }

    public static Settlement standard() {
        return full(6, ResidentState.VIRTUAL);
    }

    /**
     * A settlement as M2 actually finds one: an adopted vanilla village, with beds and no plots.
     *
     * <p>{@link #full} is built for the round-trip test and so has a plot with three beds and a
     * loaded casualty ring, which together hold its carrying capacity below its population. That
     * is the right shape for proving nothing leaks through a Codec and the wrong shape entirely
     * for asking whether a village can grow - a village that is already over capacity is
     * supposed to have no children, so the growth test would pass while proving nothing.
     *
     * @param beds how many the vanilla village came with
     */
    public static Settlement adopted(int residentCount, int beds) {
        Settlement s = full(residentCount, ResidentState.VIRTUAL);
        AnchorSet anchors = new AnchorSet(s.anchors().shelters(), s.anchors().watchPoints(),
                s.anchors().muster(), beds, s.anchors().refreshedAt());
        DefenseState peaceful = new DefenseState(s.defense().alert(), s.defense().alertSince(),
                s.defense().wall(), s.defense().lightingScore(),
                List.of(0, 0, 0, 0, 0, 0, 0),   // nobody has died here recently
                s.defense().famineSteps(), s.defense().foodWarned());
        return new Settlement(s.identity(), s.scaleState(), s.residents(),
                Map.of(),   // no plots: construction is M3
                s.grid(), s.stock(), s.buildQueue(), s.pendingOps(), peaceful, anchors,
                s.chronicle(), s.clock(), s.ruler(), s.parentId(), s.forceLoadCore());
    }
}
