package com.syang.placitum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.placitum.build.GatePlan;
import com.syang.placitum.build.Spans;
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
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The gatehouse: a way through the wall, and the walkway carried over it.
 *
 * <p>Everything here is a pure function of the recipe, which is the point of expand being one -
 * a gatehouse is mostly holes, and a hole in the wrong place is invisible from anywhere except
 * standing in front of it.
 */
class GateTest {

    private static final BlockPos ANCHOR = new BlockPos(0, 64, -80);
    private static final int FLOOR = 64;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * A gatehouse on dead flat ground with a wood standing on it, facing north.
     *
     * <p>The wood matters. Felling what grows on a site and putting the site up are two passes
     * over the same columns, and the first version cleared the gatehouse it had just built - so
     * the settlement asked for it again a second later, and again, for ever. An expansion with
     * nothing to fell would never have shown it.
     */
    private static Map<BlockPos, BuildOp> built() {
        List<Integer> profile = new ArrayList<>();
        List<Spans> wood = new ArrayList<>();
        for (BlockPos column : GatePlan.footprint(ANCHOR, Direction.NORTH)) {
            profile.add(FLOOR);
            wood.add(new Spans(column.getX(), column.getZ(), FLOOR, FLOOR + GatePlan.TALL + 2));
        }
        List<BuildOp> ops = GatePlan.expand(new BuildRecipe(GatePlan.GATEHOUSE, ANCHOR,
                GatePlan.rotationOf(Direction.NORTH), Craft.STONE.paletteId(), profile,
                new BlockPos(GatePlan.WIDE, GatePlan.TALL, GatePlan.DEEP), Spans.encode(wood)));

        Map<BlockPos, BuildOp> world = new HashMap<>();
        for (BuildOp op : ops) {
            assertFalse(world.containsKey(op.pos()),
                    "two blocks fight over " + op.pos() + "; whichever sorts last wins, which is"
                            + " a shape decided by insertion order - and when the later one is"
                            + " air, the build rubs itself out and is asked for again for ever");
            world.put(op.pos(), op);
        }
        return world;
    }

    /** The block h above the floor, d along the road from the anchor, a across it. */
    private static BuildOp at(Map<BlockPos, BuildOp> world, int d, int a, int h) {
        BlockPos pos = ANCHOR.relative(Direction.NORTH, d)
                .relative(Direction.NORTH.getClockWise(), a);
        return world.get(new BlockPos(pos.getX(), FLOOR + h, pos.getZ()));
    }

    private static boolean open(Map<BlockPos, BuildOp> world, int d, int a, int h) {
        BuildOp op = at(world, d, a, h);
        return op == null || op.state().isAir();
    }

    @Test
    @DisplayName("the road goes all the way through, at every depth")
    void theWayThroughIsOpen() {
        // The walkway's own two columns are the ones the road passes under. Letting the stairwell
        // decide what goes there walls the gate shut with the staircase that exists to get over
        // it - a gatehouse that is a wall, which is the one thing it must not be.
        Map<BlockPos, BuildOp> world = built();
        for (int d = 0; d < GatePlan.DEEP; d++) {
            for (int a = -1; a <= 1; a++) {
                for (int h = 1; h <= TownPlan.WALL_HEIGHT; h++) {
                    assertTrue(open(world, d, a, h),
                            "the way through is blocked at depth " + d + ", across " + a
                                    + ", height " + h);
                }
            }
        }
        assertTrue(open(world, 4, 0, TownPlan.WALL_HEIGHT + 1), "the arch has a crown");
        assertFalse(open(world, 4, 2, 1), "and it is three wide, not five");
    }

    @Test
    @DisplayName("the wall arrives at the ramp's foot at its own height")
    void theRampStartsLevelWithTheWall() {
        // The ramp is on the wall now, not inside the gatehouse. Its far end has to be ordinary
        // rampart height or the circuit ends in a step, twice per gate.
        Map<BlockPos, BuildOp> world = built();
        int wallTop = TownPlan.WALL_HEIGHT - 1;

        for (int lane : new int[] {3, 4}) {
            for (int a : new int[] {-(GatePlan.WIDE / 2), GatePlan.WIDE / 2}) {
                assertFalse(open(world, lane, a, wallTop), "nothing to walk in on at across " + a);
                assertTrue(open(world, lane, a, wallTop + 1), "and nothing to walk into either");
            }
        }
    }

