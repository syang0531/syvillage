package com.syang.placitum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.placitum.build.Spans;
import com.syang.placitum.build.TowerPlan;
import com.syang.placitum.build.TownPlan;
import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.Craft;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Getting onto the wall, and up the tower once you are.
 *
 * <p>A walkway with no way up is scenery, and the kind of scenery nobody notices is missing
 * until they are standing in the street looking at it. So the ramps are worth a test even though
 * they are four blocks of stone.
 */
class WallAccessTest {

    private static final BlockPos ANCHOR = new BlockPos(0, 64, 0);
    private static final int FLOOR = 64;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** A south-east tower on flat ground, so u and v run the same way as the plan writes them. */
    private static Map<BlockPos, BuildOp> tower() {
        List<Integer> profile = new ArrayList<>();
        for (int i = 0; i < TowerPlan.SIDE * TowerPlan.SIDE; i++) {
            profile.add(FLOOR);
        }
        List<BuildOp> ops = TowerPlan.expand(new BuildRecipe(TowerPlan.TOWER, ANCHOR,
                Rotation.CLOCKWISE_180, Craft.STONE.paletteId(), profile,
                new BlockPos(TowerPlan.SIDE, TowerPlan.SIDE, TowerPlan.SIDE),
                Spans.encode(List.of())));

        Map<BlockPos, BuildOp> world = new HashMap<>();
        for (BuildOp op : ops) {
            assertFalse(world.containsKey(op.pos()), "two blocks fight over " + op.pos());
            world.put(op.pos(), op);
        }
        return world;
    }

    /**
     * The height you would stand on in a column, or 0 if there is nothing there.
     *
     * <p>Stops below the parapet on purpose. A merlon is a block you walk beside, not one you
     * walk on, and counting it measures how tall the tower looks rather than where the ramp got
     * to.
     */
    private static int topOf(Map<BlockPos, BuildOp> world, int u, int v) {
        int top = 0;
        for (int h = 1; h < TowerPlan.SIDE; h++) {
            if (world.containsKey(new BlockPos(ANCHOR.getX() + u, FLOOR + h, ANCHOR.getZ() + v))) {
                top = h;
            }
        }
        return top;
    }

    @Test
    @DisplayName("the walkway walks into the tower at its own height")
    void theRampStartsLevelWithTheWall() {
        // The wall's walkway is the two middle columns of a four-wide wall, which land on 3 and
        // 4 of an eight-wide tower centred on the corner. If the tower's edge there is not the
        // wall's own height, the circuit ends in a step up or a hole.
        Map<BlockPos, BuildOp> world = tower();
        for (int lane : new int[] {3, 4}) {
            assertEquals(TownPlan.WALL_HEIGHT - 1, topOf(world, lane, 0),
                    "the ramp mouth at u=" + lane + " is not level with the wall");
            assertEquals(TownPlan.WALL_HEIGHT - 1, topOf(world, 0, lane),
                    "nor the one at v=" + lane);
        }
    }

    @Test
    @DisplayName("both ramps climb one block at a time and arrive on the deck")
    void bothRampsReachTheTop() {
        Map<BlockPos, BuildOp> world = tower();
        int deck = TowerPlan.SIDE - 1;

        for (int lane : new int[] {3, 4}) {
            // Walked the whole way across rather than to a fixed square. The two ramps cross in
            // the middle four and level off there for a block, so which square a given lane
            // reaches the deck at is a consequence rather than a rule; what is a rule is that it
            // never climbs more than one and never drops.
            int previous = topOf(world, lane, 0);
            for (int v = 1; v < TowerPlan.SIDE; v++) {
                int top = topOf(world, lane, v);
                assertTrue(top >= previous, "the ramp drops from " + previous + " to " + top
                        + " at v=" + v);
                assertTrue(top - previous <= 1, "the ramp jumps from " + previous + " to " + top
                        + " at v=" + v);
                previous = top;
            }
            assertEquals(deck, previous, "and gets to the deck");
        }
        assertEquals(deck, topOf(world, 6, 6), "the rest of the tower is deck");
    }

    @Test
    @DisplayName("the parapet never stands across the mouth of a ramp")
    void theRampsAreNotWalledOff() {
        // Crenellations run round the edge, and the edge is where the wall arrives. A merlon in
        // the wrong square is a wall across the top of the stairs.
        Map<BlockPos, BuildOp> world = tower();
        int parapet = FLOOR + TowerPlan.SIDE;
        for (int lane : new int[] {3, 4}) {
            assertFalse(world.containsKey(
                            new BlockPos(ANCHOR.getX() + lane, parapet, ANCHOR.getZ())),
                    "a merlon blocks the ramp mouth at u=" + lane);
            assertFalse(world.containsKey(
                            new BlockPos(ANCHOR.getX(), parapet, ANCHOR.getZ() + lane)),
                    "a merlon blocks the ramp mouth at v=" + lane);
        }
        assertTrue(world.containsKey(new BlockPos(ANCHOR.getX(), parapet, ANCHOR.getZ())),
                "but the corner of the parapet is still a corner");
    }

    @Test
    @DisplayName("the tower is eight on a side and eight tall")
    void itIsTheSizeItSaysItIs() {
        Map<BlockPos, BuildOp> world = tower();
        int highest = FLOOR;
        for (BlockPos pos : world.keySet()) {
            highest = Math.max(highest, pos.getY());
            assertTrue(pos.getX() >= ANCHOR.getX() && pos.getX() < ANCHOR.getX() + TowerPlan.SIDE,
                    "the tower reaches outside its own footprint at " + pos);
        }
        assertEquals(FLOOR + TowerPlan.SIDE, highest);
        assertEquals(8, TowerPlan.SIDE);
    }
}
