package com.syang.placitum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.placitum.build.BuildPlanner;
import com.syang.placitum.build.WallGeometry;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.data.WallState;
import com.syang.placitum.sim.SimParams;
import com.syang.placitum.sim.Simulation;
import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.BuildRecipe;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Expansion, which everything about replay rests on.
 *
 * <p>Op lists are not stored - a wall is thousands of them and the save is rewritten whole
 * whenever a settlement changes - so the same recipe has to expand to the same list weeks
 * later, against terrain that has since moved. If it does not, promote replays a different wall
 * than the one the simulation counted, and the difference is permanent and in the world.
 */
class BuildPlannerTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** A 5x5 ring - 16 positions - on ground of a given shape. */
    private static BuildRecipe recipe(List<Integer> profile, int height) {
        return new BuildRecipe(
                Identifier.fromNamespaceAndPath("placitum", "wall/palisade"),
                new BlockPos(0, 0, 0),
                Rotation.NONE,
                Identifier.fromNamespaceAndPath("placitum", "biome_palette/plains"),
                profile,
                new BlockPos(5, height, 5),
                List.of());
    }

    private static BuildRecipe withGates(List<Integer> profile, int height, List<Integer> gates) {
        return new BuildRecipe(
                Identifier.fromNamespaceAndPath("placitum", "wall/palisade"),
                new BlockPos(0, 0, 0),
                Rotation.NONE,
                Identifier.fromNamespaceAndPath("placitum", "biome_palette/plains"),
                profile,
                new BlockPos(5, height, 5),
                gates);
    }

    private static List<Integer> flat(int y) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            out.add(y);
        }
        return out;
    }

    @Test
    @DisplayName("a wall builds at the same speed whether or not you are watching")
    void bothPathsLayAtTheSameRate() {
        SimParams params = SimParams.defaults();
        // BuildTick lays one block per interval per builder; the virtual side lays
        // opsPerBuilderStep in a whole step. Over the same span those have to match, or walking
        // away changes how fast the settlement builds - which it did, by a factor of five.
        int visiblePerStep = params.stepTicks() / 10;   // buildOpIntervalTicks default
        assertEquals(visiblePerStep, params.opsPerBuilderStep(),
                "the rate you can see and the rate you cannot have to be one number");
    }

    @Test
    @DisplayName("consecutive blocks are neighbours, so a builder can walk the wall")
    void opsFollowTheRing() {
        // Sorting by coordinate scatters the work: on one course, stepping x by one gives a
        // block on the north edge then one on the south, a ring apart. In game that produced 24
        // blocks and then nothing, because no villager could ever reach the next one.
        List<BuildOp> ops = BuildPlanner.expand(recipe(flat(64), 3));

        BuildOp previous = null;
        int jumps = 0;
        for (BuildOp op : ops) {
            if (previous != null && previous.pos().getY() == op.pos().getY()
                    && previous.pos().distSqr(op.pos()) > 2) {
                jumps++;
            }
            previous = op;
        }
        assertEquals(0, jumps,
                "every step within a course must be to an adjacent position; " + jumps
                        + " were not");
    }

    @Test
    @DisplayName("a gate has the way above it cleared, not merely left unbuilt")
    void gateLeavesTheWayOpen() {
        List<BuildOp> ops = BuildPlanner.expand(withGates(flat(64), 3, List.of(2)));

        // 15 full columns of 3, plus the gate and the two blocks of air over it. Leaving the
        // column out instead would place nothing - and placing nothing over a wall that already
        // stands leaves the old logs where they were, which is how a gate cut into a finished
        // palisade stayed buried under it.
        assertEquals(15 * 3 + 3, ops.size());

        for (BuildOp op : ops) {
            if (op.pos().getX() == 2 && op.pos().getZ() == 0) {
                if (op.pos().getY() == 65) {
                    assertTrue(op.state().is(net.minecraft.world.level.block.Blocks.OAK_FENCE_GATE),
                            "the gate itself sits on the ground");
                } else {
                    assertTrue(op.state().isAir(),
                            "a log over the gate is a doorway with a ceiling, which villagers"
                                    + " cannot path through: " + op.pos());
                }
            }
        }
    }

    @Test
    @DisplayName("a gate villagers can actually open")
    void gateIsAFenceGate() {
        for (BuildOp op : BuildPlanner.expand(withGates(flat(64), 3, List.of(2)))) {
            if (op.pos().getY() == 65 && op.pos().getX() == 2 && op.pos().getZ() == 0) {
                assertTrue(op.state().is(net.minecraft.world.level.block.Blocks.OAK_FENCE_GATE),
                        "villagers open fence gates by themselves and mobs do not; an iron door"
                                + " would seal the village in, since pathfinding reads it as solid");
                return;
            }
        }
        throw new AssertionError("no gate was placed at all");
    }

    @Test
    @DisplayName("gates are registered facing out of the settlement")
    void gatesFaceOutward() {
        List<com.syang.placitum.data.GateNode> gates =
                BuildPlanner.gatesOf(withGates(flat(64), 3, List.of(2)));
        assertEquals(1, gates.size());
        assertEquals(net.minecraft.core.Direction.NORTH, gates.get(0).facing(),
                "index 2 is on the north edge; facing inward opens the gate into the wall");
        assertEquals(new BlockPos(2, 65, 0), gates.get(0).pos());
        assertTrue(gates.get(0).open());
    }

    @Test
    @DisplayName("a gate on a skipped position is not registered")
    void gatesOnGapsAreDropped() {
        List<Integer> wet = flat(64);
        wet.set(2, WallGeometry.SKIP);
        assertTrue(BuildPlanner.gatesOf(withGates(wet, 3, List.of(2))).isEmpty(),
                "telling pathfinding about a gate in a stretch of wall that was never built"
                        + " sends villagers at a gap in the ring as though it were a door");
    }

    @Test
    @DisplayName("a job finished with nobody watching survives to be replayed")
    void completedWorkWaitsForBodies() {
        // Built virtually, a job has laid no blocks anywhere. Dropping it on completion left
        // the wall as a record with nothing under it: info reported seven gates and the ground
        // had none, because promote replays by walking the build queue and the queue was empty.
        com.syang.placitum.data.BuildJob done = SettlementFixture.standard().buildQueue().get(0)
                .withStage(com.syang.placitum.data.BuildStage.COMPLETE);

        Settlement virtualStill = Simulation.catchUp(SettlementFixture.SEED,
                SettlementFixture.full(4, com.syang.placitum.data.ResidentState.VIRTUAL)
                        .withBuildQueue(List.of(done)),
                SimParams.defaults(), SettlementFixture.START_TICK + 2000);
        assertEquals(1, virtualStill.buildQueue().size(),
                "nobody has been there to put the blocks down, so the job has to wait");

        Settlement embodied = Simulation.catchUp(SettlementFixture.SEED,
                SettlementFixture.full(4, com.syang.placitum.data.ResidentState.MATERIALIZED)
                        .withBuildQueue(List.of(done)),
                SimParams.defaults(), SettlementFixture.START_TICK + 2000);
        assertTrue(embodied.buildQueue().isEmpty(),
                "with bodies present the blocks are down, and a finished job kept for ever is a"
                        + " queue that never empties");
    }

    @Test
    @DisplayName("a finished wall is one the settlement stops wanting")
    void completionStopsTheReorderLoop() {
        // In-game this cost 1761 logs a lap. The wall finished, the job left the queue, nothing
        // wrote WallState, NeedsModule saw tier NONE and ordered another one - for ever.
        Settlement before = SettlementFixture.adopted(11, 20)
                .withDefense(SettlementFixture.standard().defense().withWall(WallState.NONE))
                .withBuildQueue(List.of());
        Settlement stocked = before.withGrid(before.grid());

        Settlement after = stocked;
        for (int i = 0; i < 40; i++) {
            after = Simulation.catchUp(SettlementFixture.SEED, after, SimParams.defaults(),
                    after.lastSimTick() + 200L * 50);
        }

        assertTrue(after.buildQueue().size() <= 1,
                "a settlement may want one wall at a time, not " + after.buildQueue().size());
    }

    @Test
    @DisplayName("the ring is one position a column, not one a block")
    void ringIsColumnsNotBlocks() {
        List<BlockPos> ring = BuildPlanner.ringOf(recipe(flat(64), 3));
        assertEquals(16, ring.size(), "16 columns, whatever height is stacked on them");
        for (BlockPos p : ring) {
            assertEquals(65, p.getY(), "the ring sits on the ground it was footed at");
        }
    }

    @Test
    @DisplayName("positions the wall skipped are not in its ring")
    void ringSkipsGaps() {
        List<Integer> wet = flat(64);
        wet.set(2, WallGeometry.SKIP);
        assertEquals(15, BuildPlanner.ringOf(recipe(wet, 3)).size(),
                "a gap in the wall is a gap in the ring, or pathfinding is told about a wall"
                        + " that is not there");
    }

    @Test
    @DisplayName("the same recipe expands to the same list, every time")
    void expansionIsPure() {
        BuildRecipe recipe = recipe(flat(64), 3);
        assertEquals(BuildPlanner.expand(recipe), BuildPlanner.expand(recipe),
                "two expansions of one recipe disagreeing means replay cannot be trusted at all");
    }

    @Test
    @DisplayName("a flat ring is the perimeter times the height")
    void flatRingIsFullHeight() {
        List<BuildOp> ops = BuildPlanner.expand(recipe(flat(64), 3));
        assertEquals(16 * 3, ops.size(), "16 columns, 3 courses");

        Set<BlockPos> seen = new HashSet<>();
        for (BuildOp op : ops) {
            assertTrue(seen.add(op.pos()), "a block placed twice at " + op.pos());
        }
    }

    @Test
    @DisplayName("ops climb, so a builder can stand on what it has laid")
    void opsAreYAscending() {
        List<Integer> stepped = flat(64);
        stepped.set(3, 66);
        stepped.set(4, 67);

        int previous = Integer.MIN_VALUE;
        for (BuildOp op : BuildPlanner.expand(recipe(stepped, 3))) {
            assertTrue(op.pos().getY() >= previous,
                    "docs/construction.md leans on this ordering instead of scaffolding");
            previous = op.pos().getY();
        }
    }

    @Test
    @DisplayName("water is left as a gap")
    void skippedPositionsPlaceNothing() {
        List<Integer> wet = flat(64);
        wet.set(2, WallGeometry.SKIP);
        wet.set(3, WallGeometry.SKIP);

        List<BuildOp> ops = BuildPlanner.expand(recipe(wet, 3));
        assertEquals(14 * 3, ops.size(), "two positions of sixteen carry nothing");
    }

    @Test
    @DisplayName("a step up is sealed, so the wall has no hole in it")
    void stepsAreSealed() {
        // Position 5 stands three blocks above position 4. Left alone, the lower column tops out
        // below the higher one's footing and a skeleton shoots straight through the gap.
        List<Integer> stepped = flat(64);
        stepped.set(5, 67);

        List<BuildOp> ops = BuildPlanner.expand(recipe(stepped, 3));
        int topOfFour = -1;
        for (BuildOp op : ops) {
            BlockPos p = op.pos();
            if (p.getX() == 4 && p.getZ() == 0) {
                topOfFour = Math.max(topOfFour, p.getY());
            }
        }
        assertTrue(topOfFour >= 68,
                "column 4 topped out at " + topOfFour + ", leaving a gap under its neighbour");
    }

    @Test
    @DisplayName("a mismatched profile builds nothing rather than something wrong")
    void lengthMismatchRefuses() {
        List<Integer> tooShort = new ArrayList<>(flat(64).subList(0, 9));
        assertTrue(BuildPlanner.expand(recipe(tooShort, 3)).isEmpty(),
                "nine heights for sixteen positions means the recipe and the geometry disagree;"
                        + " half a wall in the wrong place is worse than none");
    }

    @Test
    @DisplayName("five blocks of drop is a cliff, and a cliff is already a wall")
    void cliffsAreNotClimbed() {
        assertTrue(BuildPlanner.isCliff(64, 69));
        assertFalse(BuildPlanner.isCliff(64, 68), "four is a vertical segment, not a cliff");
        assertFalse(BuildPlanner.isCliff(WallGeometry.SKIP, 64),
                "nothing is a cliff relative to a position the wall already skips");
    }
}