    @Test
    @DisplayName("the ramp climbs the wall to the deck, a block at a time")
    void theRampClimbsOutside() {
        Map<BlockPos, BuildOp> world = built();
        for (int lane : new int[] {3, 4}) {
            int previous = -1;
            for (int a = GatePlan.WIDE / 2; a >= GatePlan.HOUSE / 2; a--) {
                int top = 0;
                for (int h = 1; h <= GatePlan.TALL; h++) {
                    if (!open(world, lane, a, h)) {
                        top = h;
                    }
                }
                if (previous >= 0) {
                    assertEquals(previous + 1, top,
                            "the ramp has to gain exactly one at across " + a);
                }
                previous = top;
                for (int head = 1; head <= 2; head++) {
                    assertTrue(open(world, lane, a, top + head),
                            "no headroom over the ramp at across " + a);
                }
            }
            assertEquals(GatePlan.TALL - 1, previous, "and arrive on the deck");
        }
    }

    @Test
    @DisplayName("the deck is whole, because the climb is no longer cut out of it")
    void theDeckIsNotEatenByStairs() {
        // The point of moving the ramp onto the wall. The deck used to lose two of its rows to
        // the staircase, which made the one place worth standing the one place there was no room
        // to stand.
        Map<BlockPos, BuildOp> world = built();
        for (int d = 0; d < GatePlan.DEEP; d++) {
            for (int a = -(GatePlan.HOUSE / 2); a <= GatePlan.HOUSE / 2; a++) {
                assertFalse(open(world, d, a, GatePlan.TALL - 1),
                        "a hole in the deck at depth " + d + ", across " + a);
            }
        }
    }

    @Test
    @DisplayName("the gateway has a ceiling, so nobody walks in from above")
    void nothingFallsIntoTheGateway() {
        // Not "the deck is solid everywhere" - the last step before the deck is deliberately one
        // block lower, and the square above it has to be air for somebody to stand in. What must
        // hold is that the way through has something over it at every point, or the walkway is a
        // trapdoor into the gateway.
        Map<BlockPos, BuildOp> world = built();
        for (int d = 0; d < GatePlan.DEEP; d++) {
            for (int a = -1; a <= 1; a++) {
                boolean roofed = false;
                for (int h = TownPlan.WALL_HEIGHT + 2; h <= GatePlan.TALL - 1; h++) {
                    roofed |= !open(world, d, a, h);
                }
                assertTrue(roofed, "the gateway is open to the sky at depth " + d
                        + ", across " + a + " - that is a hole in the walkway");
            }
        }
    }

    @Test
    @DisplayName("the walkway is a staircase, not a ladder of gaps")
    void theWalkwaySurfaceIsContinuous() {
        // Walking in from the wall and across: every step is either level with the last or one
        // above it, all the way to the middle, on both of the walkway's two columns.
        Map<BlockPos, BuildOp> world = built();
        for (int lane : new int[] {3, 4}) {
            int standing = -1;
            for (int a = -4; a <= 4; a++) {
                int top = 0;
                for (int h = 1; h <= GatePlan.TALL; h++) {
                    if (!open(world, lane, a, h)) {
                        top = h;
                    }
                }
                if (standing >= 0) {
                    assertTrue(Math.abs(top - standing) <= 1,
                            "the walkway jumps from " + standing + " to " + top + " at across "
                                    + a + " on lane " + lane);
                }
                standing = top;
            }
        }
    }

    @Test
    @DisplayName("the gatehouse is nine by eight by eight and no more")
    void itIsTheSizeItSaysItIs() {
        // Masonry only. Felling the wood over the site writes air a good deal higher than the
        // gatehouse stands, and that is not the gatehouse being too tall.
        Map<BlockPos, BuildOp> world = built();
        int highest = FLOOR;
        for (Map.Entry<BlockPos, BuildOp> entry : world.entrySet()) {
            if (!entry.getValue().state().isAir()) {
                highest = Math.max(highest, entry.getKey().getY());
            }
        }
        assertEquals(FLOOR + GatePlan.TALL, highest, "the parapet is the top of it");
        assertEquals(9, GatePlan.HOUSE, "the gatehouse proper is nine across the road");
        assertEquals(8, GatePlan.DEEP);
        assertEquals(GatePlan.HOUSE + 2 * TownPlan.RAMP, GatePlan.WIDE,
                "and the footprint carries a ramp either side of it");
    }
}
