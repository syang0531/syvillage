package com.syang.placitum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.placitum.build.Clearance;
import com.syang.placitum.build.CottagePlan;
import com.syang.placitum.build.Ground;
import com.syang.placitum.build.HousePlanner;
import com.syang.placitum.build.Spans;
import com.syang.placitum.build.TownPlan;
import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.CellPos;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Getting a site ready: which way the door goes, and what gets cut down first.
 *
 * <p>Both are rules that are invisible until they are wrong in the world - a house opening onto
 * its neighbour's back wall, a cottage built straight through a tree - so both are worth pinning
 * here rather than in a screenshot.
 */
class SiteWorkTest {

    private static final BlockPos BELL = new BlockPos(112, 68, -304);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    @DisplayName("every door opens onto a street, and every door faces north or south")
    void doorsOpenOntoTheStreet() {
        // A city block holds two lots per axis, so a lot does not have a road on all four sides.
        // The old rule - north of the bell face north - gave the lots in the middle of a block a
        // door onto their neighbour's back wall.
        for (int gz = -8; gz <= 7; gz++) {
            CellPos cell = new CellPos(0, gz);
            Rotation rotation = HousePlanner.doorFacing(cell);
            Direction door = CottagePlan.doorFacing(rotation);
            assertTrue(door == Direction.NORTH || door == Direction.SOUTH,
                    "lot " + cell.toKey() + " has its door facing " + door);

            BlockPos lot = TownPlan.lotCorner(cell, BELL);
            int outside = door == Direction.NORTH
                    ? lot.getZ() - TownPlan.MARGIN - 1
                    : lot.getZ() + TownPlan.LOT + TownPlan.MARGIN;
            assertTrue(TownPlan.isRoad(outside, BELL.getZ()),
                    "lot " + cell.toKey() + " opens " + door + " onto z=" + outside
                            + ", which is not a street");
        }
    }

    @Test
    @DisplayName("the two lots of a block turn their backs on each other")
    void neighboursDoNotFaceEachOther() {
        // The pair sharing a block have one road between the two of them on each side, so they
        // have to face outward. If both faced the same way one of them is looking at a wall.
        assertEquals(Rotation.NONE, HousePlanner.doorFacing(new CellPos(0, 0)));
        assertEquals(Rotation.CLOCKWISE_180, HousePlanner.doorFacing(new CellPos(0, 1)));
        assertEquals(Rotation.NONE, HousePlanner.doorFacing(new CellPos(0, -2)));
        assertEquals(Rotation.CLOCKWISE_180, HousePlanner.doorFacing(new CellPos(0, -1)));
    }

    @Test
    @DisplayName("a column with nothing growing on it costs nothing to clear")
    void emptyGroundIsNotCleared() {
        // A road batch is 64 columns. Clearing each one blindly to the height of an oak would be
        // eight hundred writes to air, and the whole street would take ten times as long to lay
        // for no visible difference.
        List<Spans> bare = List.of(new Spans(10, 20, 64, 64), new Spans(11, 20, 64, 64));
        assertTrue(Clearance.ops(bare, Set.of()).isEmpty());

        assertTrue(new Spans(10, 20, 64, 64).clear());
        assertTrue(new Spans(10, 20, Ground.SKIP, 70).clear(),
                "a column with no ground has nothing to stand on and nothing to cut");
    }

    @Test
    @DisplayName("a tree is cut from the ground up to its top, and no further")
    void growthIsCutToItsTop() {
        List<BuildOp> ops = Clearance.ops(List.of(new Spans(10, 20, 64, 69)), Set.of());

        assertEquals(5, ops.size(), "ground is 64 and the top is 69: five blocks come out");
        for (BuildOp op : ops) {
            assertTrue(op.state().isAir(), "clearing writes air, not blocks");
            assertTrue(op.pos().getY() > 64 && op.pos().getY() <= 69,
                    "cut at y=" + op.pos().getY() + ", outside 65..69");
        }
        assertEquals(64 + 1, ops.getFirst().pos().getY(),
                "the ground itself stays: it is what the road is laid on");
    }

    @Test
    @DisplayName("clearing never writes over a block the build itself places")
    void claimedPositionsAreLeftAlone() {
        // Two ops for one position is a shape decided by insertion order, and this codebase has
        // already lost an afternoon to eight of 253 ops doing exactly that.
        BlockPos wall = new BlockPos(10, 66, 20);
        List<BuildOp> ops = Clearance.ops(List.of(new Spans(10, 20, 64, 69)), Set.of(wall));

        assertEquals(4, ops.size());
        assertTrue(ops.stream().noneMatch(op -> op.pos().equals(wall)),
                "the clearance wrote air over a block the house was going to place");
    }

    @Test
    @DisplayName("water sits on the ground, not in it")
    void waterIsFoundAboveTheGround() {
        // The rule had been written the other way round since it was added and had therefore
        // never once fired: water is replaceable, so the walk down to the ground goes straight
        // through a lake and stops on the sand at the bottom, and sand is not wet. Streets were
        // laid along sea beds for as long as there have been streets.
        assertTrue(Ground.underwater(Blocks.SAND.defaultBlockState(),
                        Blocks.WATER.defaultBlockState()),
                "a block of sand with water on top of it is the sea bed");
        assertTrue(Ground.underwater(Blocks.WATER.defaultBlockState(),
                Blocks.WATER.defaultBlockState()));
        assertFalse(Ground.underwater(Blocks.GRASS_BLOCK.defaultBlockState(),
                        Blocks.AIR.defaultBlockState()),
                "dry land has to stay buildable, or the village never gets a street at all");
        assertFalse(Ground.underwater(Blocks.GRASS_BLOCK.defaultBlockState(),
                        Blocks.SHORT_GRASS.defaultBlockState()),
                "grass on grass is not a lake");
    }

    @Test
    @DisplayName("spans survive the trip through a recipe")
    void spansRoundTrip() {
        List<Spans> spans = List.of(new Spans(10, 20, 64, 69), new Spans(-11, -21, 70, 70));
        assertEquals(spans, Spans.decode(Spans.encode(spans)));
        assertEquals(List.of(), Spans.decode(List.of(1, 2, 3)),
                "a truncated list decodes to nothing rather than to a wrong column");
    }
}
