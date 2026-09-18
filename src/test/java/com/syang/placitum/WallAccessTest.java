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

    /** The two columns of the rampart you actually walk on, in the tower's own frame. */
    private static int[] walkway() {
        int outerFace = TownPlan.RAMP + (TowerPlan.SIDE + TownPlan.WALL) / 2 - 1;
        return new int[] {outerFace - 2, outerFace - 1};
    }

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * A south-east tower on flat ground with a wood standing on it.
     *
     * <p>Something to fell, on purpose: clearing the site and building on it are two passes over
     * the same columns, and the first version of this cleared what it had just built.
     */
    private static Map<BlockPos, BuildOp> tower() {
        List<Integer> profile = new ArrayList<>();
        List<Spans> wood = new ArrayList<>();
        for (BlockPos column : TowerPlan.footprint(ANCHOR)) {
            profile.add(FLOOR);
            wood.add(new Spans(column.getX(), column.getZ(), FLOOR, FLOOR + TowerPlan.SIDE + 2));
        }
        List<BuildOp> ops = TowerPlan.expand(new BuildRecipe(TowerPlan.TOWER, ANCHOR,
                Rotation.CLOCKWISE_180, Craft.STONE.paletteId(), profile,
                new BlockPos(TowerPlan.SIDE, TowerPlan.SIDE, TowerPlan.SIDE),
                Spans.encode(wood)));

        Map<BlockPos, BuildOp> world = new HashMap<>();
        for (BuildOp op : ops) {
            assertFalse(world.containsKey(op.pos()), "two blocks fight over " + op.pos()
                    + " - and when the later one is air, the tower clears itself and the"
                    + " settlement asks for it again for ever");
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
            if (solid(world, u, h, v)) {
                top = h;
            }
        }
        return top;
    }

    /** Whether the tower puts masonry here. Air is what felling a wood leaves behind. */
    private static boolean solid(Map<BlockPos, BuildOp> world, int u, int h, int v) {
        BuildOp op = world.get(
                new BlockPos(ANCHOR.getX() + u, FLOOR + h, ANCHOR.getZ() + v));
        return op != null && !op.state().isAir();
    }

    @Test
    @DisplayName("the walkway walks into the tower at its own height")
    void theRampStartsLevelWithTheWall() {
        // The wall's walkway is the two middle columns of a four-wide wall, which land on 3 and
        // 4 of an eight-wide tower centred on the corner. If the tower's edge there is not the
        // wall's own height, the circuit ends in a step up or a hole.
        Map<BlockPos, BuildOp> world = tower();
        for (int lane : walkway()) {
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

        for (int lane : walkway()) {
            // The ramp is on the wall now, so it climbs cleanly from the rampart to the deck
            // without the two of them crossing and arguing about the middle.
            int previous = topOf(world, lane, 0);
            for (int v = 1; v < TowerPlan.FRAME; v++) {
                int top = topOf(world, lane, v);
                assertTrue(top >= previous, "the ramp drops from " + previous + " to " + top
                        + " at v=" + v);
                assertTrue(top - previous <= 1, "the ramp jumps from " + previous + " to " + top
                        + " at v=" + v);
                previous = top;
            }
            assertEquals(deck, previous, "and gets to the deck");
        }
        assertEquals(deck, topOf(world, TowerPlan.FRAME - 2, TowerPlan.FRAME - 2),
                "the rest of the tower is deck");
    }

    @Test
    @DisplayName("the parapet never stands across the mouth of a ramp")
    void theRampsAreNotWalledOff() {
        // Crenellations run round the edge, and the edge is where the wall arrives. A merlon in
        // the wrong square is a wall across the top of the stairs.
        Map<BlockPos, BuildOp> world = tower();
        int parapet = FLOOR + TowerPlan.SIDE;
        for (int lane : walkway()) {
            assertFalse(solid(world, lane, TowerPlan.SIDE, TownPlan.RAMP),
                    "a merlon blocks the ramp mouth at u=" + lane);
            assertFalse(solid(world, TownPlan.RAMP, TowerPlan.SIDE, lane),
                    "a merlon blocks the ramp mouth at v=" + lane);
        }
        assertTrue(solid(world, TownPlan.RAMP, TowerPlan.SIDE, TownPlan.RAMP),
                "but the corner of the parapet is still a corner");
        assertEquals(FLOOR + TowerPlan.SIDE, parapet);
    }

    @Test
    @DisplayName("the tower is eight on a side and eight tall")
    void itIsTheSizeItSaysItIs() {
        Map<BlockPos, BuildOp> world = tower();
        int highest = FLOOR;
        for (Map.Entry<BlockPos, BuildOp> entry : world.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!entry.getValue().state().isAir()
                    && !entry.getValue().state().is(net.minecraft.world.level.block.Blocks.LANTERN)) {
                highest = Math.max(highest, pos.getY());
            }
            assertTrue(pos.getX() >= ANCHOR.getX() && pos.getX() < ANCHOR.getX() + TowerPlan.FRAME,
                    "the tower reaches outside its own footprint at " + pos);
        }
        assertEquals(FLOOR + TowerPlan.SIDE, highest);
        assertEquals(8, TowerPlan.SIDE);
    }

    @Test
    @DisplayName("two lanterns on the corner merlons, in place of the lamp post it stands on")
    void itCarriesItsOwnLight() {
        Map<BlockPos, BuildOp> world = tower();
        int lanterns = 0;
        for (Map.Entry<BlockPos, BuildOp> entry : world.entrySet()) {
            if (entry.getValue().state().is(net.minecraft.world.level.block.Blocks.LANTERN)) {
                lanterns++;
                assertEquals(FLOOR + TowerPlan.SIDE + 1, entry.getKey().getY());
                assertTrue(!world.get(entry.getKey().below()).state().isAir(),
                        "a lantern on air at " + entry.getKey());
            }
        }
        assertEquals(2, lanterns, "the town's corner, and the one facing in");
    }

    @Test
    @DisplayName("touches() names exactly the columns the tower writes in")
    void theDiagnosticKnowsWhichGroundIsOurs() {
        // A bare site. Felling a wood writes air in every column of the frame, including the
        // sixteen the tower never touches, so a cleared site cannot answer this question.
        List<Integer> profile = new ArrayList<>();
        List<BlockPos> columns = TowerPlan.footprint(ANCHOR);
        for (int i = 0; i < columns.size(); i++) {
            profile.add(FLOOR);
        }
        List<BuildOp> ops = TowerPlan.expand(new BuildRecipe(TowerPlan.TOWER, ANCHOR,
                Rotation.CLOCKWISE_180, Craft.STONE.paletteId(), profile,
                new BlockPos(TowerPlan.SIDE, TowerPlan.SIDE, TowerPlan.SIDE),
                Spans.encode(List.of())));

        java.util.Set<Long> occupied = new java.util.HashSet<>();
        for (BuildOp op : ops) {
            occupied.add(((long) op.pos().getX() << 32) ^ (op.pos().getZ() & 0xffffffffL));
        }
        int[] corner = {1, 1};   // south-east, which is CLOCKWISE_180
        int spare = 0;

        for (int i = 0; i < columns.size(); i++) {
            BlockPos column = columns.get(i);
            boolean wrote = occupied.contains(
                    ((long) column.getX() << 32) ^ (column.getZ() & 0xffffffffL));
            assertEquals(wrote, TowerPlan.touches(i, corner),
                    "the idle report asks touches() whether a blocked column is ground the"
                            + " tower would stand on; it disagrees with expand at " + column);
            if (!wrote) {
                spare++;
            }
        }
        // The four-by-four beyond both ramps, plus the width of each ramp arm that is not the
        // rampart's own four columns. Forty-eight of a hundred and forty-four: a third of what
        // the planner demands be walkable is ground the tower never puts a block on.
        assertEquals(TownPlan.RAMP * TownPlan.RAMP + 2 * TownPlan.WALL * TownPlan.RAMP, spare);
        assertEquals(48, spare);
    }

    /** The four corners, and the rotation each one is carried as. */
    private static List<int[]> corners() {
        return List.of(new int[] {-1, -1}, new int[] {1, -1}, new int[] {1, 1}, new int[] {-1, 1});
    }

    /**
     * Whether a footprint column is one of the sixty-four the tower proper stands on.
     *
     * <p>Measured out from the deck column, because a reflection preserves distance: this is the
     * same square whichever way the corner's axes run, without having to mirror anything here.
     */
    private static boolean isCore(int index, int[] corner) {
        int deck = TowerPlan.deckColumn(corner);
        int du = Math.abs(index % TowerPlan.FRAME - deck % TowerPlan.FRAME);
        int dv = Math.abs(index / TowerPlan.FRAME - deck / TowerPlan.FRAME);
        return du <= TowerPlan.SIDE / 2 && dv <= TowerPlan.SIDE / 2;
    }

    /**
     * A tower whose ramp ground is five blocks above its core.
     *
     * <p>Uneven on purpose. Every corner is the same tower with its axes flipped, so a reader
     * that forgets the flip still gets the right answer when the ground is flat - which is every
     * other test in this file, and was the only way the wall had ever been tried.
     */
    private static Map<BlockPos, BuildOp> onARidge(int[] corner) {
        List<BlockPos> columns = TowerPlan.footprint(ANCHOR);
        List<Integer> profile = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            profile.add(isCore(i, corner) ? FLOOR : FLOOR + 5);
        }
        List<BuildOp> ops = TowerPlan.expand(new BuildRecipe(TowerPlan.TOWER, ANCHOR,
                TowerPlan.quadrantOf(corner), Craft.STONE.paletteId(), profile,
                new BlockPos(TowerPlan.SIDE, TowerPlan.SIDE, TowerPlan.SIDE),
                Spans.encode(List.of())));

        Map<BlockPos, BuildOp> world = new HashMap<>();
        for (BuildOp op : ops) {
            world.put(op.pos(), op);
        }
        return world;
    }

    @Test
    @DisplayName("every corner levels its tower against its own core, not against the ramps")
    void theFloorComesFromTheTowerNotTheApproach() {
        for (int[] corner : corners()) {
            Map<BlockPos, BuildOp> world = onARidge(corner);
            int highest = FLOOR;
            for (Map.Entry<BlockPos, BuildOp> entry : world.entrySet()) {
                if (!entry.getValue().state().isAir()
                        && !entry.getValue().state().is(net.minecraft.world.level.block.Blocks.LANTERN)) {
                    highest = Math.max(highest, entry.getKey().getY());
                }
            }
            assertEquals(FLOOR + TowerPlan.SIDE, highest,
                    "the tower at corner " + corner[0] + "," + corner[1] + " was levelled"
                            + " against the ridge its ramps climb rather than against the ground"
                            + " it stands on: the frame is mirrored per corner, and whoever"
                            + " picked the core square did not mirror with it");
        }
    }

    @Test
    @DisplayName("the column standing() asks about is deck in every corner")
    void theQuestionIsPutToTheDeck() {
        for (int[] corner : corners()) {
            int deck = TowerPlan.deckColumn(corner);
            assertTrue(TowerPlan.touches(deck, corner),
                    "corner " + corner[0] + "," + corner[1] + " asks whether the tower is up by"
                            + " looking at a column the tower never builds in, so it can only"
                            + " answer no - and nothing else now stops it being built again");

            BlockPos column = TowerPlan.footprint(ANCHOR).get(deck);
            BuildOp op = onARidge(corner).get(new BlockPos(column.getX(),
                    FLOOR + TowerPlan.SIDE - 1, column.getZ()));
            assertTrue(op != null && !op.state().isAir(),
                    "and there is no deck there to find at corner " + corner[0] + corner[1]);
        }
    }
}
