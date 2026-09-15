package com.syang.placitum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.placitum.build.GridMap;
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
