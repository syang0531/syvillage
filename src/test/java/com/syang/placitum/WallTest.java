package com.syang.placitum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.placitum.build.Spans;
import com.syang.placitum.build.TownPlan;
import com.syang.placitum.build.WallPlan;
import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.Craft;
import com.syang.placitum.data.PlotGrid;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.data.Stage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where the wall goes, and where its four gates land.
 *
 * <p>All of it is arithmetic on the bell, which is the whole reason the wall could come back: the
 * ring that was deleted was computed from wherever the settlement had spread, and every bug it
 * had came from that.
 */
class WallTest {

    private static final BlockPos BELL = new BlockPos(112, 68, -304);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Settlement town() {
        return SettlementFixture.founded().withGrid(PlotGrid.empty(BELL, 21))
                .withStage(Stage.WALLED);
    }

    @Test
    @DisplayName("the wall stands fourteen inside the gap the outer phase leaves, on a lot band")
    void theWallStandsInsideTheOuterRing() {
        // The last phase is left open so the streets run out of the town rather than round it.
        // The wall used to stand in that gap, twenty blocks of lit grid from the last house;
        // now it stands inside the ring, and the ring's streets and lamps stay outside it.
        Settlement town = town();
        int outerPhase = TownPlan.outerPhase(town);
        int lastRoad = TownPlan.reachOf(town, outerPhase);

        assertEquals(lastRoad + 1 - TownPlan.WALL_INSET, TownPlan.wallInner(town),
                "fourteen inside where the outer phase stopped");
        assertEquals(TownPlan.WALL, TownPlan.wallOuter(town) - TownPlan.wallInner(town) + 1,
                "parapet, walkway, walkway, parapet");

        // Every column of the rampart, radially, is lot: never a road, never the margin a lamp
        // post stands on. That is what the number twelve was chosen for.
        for (int out = TownPlan.wallInner(town); out <= TownPlan.wallOuter(town); out++) {
            int x = BELL.getX() + out;
            assertFalse(TownPlan.isRoad(x, BELL.getX()), "wall on a road at " + out);
            assertFalse(TownPlan.isMargin(x, BELL.getX()), "wall on a margin at " + out);
        }
    }

    @Test
    @DisplayName("a tower stands on exactly one lamp post's ground, a gatehouse on two")
    void theStructuresReplaceTheLampsTheyStandOn() {
        // The lot band is seven and a tower is eight, so some lamp post goes under it whatever
        // the inset; twelve is the inset where that post is on a margin and not on the road.
        // Those posts are never built, and the tower and gatehouse carry lanterns instead.
        // A footprint is the template's occupied columns, so every one of these is ground the
        // structure actually stands on.
        Settlement town = town();
        for (int[] corner : com.syang.placitum.build.TowerPlan.corners()) {
            int posts = 0;
            for (BlockPos column : com.syang.placitum.build.TowerPlan.footprint(town, corner)) {
                assertFalse(TownPlan.isRoad(column.getX(), BELL.getX())
                        && TownPlan.isRoad(column.getZ(), BELL.getZ()),
                        "a tower on a crossroads at " + column);
                if (TownPlan.isLampPost(column, BELL)) {
                    posts++;
                }
            }
            assertEquals(1, posts, "tower " + corner[0] + "," + corner[1]);
        }
        int posts = 0;
        for (BlockPos column : com.syang.placitum.build.GatePlan.footprint(town,
                net.minecraft.core.Direction.NORTH)) {
            if (TownPlan.isLampPost(column, BELL)) {
                posts++;
            }
        }
        // Twenty-five across reaches the block-centre posts ten either side of the road, on the
        // margin the gatehouse's inner rows stand on. It carries lanterns for them.
        assertEquals(2, posts);
    }

    @Test
    @DisplayName("the wall is a closed ring with four corners")
    void theWallCloses() {
        Settlement town = town();
        int inner = TownPlan.wallInner(town);
        int outer = TownPlan.wallOuter(town);

        assertTrue(TownPlan.onWall(BELL.offset(inner, 0, 0), town));
        assertTrue(TownPlan.onWall(BELL.offset(outer, 0, 0), town));
        assertTrue(TownPlan.onWall(BELL.offset(0, 0, -outer), town));
        assertTrue(TownPlan.onWall(BELL.offset(outer, 0, outer), town), "the corner is wall too");
        assertFalse(TownPlan.onWall(BELL.offset(inner - 1, 0, 0), town), "one block inside");
        assertFalse(TownPlan.onWall(BELL.offset(outer + 1, 0, 0), town), "one block outside");
    }

