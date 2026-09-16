package com.syang.placitum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.placitum.build.BuildPlanner;
import com.syang.placitum.build.CottagePlan;
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




}
