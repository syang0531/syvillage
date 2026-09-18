package com.syang.placitum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.placitum.build.GatePlan;
import com.syang.placitum.build.Spans;
import com.syang.placitum.build.Template;
import com.syang.placitum.build.TownPlan;
import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.PlotGrid;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.data.Stage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The gatehouse: a template, placed. What is tested is not its shape - that is the player's -
 * but that it lands where the plan says, turned the right way, on the road, with the rampart
 * arriving at its ends at the rampart's own heights.
 */
class GateTest {

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

    /** A gatehouse on dead flat ground with a wood standing on it, facing the given side. */
    private static Map<BlockPos, BuildOp> built(Direction side) {
        Settlement town = town();
        List<BlockPos> columns = GatePlan.footprint(town, side);
        List<Integer> profile = new ArrayList<>();
        List<Spans> wood = new ArrayList<>();
        for (BlockPos column : columns) {
            profile.add(FLOOR);
            wood.add(new Spans(column.getX(), column.getZ(), FLOOR, FLOOR + 12));
        }
        BuildRecipe recipe = GatePlan.recipe(town, side, profile, wood);

        Map<BlockPos, BuildOp> world = new HashMap<>();
        for (BuildOp op : GatePlan.expand(recipe)) {
            assertFalse(world.containsKey(op.pos()),
                    "two blocks fight over " + op.pos() + "; whichever sorts last wins, which is"
                            + " a shape decided by insertion order - and when the later one is"
                            + " air, the build rubs itself out and is asked for again for ever");
            world.put(op.pos(), op);
        }
        return world;
    }

    /** Where a template column lands for the north gate. */
    private static BlockPos at(int tx, int tz) {
        BuildRecipe recipe = GatePlan.recipe(town(), Direction.NORTH, List.of(), List.of());
        int[] origin = {recipe.extent().getX(), recipe.extent().getZ()};
        return Template.columnAt(BELL, origin, recipe.rotation(), tx, tz);
    }

    private static boolean stone(Map<BlockPos, BuildOp> world, BlockPos column, int dy) {
        BuildOp op = world.get(new BlockPos(column.getX(), FLOOR + dy, column.getZ()));
        return op != null && op.state().is(Blocks.STONE_BRICKS);
    }

    @Test
    @DisplayName("the way through is on the bell's road and open")
    void theArchIsOnTheRoad() {
        Map<BlockPos, BuildOp> world = built(Direction.NORTH);
        int middle = TownPlan.GATE_WIDE / 2;
        for (int tz = 0; tz < TownPlan.GATE_DEEP; tz++) {
            for (int across = -1; across <= 1; across++) {
                BlockPos column = at(middle + across, tz);
                assertTrue(TownPlan.inArch(column, BELL), column + " is not on the road");
                for (int dy = 1; dy <= 3; dy++) {
                    assertFalse(stone(world, column, dy),
                            "masonry in the way through at " + column + " +" + dy);
                }
            }
        }
    }

    @Test
    @DisplayName("the rampart arrives at both ends at its own heights")
    void theRampartMeetsIt() {
        // Template rows 2 to 6 are the wall's five: parapet, three of walkway, parapet. Body to
        // three, parapet at four. If the sample rampart and the gatehouse disagreed here, the
        // walkway would step where the two met.
        Map<BlockPos, BuildOp> world = built(Direction.NORTH);
        for (int tx : new int[] {0, TownPlan.GATE_WIDE - 1}) {
            for (int tz = 2; tz <= 6; tz++) {
                BlockPos column = at(tx, tz);
                assertTrue(TownPlan.onWall(column, town()), column + " is off the wall line");
                for (int dy = 1; dy <= 3; dy++) {
                    assertTrue(stone(world, column, dy), "body at " + column + " +" + dy);
                }
                boolean face = tz == 2 || tz == 6;
                assertEquals(face, stone(world, column, 4), "parapet at " + column);
            }
        }
    }

    @Test
    @DisplayName("the probe column carries masonry at the height standing() will ask")
    void theProbeIsAnswerable() {
        Map<BlockPos, BuildOp> world = built(Direction.NORTH);
        int[] probe = GatePlan.template().probe();
        assertTrue(stone(world, at(probe[0], probe[1]), 1 + probe[2]));
    }

