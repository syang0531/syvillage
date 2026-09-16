package com.syang.placitum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.placitum.build.BuildPlanner;
import com.syang.placitum.build.CottagePlan;
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
    @DisplayName("a cottage writes each position once, and is the height it says it is")
    void cottageShapeIsWhatItClaims() {
        BuildRecipe recipe = new BuildRecipe(
                com.syang.placitum.build.HousePlanner.COTTAGE,
                new BlockPos(0, 0, 0), Rotation.NONE,
                Identifier.fromNamespaceAndPath("placitum", "biome_palette/plains"),
                java.util.Collections.nCopies(CottagePlan.SIDE * CottagePlan.SIDE + 1, 64),
                new BlockPos(CottagePlan.SIDE, CottagePlan.HEIGHT, CottagePlan.SIDE),
                List.of());

        List<BuildOp> ops = BuildPlanner.expand(recipe);
        Set<BlockPos> seen = new HashSet<>();
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (BuildOp op : ops) {
            // The shell and the fixtures used to both write the door, the beds and the torch,
            // and which one survived was decided by the sort being stable - a shape that
            // depends on insertion order rather than on anything a reader can see.
            assertTrue(seen.add(op.pos()), "written twice: " + op.pos());
            lowest = Math.min(lowest, op.pos().getY());
            highest = Math.max(highest, op.pos().getY());
        }
        assertEquals(CottagePlan.HEIGHT, highest - lowest + 1,
                "floor, three courses and a roof is five blocks");
    }

    @Test
    @DisplayName("each bed is two halves that agree which way they lie")
    void bedsArePutTogetherTheRightWayRound() {
        for (Rotation turn : Rotation.values()) {
            BuildRecipe recipe = new BuildRecipe(
                    com.syang.placitum.build.HousePlanner.COTTAGE,
                    new BlockPos(0, 0, 0), turn,
                    Identifier.fromNamespaceAndPath("placitum", "biome_palette/plains"),
                    java.util.Collections.nCopies(CottagePlan.SIDE * CottagePlan.SIDE + 1, 64),
                    new BlockPos(CottagePlan.SIDE, CottagePlan.HEIGHT, CottagePlan.SIDE),
                    List.of());

            java.util.Map<BlockPos, BuildOp> beds = new java.util.HashMap<>();
            for (BuildOp op : BuildPlanner.expand(recipe)) {
                if (op.state().getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
                    beds.put(op.pos(), op);
                }
            }
            assertEquals(4, beds.size(), "two beds, two blocks each, facing " + turn);

            for (BuildOp op : beds.values()) {
                if (op.state().getValue(net.minecraft.world.level.block.BedBlock.PART)
                        != net.minecraft.world.level.block.state.properties.BedPart.FOOT) {
                    continue;
                }
                // FACING points from the foot towards the head. Getting this backwards is how
                // the last pair ended up as mismatched halves with a villager lying across them.
                net.minecraft.core.Direction facing = op.state().getValue(
                        net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
                BuildOp head = beds.get(op.pos().relative(facing));
                assertTrue(head != null && head.state().getValue(
                                net.minecraft.world.level.block.BedBlock.PART)
                                == net.minecraft.world.level.block.state.properties.BedPart.HEAD,
                        "the foot at " + op.pos() + " faces " + facing + " and there is no head"
                                + " there");
            }
        }
    }

    @Test
    @DisplayName("the way in is not blocked by a bed")
    void theDoorwayIsClear() {
        BuildRecipe recipe = new BuildRecipe(
                com.syang.placitum.build.HousePlanner.COTTAGE,
                new BlockPos(0, 0, 0), Rotation.NONE,
                Identifier.fromNamespaceAndPath("placitum", "biome_palette/plains"),
                java.util.Collections.nCopies(CottagePlan.SIDE * CottagePlan.SIDE + 1, 64),
                new BlockPos(CottagePlan.SIDE, CottagePlan.HEIGHT, CottagePlan.SIDE),
                List.of());

        // Door faces north, so the tile just inside it is the middle of the north wall.
        BlockPos inside = new BlockPos(CottagePlan.SIDE / 2, 65, 1);
        for (BuildOp op : BuildPlanner.expand(recipe)) {
            if (op.pos().equals(inside)) {
                assertTrue(op.state().isAir(),
                        "the tile inside the door holds " + op.state().getBlock()
                                + "; the way in has to be walkable");
            }
        }
    }

    @Test
    @DisplayName("the door has something to stand on outside it")
    void thereIsAStepUpToTheDoor() {
        // The floor is laid at the highest ground under the house, so downhill of that the
        // threshold is a ledge with nothing under it. Reported from the game as "the entrance
        // cannot be reached" - the door was there and could not be walked to.
        java.util.List<Integer> sloping = new ArrayList<>(
                java.util.Collections.nCopies(CottagePlan.SIDE * CottagePlan.SIDE, 70));
        sloping.add(66);   // the ground outside the door, four blocks below the floor

        BuildRecipe recipe = new BuildRecipe(
                com.syang.placitum.build.HousePlanner.COTTAGE,
                new BlockPos(0, 0, 0), Rotation.NONE,
                Identifier.fromNamespaceAndPath("placitum", "biome_palette/plains"),
                sloping,
                new BlockPos(CottagePlan.SIDE, CottagePlan.HEIGHT, CottagePlan.SIDE),
                List.of());

        BlockPos outside = CottagePlan.doorstep(new BlockPos(0, 0, 0),
                net.minecraft.core.Direction.NORTH);
        boolean standable = false;
        for (BuildOp op : BuildPlanner.expand(recipe)) {
            if (op.pos().getX() == outside.getX() && op.pos().getZ() == outside.getZ()
                    && op.pos().getY() == 70 && !op.state().isAir()) {
                standable = true;
            }
        }
        assertTrue(standable, "nothing was laid at floor level outside the door, so the only way"
                + " in is a four-block jump");
    }

    @Test
    @DisplayName("a cottage has beds in it, and a way in and out")
    void cottageIsHabitable() {
        BuildRecipe recipe = new BuildRecipe(
                com.syang.placitum.build.HousePlanner.COTTAGE,
                new BlockPos(0, 0, 0), Rotation.NONE,
                Identifier.fromNamespaceAndPath("placitum", "biome_palette/plains"),
                java.util.Collections.nCopies(CottagePlan.SIDE * CottagePlan.SIDE + 1, 64),
                new BlockPos(CottagePlan.SIDE, CottagePlan.HEIGHT, CottagePlan.SIDE),
                List.of());

        List<BuildOp> ops = BuildPlanner.expand(recipe);
        int beds = 0;
        int doors = 0;
        for (BuildOp op : ops) {
            if (op.state().getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
                beds++;
            }
            if (op.state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                doors++;
            }
        }
        // Two blocks a bed, two halves a door. Capacity counts beds, so a house without them is
        // a decoration that cost the settlement its timber.
        assertEquals(CottagePlan.bedCount() * 2, beds);
        assertEquals(2, doors, "a house with no door is a box the residents cannot get into");
    }

    @Test
    @DisplayName("a cottage expands the same way every time")
    void cottageExpansionIsPure() {
        BuildRecipe recipe = new BuildRecipe(
                com.syang.placitum.build.HousePlanner.COTTAGE,
                new BlockPos(10, 0, -4), Rotation.CLOCKWISE_90,
                Identifier.fromNamespaceAndPath("placitum", "biome_palette/plains"),
                java.util.Collections.nCopies(CottagePlan.SIDE * CottagePlan.SIDE + 1, 70),
                new BlockPos(CottagePlan.SIDE, CottagePlan.HEIGHT, CottagePlan.SIDE),
                List.of());
        assertEquals(BuildPlanner.expand(recipe), BuildPlanner.expand(recipe));
    }

    @Test
    @DisplayName("a settlement with no roads lays some before anything else")
    void aRoadlessSettlementBuildsItsOwnRoads() {
        // Two beds, two residents, a bell in an empty field. Site selection only puts a house
        // beside a road and nothing had laid one, so no house was ever sited, beds stayed at
        // two, population stayed at two, and the pair grew old. The smallest legal settlement
        // was a settlement with a death sentence.
        Settlement founded = SettlementFixture.adopted(2, 2)
                .withGrid(com.syang.placitum.data.PlotGrid.empty(new BlockPos(0, 64, 0), 21))
                .withDefense(SettlementFixture.standard().defense().withWall(WallState.NONE))
                .withBuildQueue(List.of());
        assertEquals(0, founded.grid().countOf(com.syang.placitum.data.CellState.ROAD),
                "premise: nobody has laid a path here");

        Settlement after = Simulation.catchUp(SettlementFixture.SEED, founded,
                SimParams.defaults(), SettlementFixture.START_TICK + 2000);

        assertTrue(after.buildQueue().stream()
                        .anyMatch(j -> j.recipe().template().equals(
                                com.syang.placitum.build.RoadPlan.CROSS)),
                "nothing was ordered, so there will never be anywhere to put a house");
    }

    @Test
    @DisplayName("a crossroads is wide enough for the survey to see")
    void theRoadIsWiderThanTheSurveyStride() {
        List<BlockPos> columns = com.syang.placitum.build.RoadPlan.columns(
                new BlockPos(0, 0, 0), 8);
        java.util.Set<Integer> xs = new java.util.HashSet<>();
        for (BlockPos column : columns) {
            if (column.getZ() == 0) {
                xs.add(column.getX());
            }
        }
        // The survey samples every other block. A single-block path can fall between samples,
        // and a road no cell reads as a road is not a road.
        assertTrue(xs.size() >= 17, "the east-west arm is too short: " + xs.size());
        long acrossAtCentre = columns.stream().filter(c -> c.getX() == 0).count();
        assertTrue(acrossAtCentre >= 3, "the crossing is " + acrossAtCentre + " block(s) wide");
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
        assertTrue(virtualStill.buildQueue().stream()
                        .anyMatch(j -> j.stage() == com.syang.placitum.data.BuildStage.COMPLETE),
                "nobody has been there to put the blocks down, so the job has to wait");

        Settlement embodied = Simulation.catchUp(SettlementFixture.SEED,
                SettlementFixture.full(4, com.syang.placitum.data.ResidentState.MATERIALIZED)
                        .withBuildQueue(List.of(done)),
                SimParams.defaults(), SettlementFixture.START_TICK + 2000);
        assertTrue(embodied.buildQueue().stream()
                        .noneMatch(j -> j.stage() == com.syang.placitum.data.BuildStage.COMPLETE),
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
