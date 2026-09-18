package com.syang.placitum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.placitum.build.GridMap;
import com.syang.placitum.build.GridSurvey;
import com.syang.placitum.build.Lots;
import com.syang.placitum.data.CellPos;
import com.syang.placitum.data.CellState;
import com.syang.placitum.data.PlotGrid;
import com.syang.placitum.data.Settlement;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The grid tally.
 *
 * <p>Worth a test out of proportion to its size, because docs/open-questions.md defers a design
 * decision to this number - whether an 8-block grid over a vanilla village leaves enough free
 * cells to build in. A tally that quietly counted unsurveyed cells as free would answer that
 * question wrongly and look completely plausible doing it.
 */
class GridMapTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        TestLanguage.inject();
    }

    private static String render(Settlement settlement) {
        StringBuilder out = new StringBuilder();
        for (Component line : GridMap.render(settlement)) {
            out.append(line.getString()).append('\n');
        }
        return out.toString();
    }

    /** A 3x3 grid: one built, one road, one blocked, and six never looked at. */
    private static Settlement partial() {
        Map<CellPos, CellState> cells = new LinkedHashMap<>();
        cells.put(new CellPos(-1, -1), CellState.BUILT);
        cells.put(new CellPos(0, -1), CellState.ROAD);
        cells.put(new CellPos(1, 1), CellState.BLOCKED);
        return SettlementFixture.standard()
                .withGrid(new PlotGrid(new BlockPos(0, 64, 0), 3, cells));
    }

    @Test
    @DisplayName("the survey may overturn its own verdict, never the player one")
    void onlyThePlayerVetoIsFrozen() {
        assertTrue(GridSurvey.isFrozen(CellState.FORBIDDEN),
                "a cell the player forbade must outlive every later survey");
        assertFalse(GridSurvey.isFrozen(CellState.BLOCKED),
                "BLOCKED is the survey own verdict: freezing it means a cell rejected once for"
                        + " a tree stays rejected after the tree, and after the bug, are gone");
        assertFalse(GridSurvey.isFrozen(CellState.FREE));
        assertFalse(GridSurvey.isFrozen(CellState.BUILT));
        assertFalse(GridSurvey.isFrozen(CellState.ROAD));
    }

    @Test
    @DisplayName("a forbidden cell is drawn and counted apart from a blocked one")
    void forbiddenIsReportedSeparately() {
        Map<CellPos, CellState> cells = new LinkedHashMap<>();
        cells.put(new CellPos(-1, -1), CellState.BLOCKED);
        cells.put(new CellPos(1, 1), CellState.FORBIDDEN);
        String text = render(SettlementFixture.standard()
                .withGrid(new PlotGrid(new BlockPos(0, 64, 0), 3, cells)));

        assertTrue(text.contains("1 blocked") && text.contains("1 forbidden"),
                "two different decisions need two different counts: " + text);
        assertTrue(text.contains("!"), "the map has to show which is which: " + text);
    }

    @Test
    @DisplayName("a site the settlement gave up on is never offered again")
    void abandonedSitesAreNotReconsidered() {
        // Giving up is only half of not looping. The other half is that the site stops being a
        // candidate - otherwise the settlement picks the same ground next step and starts the
        // whole argument with the player over again.
        Map<CellPos, CellState> cells = new LinkedHashMap<>();
        cells.put(new CellPos(0, 0), CellState.ROAD);
        cells.put(new CellPos(1, 0), CellState.FORBIDDEN);
        Settlement settlement = SettlementFixture.standard()
                .withGrid(new PlotGrid(new BlockPos(0, 64, 0), 9, cells));

        assertFalse(Lots.available(settlement, new CellPos(1, 0)),
                "the forbidden cell was offered as a building site");
        assertTrue(Lots.available(settlement, new CellPos(2, 0)),
                "only the forbidden cell is out; the rest of the plan is still open");
    }

    @Test
    @DisplayName("a survey leaves a written-off site written off")
    void abandonmentOutlivesTheNextSurvey() {
        assertTrue(GridSurvey.isFrozen(CellState.FORBIDDEN),
                "a site given up on has to survive the next survey, or the settlement rediscovers"
                        + " it and the loop starts again");
    }


    @Test
    @DisplayName("the grid covers the claim, not the population tier")
    void gridCoversTheClaim() {
        // Five chunks is 80 blocks of claim in each direction, and enough cells to cover it
        // plus the centre. The tier has nothing to say about it: a village vanilla built is the
        // size it is whether two people live in it or twenty. The count is derived rather than
        // written down, because the cell is the town plan's period and that has changed once.
        int cellsEachWay = (5 * 16 + PlotGrid.LOT_STRIDE - 1) / PlotGrid.LOT_STRIDE;
        assertEquals(cellsEachWay * 2 + 1, PlotGrid.sizeForClaim(5));
        assertEquals(5, PlotGrid.sizeForClaim(1), "one chunk is 16 blocks, so two lots each way");

        int side = PlotGrid.sizeForClaim(5) * PlotGrid.LOT_STRIDE;
        assertTrue(side >= 5 * 16 * 2,
                "a grid that does not reach the edge of the claim leaves ground unmapped: "
                        + side);
    }

    @Test
    @DisplayName("growing the grid keeps every cell already surveyed")
    void growingPreservesCells() {
        Map<CellPos, CellState> cells = new LinkedHashMap<>();
        cells.put(new CellPos(-1, -1), CellState.BUILT);
        cells.put(new CellPos(1, 1), CellState.BLOCKED);
        PlotGrid small = new PlotGrid(new BlockPos(0, 64, 0), 3, cells);

        PlotGrid grown = small.grownTo(21);

        assertEquals(21, grown.size());
        assertEquals(small.origin(), grown.origin(),
                "the origin must not move, or every stored CellPos means somewhere else");
        assertEquals(CellState.BUILT, grown.stateAt(new CellPos(-1, -1)));
        assertEquals(CellState.BLOCKED, grown.stateAt(new CellPos(1, 1)),
                "a blocked cell surviving a resize is the whole point of the override");
    }

    @Test
    @DisplayName("the grid never shrinks")
    void shrinkingIsRefused() {
        PlotGrid big = PlotGrid.empty(new BlockPos(0, 64, 0), 21);
        assertEquals(21, big.grownTo(3).size(),
                "shrinking would drop surveyed cells outside the new bounds, silently");
    }

    @Test
    @DisplayName("cells nobody has surveyed are reported, not counted as free")
    void unsurveyedCellsAreNamed() {
        String text = render(partial());

        assertTrue(text.contains("0 free"),
                "nothing was surveyed as free, so nothing may be reported as free: " + text);
        assertTrue(text.contains("6 never surveyed"),
                "six of nine cells were never looked at and the reader has to be told: " + text);
    }

    @Test
    @DisplayName("the tally counts what the map draws")
    void tallyMatchesTheMap() {
        String text = render(partial());
        assertTrue(text.contains("1 built") && text.contains("1 road")
                && text.contains("1 blocked"), text);

        // The map itself: three rows of three, with the centre marked.
        String[] lines = text.split("\n");
        assertEquals("  #=.", lines[1], "row z=-1: built, road, then an unsurveyed cell");
        assertEquals("  .@.", lines[2], "row z=0: the centre is marked wherever it lands");
        assertEquals("  ..x", lines[3], "row z=+1: the blocked cell is at +1,+1");
    }

    @Test
    @DisplayName("a fully surveyed grid says so by saying nothing")
    void completeGridHasNoWarning() {
        Map<CellPos, CellState> cells = new LinkedHashMap<>();
        for (int gz = -1; gz <= 1; gz++) {
            for (int gx = -1; gx <= 1; gx++) {
                cells.put(new CellPos(gx, gz), CellState.FREE);
            }
        }
        String text = render(SettlementFixture.standard()
                .withGrid(new PlotGrid(new BlockPos(0, 64, 0), 3, cells)));

        assertTrue(text.contains("9 free"), text);
        assertTrue(text.contains("(100% free)"), text);
        assertTrue(!text.contains("never surveyed"),
                "there is nothing to warn about, so there must be no warning: " + text);
    }
}