    @Test
    @DisplayName("it carries its own light")
    void itCarriesItsOwnLight() {
        int lanterns = 0;
        for (BuildOp op : built(Direction.NORTH).values()) {
            if (op.state().is(Blocks.LANTERN)) {
                lanterns++;
            }
        }
        assertEquals(11, lanterns, "eight on the parapets and three hanging in the way through");
    }

    @Test
    @DisplayName("the same gatehouse on every side, turned, with its arch on that side's road")
    void everySideIsTheSameGate() {
        int count = built(Direction.NORTH).size();
        for (Direction side : GatePlan.sides()) {
            Map<BlockPos, BuildOp> world = built(side);
            assertEquals(count, world.size(), side + " is a different gatehouse");

            Settlement town = town();
            int onRoad = 0;
            for (BlockPos column : GatePlan.footprint(town, side)) {
                if (TownPlan.inArch(column, BELL) && TownPlan.onWall(column, town)) {
                    onRoad++;
                }
            }
            assertEquals(3 * TownPlan.WALL, onRoad,
                    side + ": three columns of road for every column of wall depth");
        }
    }

    @Test
    @DisplayName("it stands on the ground it was given and never floats")
    void itIsFounded() {
        // The ground under the north-east corner is two lower than the rest; the corner column
        // is solid masonry, so the foundation has to reach down to it.
        Settlement town = town();
        List<BlockPos> columns = GatePlan.footprint(town, Direction.NORTH);
        List<Integer> profile = new ArrayList<>();
        BlockPos low = at(TownPlan.GATE_WIDE - 1, 0);
        for (BlockPos column : columns) {
            profile.add(column.equals(low) ? FLOOR - 2 : FLOOR);
        }
        Map<BlockPos, BuildOp> world = new HashMap<>();
        for (BuildOp op : GatePlan.expand(GatePlan.recipe(town, Direction.NORTH, profile, List.of()))) {
            world.put(op.pos(), op);
        }
        BlockState under = world.get(new BlockPos(low.getX(), FLOOR - 1, low.getZ())).state();
        assertTrue(under.is(Blocks.STONE_BRICKS), "the hole under the corner is filled");
        assertTrue(stone(world, low, 0), "up to and including the floor");
    }

    @Test
    @DisplayName("the floor is the road's, however high the ground under the rest of it")
    void theFloorIsTheWayIn() {
        // Everything but the ways in stands one higher. The old rule took the highest ground
        // and put the arch a block above the road; this one builds the block over.
        Settlement town = town();
        Template template = GatePlan.template();
        List<int[]> columns = template.columns();
        java.util.Set<String> entrances = new java.util.HashSet<>();
        for (int[] e : template.entrances()) {
            entrances.add(e[0] + "," + e[1]);
        }
        List<Integer> profile = new ArrayList<>();
        for (int[] column : columns) {
            profile.add(entrances.contains(column[0] + "," + column[1]) ? FLOOR : FLOOR + 1);
        }
        assertEquals(null, GatePlan.siteTrouble(profile), "one block of hillside is built over");

        Map<BlockPos, BuildOp> world = new HashMap<>();
        for (BuildOp op : GatePlan.expand(GatePlan.recipe(town, Direction.NORTH, profile, List.of()))) {
            world.put(op.pos(), op);
        }
        int[] probe = template.probe();
        assertTrue(stone(world, at(probe[0], probe[1]), 1 + probe[2]),
                "the whole structure sits on the road's level, not the hillside's");
    }

    @Test
    @DisplayName("it waits when its ways in disagree, or when the hill is higher than a block")
    void itWaitsForThePlayer() {
        Template template = GatePlan.template();
        List<int[]> columns = template.columns();
        int[] way = template.entrances().getFirst();

        List<Integer> profile = new ArrayList<>();
        for (int[] column : columns) {
            boolean thatOne = column[0] == way[0] && column[1] == way[1];
            profile.add(thatOne ? FLOOR + 1 : FLOOR);
        }
        assertTrue(GatePlan.siteTrouble(profile).contains("not level"),
                "a stair whose foot is a block above the road is a stair to nowhere");

        profile.clear();
        int[] far = columns.get(0);
        for (int[] column : columns) {
            profile.add(column == far ? FLOOR + 2 : FLOOR);
        }
        assertTrue(GatePlan.siteTrouble(profile).contains("hillside"),
                "two blocks of hill would bury it, and we do not cut the hill");
    }
}
