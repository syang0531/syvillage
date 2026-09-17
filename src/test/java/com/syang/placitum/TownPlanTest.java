package com.syang.placitum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.placitum.build.TownPlan;
import com.syang.placitum.data.CellPos;
import com.syang.placitum.data.Plot;
import com.syang.placitum.data.PlotGrid;
import com.syang.placitum.data.PlotKind;
import com.syang.placitum.data.Settlement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The plan, which replaced a site search that was right about one condition at a time.
 *
 * <p>Everything here is arithmetic on the bell's position, so it is all testable without a world
 * - and it had better be tested, because the failures it replaces were all invisible until
 * something was already standing in the wrong place.
 */
class TownPlanTest {

    private static final BlockPos BELL = new BlockPos(112, 68, -304);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    @DisplayName("road, margin and lot divide the period with nothing left over")
    void thePeriodIsExactlyAccountedFor() {
        assertEquals(20, TownPlan.PERIOD,
                "road 3 + (margin 1 + lot 7) twice + the last margin 1");
        assertEquals(TownPlan.PERIOD,
                TownPlan.ROAD + TownPlan.LOTS_PER_BLOCK * (TownPlan.MARGIN + TownPlan.LOT)
                        + TownPlan.MARGIN,
                "a period that does not add up leaves a strip of ground with no rule for it");
        assertEquals(PlotGrid.LOT_STRIDE, TownPlan.LOT_STRIDE,
                "the grid sizes itself in lots, so it has to agree with how densely the plan"
                        + " puts them down");

        int road = 0;
        int margin = 0;
        int lot = 0;
        for (int v = 0; v < TownPlan.PERIOD; v++) {
            int x = BELL.getX() + v;
            if (TownPlan.isRoad(x, BELL.getX())) {
                road++;
                assertFalse(TownPlan.isMargin(x, BELL.getX()),
                        "no column can be road and margin at once: " + v);
            } else if (TownPlan.isMargin(x, BELL.getX())) {
                margin++;
            } else {
                lot++;
            }
        }
        assertEquals(TownPlan.ROAD, road);
        assertEquals(TownPlan.LOTS_PER_BLOCK + 1, margin,
                "a margin before each lot and one after the last: three to a period");
        assertEquals(TownPlan.LOTS_PER_BLOCK * TownPlan.LOT, lot,
                "two lots between one road and the next");
    }

    @Test
    @DisplayName("a city block holds four lots and five lamps")
    void aBlockHoldsFourLots() {
        // What the screenshot showed and the first version of the plan got wrong: the period is
        // not one lot wide. A road every twelve blocks is a car park with houses in it.
        Set<CellPos> lots = new LinkedHashSet<>();
        int lamps = 0;
        for (int dz = 0; dz < TownPlan.PERIOD; dz++) {
            for (int dx = 0; dx < TownPlan.PERIOD; dx++) {
                BlockPos pos = BELL.offset(dx, 0, dz);
                if (TownPlan.isLampPost(pos, BELL)) {
                    lamps++;
                }
                if (insideSomeLot(pos)) {
                    lots.add(TownPlan.cellAt(pos, BELL));
                }
            }
        }
        assertEquals(4, lots.size(), "four lots to a block: " + lots);
        assertEquals(TownPlan.lampsPerBlock(), lamps,
                "its four corners and its middle, and nothing halfway along an edge: " + lamps);
        assertEquals(5, lamps);
    }

    @Test
    @DisplayName("the lamps of a block are its corners and its middle")
    void lampsMarkTheBlockItself() {
        // Nine - every margin crossing - read as a lamp yard. The four that went are the ones
        // halfway along each edge, so what is left is the outline of the block plus its centre.
        Set<String> posts = new LinkedHashSet<>();
        for (int dz = 0; dz < TownPlan.PERIOD; dz++) {
            for (int dx = 0; dx < TownPlan.PERIOD; dx++) {
                if (TownPlan.isLampPost(BELL.offset(dx, 0, dz), BELL)) {
                    posts.add(dx + "," + dz);
                }
            }
        }
        assertEquals(Set.of("2,2", "2,18", "18,2", "18,18", "10,10"), posts,
                "the margins are at 2, 10 and 18 from the bell; the edge midpoints are out");
    }

    @Test
    @DisplayName("the lots either side of the bell are the same distance from it")
    void thePlanIsSymmetricAboutTheBell() {
        // Lot 0 and lot -1 are the two against the bell's crossroads. If they are not mirror
        // images the whole town leans, and every ring after them leans further.
        BlockPos east = TownPlan.lotCorner(new CellPos(0, 0), BELL);
        BlockPos west = TownPlan.lotCorner(new CellPos(-1, -1), BELL);

        assertEquals(east.getX() - BELL.getX(), BELL.getX() - (west.getX() + TownPlan.LOT - 1),
                "lot 0 starts as far east of the bell as lot -1 ends west of it");
        assertEquals(0, TownPlan.ring(new CellPos(-1, -1)),
                "the four lots touching the bell's crossroads are all ring 0");
        assertEquals(0, TownPlan.ring(new CellPos(0, 0)));
        assertEquals(1, TownPlan.ring(new CellPos(-2, 0)));
        assertEquals(1, TownPlan.ring(new CellPos(1, 0)));
    }

