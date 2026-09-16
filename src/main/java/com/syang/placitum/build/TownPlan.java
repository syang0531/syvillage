package com.syang.placitum.build;

import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.CellPos;
import com.syang.placitum.data.Plot;
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
 * <p>One period, twenty blocks, holds <b>two</b> lots - so a city block bounded by roads has four
 * lots on it and nine lamps around them:
 *
 * <pre>
 *   local:  0  1  2 | 3 | 4 . . . . . 10 | 11 | 12 . . . . . 18 | 19 | 0 ...
 *           road    | m |      lot       | m  |      lot       | m  | road
 *
 *   *---------------*----------------------*----------------------*    * lamp
 *   |               |         lot          |         lot          |
 *   *---------------*----------------------*----------------------*
 *   |               |         lot          |         lot          |
 *   *---------------*----------------------*----------------------*
 * </pre>
 *
 * <p>A {@link CellPos} addresses a <b>lot</b>, not a period. Lot 0 is the first one east and
 * south of the bell's crossroads and lot -1 the first one west and north, so the four lots
 * around the bell are the corners of {@code {-1,0}} in each axis and the plan is symmetric
 * about the bell.
 *
 * <p>Roads and lamps are laid on any ground, however broken. A lot is only built on if it is
 * flat - and if the player levels it later, the settlement picks it up again.
 */
public final class TownPlan {

    /** Blocks across one road. Odd, so a road has a middle for the bell to stand in. */
    public static final int ROAD = 3;

    /** The gap between road and lot, and between lot and lot. Lamps stand here. */
    public static final int MARGIN = 1;

    /** One lot. A building uses the middle {@link #BUILDING} of it; a field uses all of it. */
    public static final int LOT = 7;

    /** What a building may occupy, leaving the lot's edge for eaves and steps. */
    public static final int BUILDING = 5;

    /** Lots between one road and the next, per axis. Four to a city block. */
    public static final int LOTS_PER_BLOCK = 2;

    /** Road, then a margin and a lot per lot, then the last margin. Twenty. */
    public static final int PERIOD = ROAD + LOTS_PER_BLOCK * (MARGIN + LOT) + MARGIN;

    /** Blocks from one lot to the next, averaged over the period. Ten. */
    public static final int LOT_STRIDE = PERIOD / LOTS_PER_BLOCK;

    private TownPlan() {}

    /**
     * Where a coordinate falls within its period.
     *
     * <p>Offset by one so the bell sits in the middle of its road rather than on the edge of it.
     */
    private static int local(int v, int origin) {
        return Math.floorMod(v - origin + ROAD / 2, PERIOD);
    }

    /** Which period a coordinate falls in. */
    private static int period(int v, int origin) {
        return Math.floorDiv(v - origin + ROAD / 2, PERIOD);
    }

    public static boolean isRoad(int v, int origin) {
        return local(v, origin) < ROAD;
    }

    /** The one-block gaps: after the road, and between every pair of lots. */
    public static boolean isMargin(int v, int origin) {
        int u = local(v, origin);
        return u >= ROAD && (u - ROAD) % (LOT + MARGIN) == 0;
    }

    /** Whether this column is part of the road network - either axis will do. */
    public static boolean onRoad(BlockPos pos, BlockPos bell) {
        return isRoad(pos.getX(), bell.getX()) || isRoad(pos.getZ(), bell.getZ());
    }

    /** A lamp stands where the margins cross: nine to a city block. */
    public static boolean isLampPost(BlockPos pos, BlockPos bell) {
        return isMargin(pos.getX(), bell.getX()) && isMargin(pos.getZ(), bell.getZ());
    }

    /** Which lot a world position belongs to, along one axis. */
    private static int lotIndex(int v, int origin) {
        int inLot = (local(v, origin) - ROAD - MARGIN) / (LOT + MARGIN);
        int sub = Math.max(0, Math.min(LOTS_PER_BLOCK - 1, inLot));
        return period(v, origin) * LOTS_PER_BLOCK + sub;
    }

    /** Which lot of the plan a position belongs to. */
    public static CellPos cellAt(BlockPos pos, BlockPos bell) {
        return new CellPos(lotIndex(pos.getX(), bell.getX()), lotIndex(pos.getZ(), bell.getZ()));
    }

    /** West or north edge of a lot, along one axis. */
    private static int lotStart(int g, int origin) {
        int p = Math.floorDiv(g, LOTS_PER_BLOCK);
        int sub = Math.floorMod(g, LOTS_PER_BLOCK);
        return origin - ROAD / 2 + p * PERIOD + ROAD + MARGIN + sub * (LOT + MARGIN);
    }

    /** North-west corner of a lot's 7x7. */
    public static BlockPos lotCorner(CellPos cell, BlockPos bell) {
        return new BlockPos(lotStart(cell.gx(), bell.getX()), bell.getY(),
                lotStart(cell.gz(), bell.getZ()));
    }

    /** North-west corner of the 5x5 a building may stand on, inside the lot. */
    public static BlockPos buildingCorner(CellPos cell, BlockPos bell) {
        int inset = (LOT - BUILDING) / 2;
        return lotCorner(cell, bell).offset(inset, 0, inset);
    }

    /** Every column of a lot, in a fixed order. */
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
     * How far out from the bell a lot is, counted in rings of lots.
     *
     * <p>Lot 0 and lot -1 are both against the bell's crossroads, one on each side, so they are
     * the same ring. Without that the plan counts outward from one side and grows lopsided.
     */
    public static int ringIndex(int g) {
        return g >= 0 ? g : -g - 1;
    }

    public static int ring(CellPos cell) {
        return Math.max(ringIndex(cell.gx()), ringIndex(cell.gz()));
    }

    /**
     * Lots of the plan, nearest the bell first.
     *
     * <p>Ring order rather than raster, so a village fills outward from its centre and looks
     * like it grew rather than like it was printed.
     */
    public static List<CellPos> cells(Settlement settlement) {
        int radius = radius(settlement);
        List<CellPos> out = new ArrayList<>();
        for (int gz = -(radius + 1); gz <= radius; gz++) {
            for (int gx = -(radius + 1); gx <= radius; gx++) {
                out.add(new CellPos(gx, gz));
            }
        }
        out.sort((a, b) -> Integer.compare(order(a), order(b)));
        return List.copyOf(out);
    }

    /** Ring first, then distance within the ring, so the order is stable and looks grown. */
    private static int order(CellPos c) {
        return ring(c) * 10000 + ringIndex(c.gx()) * ringIndex(c.gx())
                + ringIndex(c.gz()) * ringIndex(c.gz());
    }

    /**
     * How many rings of lots the plan currently reaches.
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
                PlacitumConfig.BUILD_RADIUS_CELLS.get());
        int built = 0;
        for (Plot plot : settlement.plots().values()) {
            built = Math.max(built, ring(plot.anchor()));
        }
        return Math.max(0, Math.min(ceiling, built + 1));
    }

    /**
     * How far the plan reaches, in blocks from the bell.
     *
     * <p>Measured from the outermost lot rather than multiplied out, because lots are not evenly
     * spaced - eight blocks apart inside a city block, twelve across a road.
     */
    public static int reachBlocks(Settlement settlement) {
        int r = radius(settlement);
        int east = lotStart(r, 0) + LOT - 1 + MARGIN;
        int west = -lotStart(-(r + 1), 0) + MARGIN;
        return Math.max(east, west);
    }
}
