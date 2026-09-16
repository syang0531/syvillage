package com.syang.placitum.build;

import com.syang.placitum.data.CellPos;
import com.syang.placitum.data.PlotGrid;
import com.syang.placitum.data.Settlement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * The town plan: where the roads, the lamps and the building lots are, decided by arithmetic.
 *
 * <p>Site selection used to be a search - free cell, beside a road, nearest the centre, level
 * enough - and every bug it produced came from that search being right about one thing and wrong
 * about another. Houses eighteen blocks down a slope. Lamps so dense there was nowhere left to
 * build. A cottage on top of another cottage's roof.
 *
 * <p>A plan cannot do any of that. Every position is a function of the bell and nothing else, so
 * the same village always has the same streets, and "is there room for a house" stops being a
 * judgement call.
 *
 * <pre>
 *   . . 가 . . . . . . . 가 . .      road   3 blocks
 *   . . .  . . . . . . . .  . .      margin 1 block   (lamps live here)
 *   가 . . 부 부 부 부 부 . . 가      lot    7 blocks
 *   .  . . 부 건 건 건 부 . . .       building 5x5 inside the lot
 * </pre>
 *
 * <p>Roads and lamps are laid on any ground, however broken. A lot is only built on if it is
 * flat - and if the player levels it later, the settlement picks it up again.
 */
public final class TownPlan {

    /** Blocks across one road. Odd, so a road has a middle for the bell to stand in. */
    public static final int ROAD = 3;

    /** The gap between road and lot. Lamps stand here, and roofs overhang into it. */
    public static final int MARGIN = 1;

    /** The lot. A building uses the middle {@link #BUILDING} of it. */
    public static final int LOT = 7;

    /** What a building may occupy, leaving the lot's edge for steps and eaves. */
    public static final int BUILDING = 5;

    /** Road, margin, lot, margin. One cell of the plan. */
    public static final int PERIOD = ROAD + MARGIN + LOT + MARGIN;

    private TownPlan() {}

    /**
     * Where a coordinate falls within its period.
     *
     * <p>Offset by one so the bell sits in the middle of its road rather than on the edge of it.
     */
    private static int local(int v, int origin) {
        return Math.floorMod(v - origin + ROAD / 2, PERIOD);
    }

    public static boolean isRoad(int v, int origin) {
        return local(v, origin) < ROAD;
    }

    public static boolean isMargin(int v, int origin) {
        int u = local(v, origin);
        return u == ROAD || u == PERIOD - 1;
    }

    /** Whether this column is part of the road network - either axis will do. */
    public static boolean onRoad(BlockPos pos, BlockPos bell) {
        return isRoad(pos.getX(), bell.getX()) || isRoad(pos.getZ(), bell.getZ());
    }

    /** A lamp stands where the margins cross: the corners of every lot. */
    public static boolean isLampPost(BlockPos pos, BlockPos bell) {
        return isMargin(pos.getX(), bell.getX()) && isMargin(pos.getZ(), bell.getZ());
    }

    /** Which cell of the plan a position belongs to. */
    public static CellPos cellAt(BlockPos pos, BlockPos bell) {
        return new CellPos(Math.floorDiv(pos.getX() - bell.getX() + ROAD / 2, PERIOD),
                Math.floorDiv(pos.getZ() - bell.getZ() + ROAD / 2, PERIOD));
    }

    /** North-west corner of a cell's 7x7 lot. */
    public static BlockPos lotCorner(CellPos cell, BlockPos bell) {
        return new BlockPos(
                bell.getX() - ROAD / 2 + cell.gx() * PERIOD + ROAD + MARGIN,
                bell.getY(),
                bell.getZ() - ROAD / 2 + cell.gz() * PERIOD + ROAD + MARGIN);
    }

    /** North-west corner of the 5x5 a building may stand on, inside the lot. */
    public static BlockPos buildingCorner(CellPos cell, BlockPos bell) {
        int inset = (LOT - BUILDING) / 2;
        return lotCorner(cell, bell).offset(inset, 0, inset);
    }

    /** Every column of a cell's lot, in a fixed order. */
    public static List<BlockPos> lotColumns(CellPos cell, BlockPos bell) {
        BlockPos corner = lotCorner(cell, bell);
        List<BlockPos> out = new ArrayList<>(LOT * LOT);
        for (int dz = 0; dz < LOT; dz++) {
            for (int dx = 0; dx < LOT; dx++) {
                out.add(corner.offset(dx, 0, dz));
            }
        }
        return out;
    }

    /**
     * Cells of the plan, nearest the bell first.
     *
     * <p>Ring order rather than raster, so a village fills outward from its centre and looks
     * like it grew rather than like it was printed.
     */
    public static List<CellPos> cells(Settlement settlement) {
        int radius = radius(settlement);
        List<CellPos> out = new ArrayList<>();
        for (int gz = -radius; gz <= radius; gz++) {
            for (int gx = -radius; gx <= radius; gx++) {
                out.add(new CellPos(gx, gz));
            }
        }
        out.sort((a, b) -> Integer.compare(ringOrder(a), ringOrder(b)));
        return List.copyOf(out);
    }

    /** Ring first, then distance within the ring, so the order is stable and looks grown. */
    private static int ringOrder(CellPos c) {
        return Math.max(Math.abs(c.gx()), Math.abs(c.gz())) * 1000
                + c.gx() * c.gx() + c.gz() * c.gz();
    }

    /**
     * How many cells out the plan currently reaches.
     *
     * <p>One ring beyond the outermost thing the settlement has built, so a village of two
     * houses is a village of two houses with a street round it - not two houses in the middle of
     * a hundred and fifty blocks of empty road and lamps, which is what a fixed radius gives you
     * and which took a very long time to build to no visible purpose.
     *
     * <p>It only ever grows, because it is measured from what stands. Nothing here shrinks a
     * town back down after the player pulls a house apart.
     */
    public static int radius(Settlement settlement) {
        int ceiling = Math.min((settlement.grid().size() - 1) / 2,
                com.syang.placitum.config.PlacitumConfig.BUILD_RADIUS_CELLS.get());
        int built = 0;
        for (com.syang.placitum.data.Plot plot : settlement.plots().values()) {
            built = Math.max(built,
                    Math.max(Math.abs(plot.anchor().gx()), Math.abs(plot.anchor().gz())));
        }
        return Math.max(1, Math.min(ceiling, built + 1));
    }

    /** How far the plan reaches, in blocks from the bell. */
    public static int reachBlocks(Settlement settlement) {
        return radius(settlement) * PERIOD + PERIOD / 2;
    }

    /** Sanity: the grid's cells and the plan's periods have to be the same thing. */
    public static void checkGridMatchesPlan() {
        if (PlotGrid.CELL_BLOCKS != PERIOD) {
            throw new IllegalStateException("PlotGrid.CELL_BLOCKS is " + PlotGrid.CELL_BLOCKS
                    + " but the town plan has a period of " + PERIOD
                    + "; a cell of the grid and a cell of the plan must be the same square");
        }
    }
}