    @Test
    @DisplayName("the bell stands in the middle of its own road, not on the kerb")
    void theBellIsOnTheCrossroads() {
        assertTrue(TownPlan.onRoad(BELL, BELL),
                "the bell has to be on the road it is the centre of");
        assertTrue(TownPlan.isRoad(BELL.getX(), BELL.getX()));
        assertTrue(TownPlan.isRoad(BELL.getX() - 1, BELL.getX())
                        && TownPlan.isRoad(BELL.getX() + 1, BELL.getX()),
                "a three-wide road centred on the bell reaches one block either side");
        assertFalse(TownPlan.isRoad(BELL.getX() - 2, BELL.getX()),
                "and no further, or the roads run into the lots");
    }

    @Test
    @DisplayName("a lamp never stands on a road or on a lot")
    void lampsStandInTheMargin() {
        // The previous version lit the place so densely there was nowhere left to build, which is
        // the failure this whole plan exists to make impossible rather than unlikely.
        int posts = 0;
        for (int dz = 0; dz < TownPlan.PERIOD; dz++) {
            for (int dx = 0; dx < TownPlan.PERIOD; dx++) {
                BlockPos pos = BELL.offset(dx, 0, dz);
                if (!TownPlan.isLampPost(pos, BELL)) {
                    continue;
                }
                posts++;
                assertFalse(TownPlan.onRoad(pos, BELL), "lamp on the road at " + pos);
                assertFalse(insideSomeLot(pos), "lamp on a building lot at " + pos);
            }
        }
        assertEquals(TownPlan.lampsPerBlock(), posts,
                "five to a city block - its corners and its middle: " + posts);
    }

    @Test
    @DisplayName("the building sits inside its lot, evenly, with the margin left over")
    void theBuildingFitsTheLot() {
        CellPos cell = new CellPos(2, -3);
        BlockPos lot = TownPlan.lotCorner(cell, BELL);
        BlockPos building = TownPlan.buildingCorner(cell, BELL);
        int inset = (TownPlan.LOT - TownPlan.BUILDING) / 2;

        assertEquals(lot.getX() + inset, building.getX());
        assertEquals(lot.getZ() + inset, building.getZ());
        assertTrue(building.getX() + TownPlan.BUILDING <= lot.getX() + TownPlan.LOT,
                "the building runs off the east edge of its own lot");
        assertTrue(building.getZ() + TownPlan.BUILDING <= lot.getZ() + TownPlan.LOT,
                "the building runs off the south edge of its own lot");

        for (BlockPos column : TownPlan.lotColumns(cell, BELL)) {
            assertFalse(TownPlan.onRoad(column, BELL), "the lot overlaps a road at " + column);
            assertFalse(TownPlan.isLampPost(column, BELL),
                    "a lamp stands on the lot at " + column);
        }
        assertEquals(TownPlan.LOT * TownPlan.LOT, TownPlan.lotColumns(cell, BELL).size());
    }

    @Test
    @DisplayName("a lot's own ground belongs to its own cell")
    void cellAtAgreesWithLotCorner() {
        // The one that has to hold: the settlement records a finished building by asking which
        // cell its anchor is in, and if that answer disagrees with where the plan put it, the lot
        // is never marked built and gets built on again.
        for (int gz = -3; gz <= 3; gz++) {
            for (int gx = -3; gx <= 3; gx++) {
                CellPos cell = new CellPos(gx, gz);
                assertEquals(cell, TownPlan.cellAt(TownPlan.lotCorner(cell, BELL), BELL),
                        "lot corner of " + cell.toKey());
                assertEquals(cell, TownPlan.cellAt(TownPlan.buildingCorner(cell, BELL), BELL),
                        "building corner of " + cell.toKey());
            }
        }
    }

    @Test
    @DisplayName("the nearest lots are offered first")
    void cellsComeInRingOrder() {
        List<CellPos> cells = TownPlan.lotsInPhase(1);
        int ring = 0;
        for (CellPos cell : cells) {
            int here = TownPlan.ring(cell);
            assertTrue(here >= ring, "ring " + here + " came after ring " + ring);
            ring = here;
        }
    }

