package com.syang.syvillage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.syvillage.build.HousePlan;
import com.syang.syvillage.build.Houses;
import com.syang.syvillage.build.Need;
import com.syang.syvillage.build.Template;
import com.syang.syvillage.build.TownPlan;
import com.syang.syvillage.data.BuildOp;
import com.syang.syvillage.data.BuildRecipe;
import com.syang.syvillage.data.CellPos;
import com.syang.syvillage.data.Craft;
import com.syang.syvillage.data.PlotGrid;
import com.syang.syvillage.data.PlotKind;
import com.syang.syvillage.data.Settlement;
import com.syang.syvillage.data.Stage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Vanilla village buildings on our lots: that all thirty load, that each one lands inside its
 * lot with its door on the street, and that what it writes is what a structure block would
 * have placed - jigsaws resolved, doorstep where the street meets it.
 */
class HouseTest {

    private static final BlockPos BELL = new BlockPos(112, 68, -304);
    private static final int FLOOR = 68;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // The game's jar is on the test classpath, but its data packages are closed to us as a
        // module. Read the buildings straight out of the jar instead.
        java.nio.file.Path jar = null;
        for (String entry : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            if (entry.contains("minecraft-patched") && entry.endsWith(".jar")
                    && !entry.contains("sources")) {
                jar = java.nio.file.Path.of(entry);
                break;
            }
        }
        assertNotNull(jar, "no minecraft jar on the test classpath");
        for (Identifier id : Houses.all()) {
            Template.loadFromJar(jar, id);
        }
    }

    private static Settlement town(Craft craft) {
        return Settlement.founding(SettlementFixture.identity(), craft)
                .withGrid(PlotGrid.empty(BELL, 21)).withStage(Stage.HEADED);
    }

    private static Map<BlockPos, BuildOp> built(Identifier id, CellPos cell) {
        Settlement town = town(Craft.PLAINS);
        Template template = Template.of(id);
        Direction street = HousePlan.doorFacing(cell);
        Rotation rotation = HousePlan.turnTo(template.front(), street);
        BlockPos origin = HousePlan.originOf(template, rotation, cell, BELL, street);
        List<Integer> profile = new ArrayList<>();
        for (int i = 0; i < template.columns().size(); i++) {
            profile.add(FLOOR);
        }
        BuildRecipe recipe = HousePlan.recipe(town, id, origin, rotation, profile, List.of());
        Map<BlockPos, BuildOp> world = new HashMap<>();
        for (BuildOp op : HousePlan.expand(recipe)) {
            assertFalse(world.containsKey(op.pos()), id + ": two blocks fight over " + op.pos());
            world.put(op.pos(), op);
        }
        return world;
    }

    @Test
    @DisplayName("all thirty dwellings load, fit a lot, and say which way they face")
    void theCatalogueLoads() {
        assertEquals(30, Houses.all().size());
        int beds = 0;
        for (Identifier id : Houses.all()) {
            Template t = Template.of(id);
            assertTrue(t.sizeX() <= TownPlan.LOT && t.sizeZ() <= TownPlan.LOT, id + " does not fit");
            assertNotNull(t.front(), id + " has no building_entrance jigsaw");
            assertTrue(t.lift() >= -1 && t.lift() <= 1, id + " sits at " + t.lift());
            for (Template.Piece piece : t.pieces()) {
                assertFalse(piece.state().is(Blocks.JIGSAW), id + " still has a jigsaw in it");
                assertFalse(piece.state().is(Blocks.STRUCTURE_VOID), id + " has a void");
            }
            if (t.bedCount() > 0) {
                beds++;
            }
        }
        assertEquals(30, beds, "every one of them has a bed: a lot with no bed grows nothing");
        for (Craft craft : Craft.values()) {
            assertFalse(Houses.dwellings(craft).isEmpty(), craft + " has nowhere to live");
        }
    }

    @Test
    @DisplayName("every building sits inside its lot, turned so its door is on the street")
    void everyBuildingSitsOnItsLot() {
        for (Identifier id : Houses.all()) {
            for (CellPos cell : new CellPos[] {new CellPos(1, 0), new CellPos(1, 1)}) {
                Template template = Template.of(id);
                Direction street = HousePlan.doorFacing(cell);
                Rotation rotation = HousePlan.turnTo(template.front(), street);
                BlockPos origin = HousePlan.originOf(template, rotation, cell, BELL, street);
                BlockPos lot = TownPlan.lotCorner(cell, BELL);
                Set<BlockPos> lotColumns = new HashSet<>(TownPlan.lotColumns(cell, BELL));

                assertEquals(street, rotation.rotate(template.front()), id + " faces away");
                for (BlockPos column : template.columnsAt(origin, rotation)) {
                    assertTrue(lotColumns.contains(column),
                            id + " on lot " + cell.toKey() + " reaches outside it at " + column);
                }
                // The turned box is pushed against the street's edge of the lot.
                int deep = template.turnedDepth(rotation);
                int edge = street == Direction.NORTH ? lot.getZ() : lot.getZ() + TownPlan.LOT - deep;
                assertEquals(edge, origin.getZ(), id + " is not against the street");
            }
        }
    }

    @Test
    @DisplayName("a house sits where its entrance jigsaw is one above the ground")
    void itSitsWhereTheStreetWouldMeetIt() {
        // A plains house carries its entrance on layer 0: it sits a block up, doorstep stair on
        // the grass, door above that. A desert house carries it on layer 1: it sits on the
        // ground and you walk straight in. Both are what a generated village does.
        assertEquals(1, Template.of(Identifier.withDefaultNamespace(
                "village/plains/houses/plains_small_house_1")).lift());
        assertEquals(0, Template.of(Identifier.withDefaultNamespace(
                "village/desert/houses/desert_small_house_4")).lift());

        Identifier id = Identifier.withDefaultNamespace("village/plains/houses/plains_small_house_1");
        Map<BlockPos, BuildOp> world = built(id, new CellPos(1, 0));

        int lowest = Integer.MAX_VALUE;
        int doors = 0;
        int steps = 0;
        for (Map.Entry<BlockPos, BuildOp> entry : world.entrySet()) {
            if (entry.getValue().state().isAir()) {
                continue;
            }
            lowest = Math.min(lowest, entry.getKey().getY());
            if (entry.getValue().state().getBlock() instanceof DoorBlock
                    && entry.getValue().state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                doors++;
                assertEquals(FLOOR + 2, entry.getKey().getY(), "the door stands on the raised floor");
                // Which way the door itself faces is the builder's habit - inward in the plains
                // set, outward in the desert set - so the front comes from the jigsaw, not the
                // door, and is checked in everyBuildingSitsOnItsLot.
            }
            if (entry.getValue().state().getBlock() instanceof StairBlock
                    && entry.getKey().getY() == FLOOR + 1) {
                steps++;
            }
        }
        assertEquals(FLOOR + 1, lowest, "layer 0 stands on the grass, the doorstep with it");
        assertEquals(1, doors);
        assertTrue(steps >= 1, "the entrance jigsaw became its final state, a doorstep stair");
    }

    @Test
    @DisplayName("it never floats: low ground under the walls is filled to the floor")
    void itIsFounded() {
        Identifier id = Identifier.withDefaultNamespace("village/plains/houses/plains_small_house_1");
        Settlement town = town(Craft.PLAINS);
        Template template = Template.of(id);
        CellPos cell = new CellPos(1, 0);
        Rotation rotation = HousePlan.turnTo(template.front(), Direction.NORTH);
        BlockPos origin = HousePlan.originOf(template, rotation, cell, BELL, Direction.NORTH);
        List<int[]> columns = template.columns();
        List<Integer> profile = new ArrayList<>();
        int[] low = null;
        for (int[] column : columns) {
            boolean corner = template.hasBase(column[0], column[1])
                    && !template.entrances().contains(column) && low == null;
            if (corner) {
                low = column;
            }
            profile.add(column == low ? FLOOR - 3 : FLOOR);
        }
        assertNotNull(low);
        Map<BlockPos, BuildOp> world = new HashMap<>();
        for (BuildOp op : HousePlan.expand(HousePlan.recipe(town, id, origin, rotation, profile,
                List.of()))) {
            world.put(op.pos(), op);
        }
        BlockPos at = template.columnAt(origin, rotation, low[0], low[1]);
        for (int y = FLOOR - 2; y <= FLOOR - 1; y++) {
            BuildOp op = world.get(new BlockPos(at.getX(), y, at.getZ()));
            assertTrue(op != null && op.state().is(Craft.PLAINS.foundation().getBlock()),
                    "no foundation at " + y + " under " + at);
        }
    }

    @Test
    @DisplayName("the same lot gets the same dwelling every time, and a farm lot is a field")
    void thePickIsTheLots() {
        CellPos cell = new CellPos(3, -2);
        assertEquals(Houses.pick(Craft.PLAINS, Need.Kind.HOUSE, cell),
                Houses.pick(Craft.PLAINS, Need.Kind.HOUSE, cell));
        for (int gx = -5; gx <= 5; gx++) {
            for (int gz = -5; gz <= 5; gz++) {
                assertTrue(Houses.pick(Craft.DESERT, Need.Kind.FARM, new CellPos(gx, gz)).isEmpty(),
                        "a farm lot got a building instead of a field");
                assertTrue(Houses.pick(Craft.TAIGA, Need.Kind.HOUSE, new CellPos(gx, gz))
                        .map(id -> Template.of(id).bedCount() > 0).orElse(false),
                        "a house lot got something without a bed");
            }
        }
        assertEquals(PlotKind.HOUSE, Houses.kindOf(
                Identifier.withDefaultNamespace("village/plains/houses/plains_small_house_1")));
    }

    @Test
    @DisplayName("a desert house is a desert house: nothing is re-materialed that was not oak")
    void biomeHousesKeepTheirOwnMaterials() {
        Identifier id = Identifier.withDefaultNamespace("village/desert/houses/desert_small_house_4");
        boolean sandstone = false;
        for (BuildOp op : built(id, new CellPos(1, 0)).values()) {
            sandstone |= op.state().is(Blocks.SMOOTH_SANDSTONE) || op.state().is(Blocks.SANDSTONE)
                    || op.state().is(Blocks.CUT_SANDSTONE);
        }
        assertTrue(sandstone);
    }
}
