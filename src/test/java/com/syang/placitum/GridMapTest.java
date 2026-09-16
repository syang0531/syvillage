package com.syang.placitum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.placitum.build.GridMap;
import com.syang.placitum.build.GridSurvey;
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
    @DisplayName("the centre cell is centred on the bell, and the grid does not lean")
    void theGridIsCentred() {
        PlotGrid grid = PlotGrid.empty(new BlockPos(100, 64, -200), 21);
        CellPos centre = new CellPos(0, 0);

        BlockPos nw = grid.blockAt(centre);
        assertTrue(nw.getX() < 100 && nw.getX() + PlotGrid.CELL_BLOCKS > 100,
                "the bell has to be inside its own cell, not on its corner: " + nw);
        assertEquals(100, grid.centreOf(centre).getX());
        assertEquals(-200, grid.centreOf(centre).getZ());

        // Reach in each direction, which must not differ by a whole cell.
        int west = 100 - grid.blockAt(new CellPos(-10, 0)).getX();
        int east = grid.blockAt(new CellPos(10, 0)).getX() + PlotGrid.CELL_BLOCKS - 1 - 100;
        assertTrue(Math.abs(west - east) <= 1,
                "a grid that reaches " + west + " west and " + east + " east is not centred");
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
    @DisplayName("a settlement may build further out than its own market square")
    void buildRadiusIsARadius() {
        // These numbers were written as grid widths, back when the grid was sized from the
        // tier. Read as a radius, an OUTPOST's 3 became one cell - eight blocks around the
        // bell, all of it the village square - and every house order was dropped for nowhere
        // to put it.
        int cells = com.syang.placitum.data.ScaleTier.OUTPOST.buildRadiusCells();
        assertTrue(cells * PlotGrid.CELL_BLOCKS >= 16,
                "the smallest settlement still has to reach past its own centre: " + cells
                        + " cell(s)");
        assertTrue(com.syang.placitum.data.ScaleTier.CITY.buildRadiusCells()
                        > com.syang.placitum.data.ScaleTier.OUTPOST.buildRadiusCells(),
                "growing has to mean being allowed to spread");
    }

    @Test
    @DisplayName("the grid covers the claim, not the population tier")
    void gridCoversTheClaim() {
        // Five chunks is 80 blocks of claim in each direction, so ten cells each way plus the
        // centre. The tier has nothing to say about it: a village vanilla built is the size it
        // is whether two people live in it or twenty.
        assertEquals(21, PlotGrid.sizeForClaim(5));
        assertEquals(5, PlotGrid.sizeForClaim(1), "one chunk is 16 blocks, so two cells each way");

        int side = PlotGrid.sizeForClaim(5) * PlotGrid.CELL_BLOCKS;
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