    @Test
    @DisplayName("the cross-section is parapet, walkway, walkway, parapet")
    void theCrossSectionReadsFromOneNumber() {
        Settlement town = town();
        int outer = TownPlan.wallOuter(town);
        for (int depth = 0; depth < TownPlan.WALL; depth++) {
            BlockPos at = BELL.offset(outer - depth, 0, 0);
            assertEquals(depth, TownPlan.wallDepth(at, town),
                    "depth counts in from the outer face");
        }
        // Depth 0 and 3 carry the parapet; 1 and 2 are what you walk on.
        assertEquals(0, TownPlan.wallDepth(BELL.offset(outer, 0, 0), town));
        assertEquals(TownPlan.WALL - 1, TownPlan.wallDepth(
                BELL.offset(TownPlan.wallInner(town), 0, 0), town));
    }

    @Test
    @DisplayName("four gates, one per side, each dead centre without anybody centring it")
    void gatesLandOnTheBellsOwnRoads() {
        // The bell sits in the middle of a crossroads and those two roads run to the wall. That
        // is the whole calculation: because the roads are centred on the bell, the gates are
        // centred on their sides for free.
        Settlement town = town();
        int outer = TownPlan.wallOuter(town);
        Set<String> sides = new LinkedHashSet<>();

        for (BlockPos gate : new BlockPos[] {
                BELL.offset(outer, 0, 0), BELL.offset(-outer, 0, 0),
                BELL.offset(0, 0, outer), BELL.offset(0, 0, -outer)}) {
            assertTrue(TownPlan.onWall(gate, town));
            assertTrue(TownPlan.inArch(gate, BELL), gate + " is not an archway");
            assertTrue(TownPlan.inGateway(gate, town), gate + " is not in a gatehouse");
            sides.add(gate.getX() + "," + gate.getZ());
        }
        assertEquals(4, sides.size(), "four gates, not the same one four times");
    }

    @Test
    @DisplayName("the arch is three wide and a corner is never a gate")
    void theArchIsTheRoadAndNothingElse() {
        // onRoad would say yes to the entire wall - it stands exactly where a road would have
        // been, and there is a road line every twenty blocks besides. That test would have cut a
        // gate every twenty blocks instead of four in total.
        Settlement town = town();
        int outer = TownPlan.wallOuter(town);

        for (int across = -1; across <= 1; across++) {
            assertTrue(TownPlan.inArch(BELL.offset(outer, 0, across), BELL),
                    "the road is three wide and all of it passes through");
        }
        assertFalse(TownPlan.inArch(BELL.offset(outer, 0, 2), BELL), "and no wider");
        assertFalse(TownPlan.inArch(BELL.offset(outer, 0, 20), BELL),
                "a road line twenty blocks along is not this town's gate");
        assertFalse(TownPlan.inArch(BELL.offset(outer, 0, outer), BELL), "nor is a corner");
        assertFalse(TownPlan.inGateway(BELL.offset(outer, 0, outer), town));
    }

    @Test
    @DisplayName("every column the plan queues turns into blocks")
    void nothingQueuedIsSilentlyDropped() {
        // The shape of the loop this cost an evening to: the plan wanted the gateway columns,
        // the laying refused them, and nothing ever happened to make the plan stop wanting them.
        // The settlement re-queued the same thirty-two columns once a second for ever.
        //
        // Whatever the rule is, it belongs to one of the two. A column that reaches expand and
        // produces nothing is a settlement that will ask for it again.
        Settlement town = town();
        int outer = TownPlan.wallOuter(town);
        List<Spans> columns = new ArrayList<>();
        for (int depth = 0; depth < TownPlan.WALL; depth++) {
            columns.add(new Spans(BELL.getX() + outer - depth, BELL.getZ() + 30, 64, 64));
        }

        List<BuildOp> ops = WallPlan.expand(new BuildRecipe(WallPlan.RAMPART, BELL,
                Rotation.NONE, Craft.STONE.paletteId(), List.of(64, 64, 64, 64, 64),
                new BlockPos(TownPlan.wallInner(town), TownPlan.WALL_HEIGHT, outer),
                Spans.encode(columns)));

        for (Spans column : columns) {
            assertTrue(ops.stream().anyMatch(op -> op.pos().getX() == column.x()
                            && op.pos().getZ() == column.z()),
                    "column " + column.x() + "," + column.z() + " was queued and never laid");
        }
        long parapets = columns.stream()
                .filter(c -> ops.stream().anyMatch(op -> op.pos().getX() == c.x()
                        && op.pos().getZ() == c.z()
                        && op.pos().getY() == 64 + TownPlan.WALL_HEIGHT))
                .count();
        assertTrue(parapets <= 2, "only the two faces carry a parapet, not the walkway");
    }