    @Test
    @DisplayName("a phase is four city blocks, then twelve, then twenty")
    void phasesAreRingsOfCityBlocks() {
        // The shape the town grows in. A phase finishes - roads, lamps and buildings - before
        // the next one starts, so the settlement is a finished piece of town plus the piece it
        // is working on, never a road network with four houses scattered down it.
        assertEquals(4, TownPlan.blocksInPhase(0), "the four blocks that meet at the bell");
        assertEquals(12, TownPlan.blocksInPhase(1));
        assertEquals(20, TownPlan.blocksInPhase(2));

        for (int phase = 0; phase <= 3; phase++) {
            assertEquals(TownPlan.blocksInPhase(phase) * TownPlan.LOTS_PER_BLOCK
                            * TownPlan.LOTS_PER_BLOCK,
                    TownPlan.lotsInPhase(phase).size(),
                    "four lots to every block of phase " + phase);
        }
        assertEquals(16, TownPlan.lotsInPhase(0).size(),
                "phase 0 is sixteen lots: four blocks of four");
    }

    @Test
    @DisplayName("every lot belongs to exactly one phase")
    void phasesDoNotOverlapOrLeaveGaps() {
        Set<CellPos> seen = new LinkedHashSet<>();
        for (int phase = 0; phase <= 3; phase++) {
            for (CellPos cell : TownPlan.lotsInPhase(phase)) {
                assertTrue(seen.add(cell), cell.toKey() + " is in two phases at once");
                assertEquals(phase, TownPlan.phaseOf(cell),
                        cell.toKey() + " was listed under phase " + phase);
            }
        }
        // Phases 0..3 must tile the square of lots they span, with nothing missing in between.
        int hi = TownPlan.LOTS_PER_BLOCK * 4 - 1;
        for (int gz = -hi - 1; gz <= hi; gz++) {
            for (int gx = -hi - 1; gx <= hi; gx++) {
                assertTrue(seen.contains(new CellPos(gx, gz)),
                        "no phase covers lot " + new CellPos(gx, gz).toKey());
            }
        }
    }

    @Test
    @DisplayName("a phase reaches past its own lots, so its outer road is its own")
    void aPhaseOwnsTheRoadThatClosesIt() {
        // A phase whose outer road belonged to the next one would be a ring of houses with no
        // street along one side until the town grew again.
        for (int phase = 0; phase <= 3; phase++) {
            int reach = TownPlan.phaseReach(phase);
            for (CellPos cell : TownPlan.lotsInPhase(phase)) {
                BlockPos corner = TownPlan.lotCorner(cell, BELL);
                int far = Math.max(
                        Math.abs(corner.getX() + TownPlan.LOT - 1 - BELL.getX()),
                        Math.abs(corner.getZ() + TownPlan.LOT - 1 - BELL.getZ()));
                int near = Math.max(Math.abs(corner.getX() - BELL.getX()),
                        Math.abs(corner.getZ() - BELL.getZ()));
                assertTrue(Math.max(far, near) < reach,
                        "phase " + phase + " reaches " + reach + " but lot " + cell.toKey()
                                + " runs to " + Math.max(far, near));
            }
            assertTrue(TownPlan.isRoad(BELL.getX() + reach - 1, BELL.getX()),
                    "phase " + phase + " has to stop on the road that closes it, not short of it");
        }
    }

    @Test
    @DisplayName("the claim decides how far the town goes")
    void theClaimBoundsTheTown() {
        // Five chunks of claim is eighty blocks, and phase 3 reaches eighty-one. The town stops
        // where the settlement said it would when it was registered.
        Settlement settlement = withPlotsAt();
        assertEquals(5, settlement.identity().claimRadiusChunks());
        assertEquals(3, TownPlan.maxPhase(settlement));
        assertEquals(81, TownPlan.phaseReach(3));
        assertTrue(TownPlan.phaseReach(2) < 5 * 16,
                "phase 2 stops short of the claim, so there is a phase 3 to build");
    }

    /** Whether a position falls on the 7x7 lot of whatever cell it is in. */
    private static boolean insideSomeLot(BlockPos pos) {
        CellPos cell = TownPlan.cellAt(pos, BELL);
        BlockPos corner = TownPlan.lotCorner(cell, BELL);
        return pos.getX() >= corner.getX() && pos.getX() < corner.getX() + TownPlan.LOT
                && pos.getZ() >= corner.getZ() && pos.getZ() < corner.getZ() + TownPlan.LOT;
    }

    private static Settlement withPlotsAt(CellPos... anchors) {
        Map<UUID, Plot> plots = new LinkedHashMap<>();
        for (int i = 0; i < anchors.length; i++) {
            UUID id = SettlementFixture.id(500 + i);
            plots.put(id, new Plot(id, anchors[i], 1, 1, Rotation.NONE,
                    Identifier.fromNamespaceAndPath("placitum", "house/cottage"),
                    PlotKind.HOUSE, 2, List.of()));
        }
        return SettlementFixture.standard()
                .withGrid(PlotGrid.empty(BELL, 21))
                .withPlots(plots);
    }
}
