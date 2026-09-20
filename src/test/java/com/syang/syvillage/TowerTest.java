package com.syang.syvillage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.syvillage.build.Spans;
import com.syang.syvillage.build.Template;
import com.syang.syvillage.build.TowerPlan;
import com.syang.syvillage.build.TownPlan;
import com.syang.syvillage.data.BuildOp;
import com.syang.syvillage.data.BuildRecipe;
import com.syang.syvillage.data.PlotGrid;
import com.syang.syvillage.data.Settlement;
import com.syang.syvillage.data.Stage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The corner tower: a template, placed on each of the four corners by turning the one the
 * player built as the north-west.
 */
class TowerTest {

    private static final BlockPos BELL = new BlockPos(112, 68, -304);
    private static final int FLOOR = 68;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Settlement town() {
        return SettlementFixture.founded().withGrid(PlotGrid.empty(BELL, 21))
                .withStage(Stage.WALLED);
    }

    private static Map<BlockPos, BuildOp> built(int[] corner) {
        Settlement town = town();
        List<BlockPos> columns = TowerPlan.footprint(town, corner);
        List<Integer> profile = new ArrayList<>();
        List<Spans> wood = new ArrayList<>();
        for (BlockPos column : columns) {
            profile.add(FLOOR);
            wood.add(new Spans(column.getX(), column.getZ(), FLOOR, FLOOR + 12));
        }
        Map<BlockPos, BuildOp> world = new HashMap<>();
        for (BuildOp op : TowerPlan.expand(TowerPlan.recipe(town, corner, profile, wood))) {
            assertFalse(world.containsKey(op.pos()), "two blocks fight over " + op.pos());
            world.put(op.pos(), op);
        }
        return world;
    }

    private static BlockPos at(int[] corner, int tx, int tz) {
        BuildRecipe recipe = TowerPlan.recipe(town(), corner, List.of(), List.of());
        int[] origin = {recipe.extent().getX(), recipe.extent().getZ()};
        return Template.columnAt(BELL, origin, recipe.rotation(), tx, tz);
    }

    private static boolean stone(Map<BlockPos, BuildOp> world, BlockPos column, int dy) {
        BuildOp op = world.get(new BlockPos(column.getX(), FLOOR + dy, column.getZ()));
        return op != null && op.state().is(Blocks.STONE_BRICKS);
    }

    @Test
    @DisplayName("the tower's outer corner is two proud of the wall's outer corner, every corner")
    void itStandsProudOfTheCorner() {
        int outer = TownPlan.wallOuter(town());
        for (int[] corner : TowerPlan.corners()) {
            BlockPos tip = at(corner, 0, 0);
            assertEquals(corner[0] * (outer + TownPlan.STRUCTURE_PROUD), tip.getX() - BELL.getX(),
                    "corner " + corner[0] + "," + corner[1] + " east-west");
            assertEquals(corner[1] * (outer + TownPlan.STRUCTURE_PROUD), tip.getZ() - BELL.getZ(),
                    "corner " + corner[0] + "," + corner[1] + " north-south");
        }
    }

    @Test
    @DisplayName("both arms meet the rampart at its own heights")
    void theRampartMeetsBothArms() {
        // The north arm ends at template x = 16, rows z 2..6; the west arm at z = 16, columns
        // x 2..6. Body to three, parapet at four on the faces, like the sample rampart.
        Map<BlockPos, BuildOp> world = built(new int[] {-1, -1});
        for (int along = 2; along <= 6; along++) {
            boolean face = along == 2 || along == 6;
            for (BlockPos column : new BlockPos[] {
                    at(new int[] {-1, -1}, TownPlan.TOWER_FRAME - 1, along),
                    at(new int[] {-1, -1}, along, TownPlan.TOWER_FRAME - 1)}) {
                assertTrue(TownPlan.onWall(column, town()), column + " is off the wall line");
                for (int dy = 1; dy <= 3; dy++) {
                    assertTrue(stone(world, column, dy), "body at " + column + " +" + dy);
                }
                assertEquals(face, stone(world, column, 4), "parapet at " + column);
            }
        }
    }

    @Test
    @DisplayName("the same tower on every corner, turned")
    void everyCornerIsTheSameTower() {
        int count = built(new int[] {-1, -1}).size();
        for (int[] corner : TowerPlan.corners()) {
            Map<BlockPos, BuildOp> world = built(corner);
            assertEquals(count, world.size(), "corner " + corner[0] + "," + corner[1]);
            int[] probe = TowerPlan.template().probe();
            assertTrue(stone(world, at(corner, probe[0], probe[1]), 1 + probe[2]),
                    "the probe column has masonry at the height standing() asks, corner "
                            + corner[0] + "," + corner[1]);
        }
    }

    @Test
    @DisplayName("it carries its own light")
    void itCarriesItsOwnLight() {
        int lanterns = 0;
        for (BuildOp op : built(new int[] {1, 1}).values()) {
            if (op.state().is(Blocks.LANTERN)) {
                lanterns++;
            }
        }
        assertEquals(8, lanterns, "four on top and four on the arms");
    }
}