    @Test
    @DisplayName("a built wall is a cliff, so its own cross-section has to go up together")
    void theWallMustNotWallItselfIn() {
        // What reaches the wall is a walk from the town that steps a block at a time, and a
        // three-high inner face is not a step. Raising the inner face all the way round before
        // starting the next one left the other three unreachable for ever: the wall came out one
        // column wide, and then the settlement decided there was nothing left it could build.
        //
        // The fix is an order, so this is a test about order: the four depths of one position
        // have to be planned before any of them is laid.
        Settlement town = town();
        int outer = TownPlan.wallOuter(town);
        int inner = TownPlan.wallInner(town);

        assertEquals(TownPlan.WALL, outer - inner + 1);
        assertTrue(inner - 1 < inner, "the walk approaches from the town side");

        // Every depth of one position is a separate column, and all four are wall.
        Set<String> slice = new LinkedHashSet<>();
        for (int depth = 0; depth < TownPlan.WALL; depth++) {
            BlockPos at = BELL.offset(outer - depth, 0, 30);
            assertTrue(TownPlan.onWall(at, town), at + " should be part of the wall");
            assertEquals(depth, TownPlan.wallDepth(at, town));
            slice.add(at.getX() + "," + at.getZ());
        }
        assertEquals(TownPlan.WALL, slice.size(),
                "a position is four distinct columns; planning one and laying it strands three");
    }

    @Test
    @DisplayName("a settlement keeps its wall when it loses its lord")
    void theWallIsARatchet() {
        Settlement walled = SettlementFixture.founded().withStage(Stage.WALLED);
        assertTrue(walled.craft() == Craft.PLAINS, "a wall is not a material");
        assertTrue(walled.withStage(Stage.LIT).walled(),
                "a town does not pull its own walls down because the lord was eaten");
    }

    @Test
    @DisplayName("a settlement saved before there were walls loads without one")
    void oldSavesLoadUnwalled() {
        var encoded = Settlement.CODEC.encodeStart(
                com.mojang.serialization.JsonOps.INSTANCE, SettlementFixture.standard())
                .getOrThrow().getAsJsonObject();
        // A save from before there were walls has neither the flag nor the stage that replaced
        // it - and its palette is a biome's, not the top rung of the old ladder.
        encoded.remove("walled");
        encoded.remove("stage");

        Settlement loaded = Settlement.CODEC.parse(
                com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow();

        assertFalse(loaded.walled());
        assertEquals(Map.of().size() + SettlementFixture.standard().plots().size(),
                loaded.plots().size(), "and loses nothing else on the way through");
    }

    @Test
    @DisplayName("the cross-section is the player's: parapet, three of walkway, parapet")
    void theCrossSectionIsTheSample() {
        // Two blocks of rampart were saved from a creative world; this is what they said. Body
        // of three, parapet at four on the two faces, merlons at five on alternate columns along
        // the wall - and the two faces' merlons line up.
        Settlement town = town();
        int outer = TownPlan.wallOuter(town);
        for (int along = 30; along <= 31; along++) {
            List<Spans> columns = new ArrayList<>();
            for (int depth = 0; depth < TownPlan.WALL; depth++) {
                columns.add(new Spans(BELL.getX() + outer - depth, BELL.getZ() + along, 64, 64));
            }
            List<BuildOp> ops = WallPlan.expand(new BuildRecipe(WallPlan.RAMPART, BELL,
                    Rotation.NONE, Craft.PLAINS.paletteId(), List.of(64, 64, 64, 64, 64),
                    new BlockPos(TownPlan.wallInner(town), TownPlan.WALL_HEIGHT, outer),
                    Spans.encode(columns)));
            Set<BlockPos> stone = new LinkedHashSet<>();
            for (BuildOp op : ops) {
                if (!op.state().isAir()) {
                    stone.add(op.pos());
                }
            }
            boolean merlons = Math.floorMod(BELL.getZ() + along, 2) == 0;
            for (int depth = 0; depth < TownPlan.WALL; depth++) {
                int x = BELL.getX() + outer - depth;
                int z = BELL.getZ() + along;
                boolean face = depth == 0 || depth == TownPlan.WALL - 1;
                for (int dy = 1; dy <= 3; dy++) {
                    assertTrue(stone.contains(new BlockPos(x, 64 + dy, z)), "body at depth " + depth);
                }
                assertEquals(face, stone.contains(new BlockPos(x, 68, z)),
                        "parapet only on the faces, depth " + depth);
                assertEquals(face && merlons, stone.contains(new BlockPos(x, 69, z)),
                        "merlons on alternate columns, both faces alike, depth " + depth);
            }
        }
    }
}
