package com.syang.syvillage.build;

import com.syang.syvillage.config.SyVillageConfig;
import com.syang.syvillage.data.CellPos;
import com.syang.syvillage.data.Plot;
import com.syang.syvillage.data.Settlement;
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
 * lots on it and five lamps - its corners and its middle:
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
        return marginIndex(v, origin) >= 0;
    }

    /**
     * Which margin of its block this column is, counting from the low side, or -1 for none.
     *
     * <p>Three of them per period with two lots to a block: the one against each road, and the
     * one between the lots.
     */
    private static int marginIndex(int v, int origin) {
        int u = local(v, origin);
        if (u < ROAD || (u - ROAD) % (LOT + MARGIN) != 0) {
            return -1;
        }
        return (u - ROAD) / (LOT + MARGIN);
    }

    /** Whether this column is part of the road network - either axis will do. */
    public static boolean onRoad(BlockPos pos, BlockPos bell) {
        return isRoad(pos.getX(), bell.getX()) || isRoad(pos.getZ(), bell.getZ());
    }

    /**
     * A lamp stands at the four corners of a city block and in the middle of it: five.
     *
     * <p>Every margin crossing would be nine, which was too many - dense enough that the town
     * read as a lamp yard. The four that go are the ones halfway along each edge, so what is
     * left is the shape of the block itself.
     */
    public static boolean isLampPost(BlockPos pos, BlockPos bell) {
        int mx = marginIndex(pos.getX(), bell.getX());
        int mz = marginIndex(pos.getZ(), bell.getZ());
        if (mx < 0 || mz < 0) {
            return false;
        }
        boolean xOnTheEdge = mx == 0 || mx == LOTS_PER_BLOCK;
        boolean zOnTheEdge = mz == 0 || mz == LOTS_PER_BLOCK;
        return xOnTheEdge == zOnTheEdge;   // both edges: a corner. Neither: the middle.
    }

    /** How many lamps a city block carries: its corners, and its middle. */
    public static int lampsPerBlock() {
        return 5;
    }

    /**
     * The roads that bound this column's city block - all three lanes of each of the four.
     *
     * <p>What a lamp asks before it is built. A lamp post is not on a road, and the one in the
     * middle of a block is not even beside one, so "is there a street here" is the wrong
     * question; "does this block have a street round it" is the right one. Without it the town
     * grew lamps in fields, because a post you can walk to from some street a hundred blocks
     * away is a post you can walk to.
     */
    public static List<BlockPos> boundingRoads(BlockPos pos, BlockPos bell) {
        int px = period(pos.getX(), bell.getX());
        int pz = period(pos.getZ(), bell.getZ());
        List<BlockPos> out = new ArrayList<>(4 * ROAD);
        for (int lane = -(ROAD / 2); lane <= ROAD / 2; lane++) {
            for (int side = 0; side <= 1; side++) {
                out.add(new BlockPos(bell.getX() + (px + side) * PERIOD + lane, pos.getY(),
                        pos.getZ()));
                out.add(new BlockPos(pos.getX(), pos.getY(),
                        bell.getZ() + (pz + side) * PERIOD + lane));
            }
        }
        return out;
    }

    /** Blocks across the wall: parapet, two of walkway, parapet. */
    public static final int WALL = 5;

    /**
     * How high the wall stands above its footing, to the top of the merlons.
     *
     * <p>Body of three, a parapet on the two faces at four, merlons at five. The cross-section
     * is the player's: two blocks of it saved from a creative world, in
     * {@code data/syvillage/structure/rampart.nbt}, and read back into {@link WallPlan} by hand.
     */
    public static final int WALL_HEIGHT = 5;

    /** A tower is this on a side. The template's own nine by nine. */
    public static final int TOWER = 9;

    /**
     * The gatehouse template's box: across the road, and along it.
     *
     * <p>Twenty-five across because the template carries its own two ends of rampart, nine
     * deep because the rampart's five run down the middle of it with two rows either side.
     */
    public static final int GATE_WIDE = 25;
    public static final int GATE_DEEP = 9;

    /** The tower template's box: the tower and the two arms of wall that meet at it. */
    public static final int TOWER_FRAME = 17;

    /**
     * How far a gatehouse and a tower stand proud of the wall's outer face.
     *
     * <p>Both templates put the rampart's cross-section two rows in from their outer edge, so
     * both overhang the wall by two, and the walk that decides what is reachable has to know
     * that or their outermost columns are simply not in the set.
     */
    public static final int STRUCTURE_PROUD = 2;

    /**
     * How far inside the outer phase's closing road the wall stands.
     *
     * <p>The wall used to stand exactly in that road's gap, twenty blocks of empty lit grid
     * from the last house, and it read as a fence round a field. Counting inward from the gap,
     * the outer ring is margin (-1), lot (-2 to -8), margin (-9), lot (-10 to -16), margin
     * (-17), and then the last housing phase's road (-18 to -20); lamp posts stand where two
     * margins cross. Fourteen puts the rampart's five columns at -14 to -10, inside the inner
     * lot band, touching neither a road nor a margin. Twelve was the first guess and put the
     * outer parapet on the -9 margin, which carries a lamp post every eight blocks along the
     * whole wall. The outer ring's streets and lamps stay where they are, outside, reachable
     * through the gates.
     *
     * <p>A tower is nine to the wall's five and overhangs it by two each way, which reaches
     * the -9 margin: one lamp post per corner is never built, and the tower carries lanterns
     * instead. Some post goes under a tower whatever the inset - the lot band is seven and the
     * tower is nine - and fourteen is one where it lands on a margin rather than on the road.
     * A gatehouse, twenty-five across, reaches the two posts at the -9 margin ten either side
     * of the road; it carries lanterns too.
     */
    public static final int WALL_INSET = 14;

    /**
     * The inner face of the wall, in blocks from the bell.
     *
     * <p>{@link #WALL_INSET} inside where the outer phase's closing road would have been. That
     * phase is left open so that the streets run out of the town rather than round it; the wall
     * was not planned to go in the gap, and now it does not.
     */
    public static int wallInner(Settlement settlement) {
        return (outerPhase(settlement) + 1) * PERIOD - 1 - WALL_INSET;
    }

    /** The outer face. Four blocks further out, and the edge of everything we build. */
    public static int wallOuter(Settlement settlement) {
        return wallInner(settlement) + WALL - 1;
    }

    /**
     * Whether a column is outside the last closed ring of street.
     *
     * <p>Where the side streets stop. The outer ring's block-boundary streets used to run on
     * out to the ring's edge and the wall was built over them, twenty blocks apart all the way
     * round; stopped at the wall's inner face instead, they left three-block stubs poking out
     * from the ring road towards the wall. So they end at the ring road, and the crossings
     * there become corners and T-junctions. Only the bell's own two roads carry on, out
     * through the gatehouses to the edge of the plan. The line is arithmetic, so a street
     * stops at it before there is a wall to stop at.
     */
    public static boolean beyondTheLastRing(BlockPos pos, Settlement settlement) {
        int reach = Math.max(Math.abs(pos.getX() - settlement.center().getX()),
                Math.abs(pos.getZ() - settlement.center().getZ()));
        return reach > phaseReach(maxPhase(settlement));
    }

    /**
     * Whether this column is part of the wall ring.
     *
     * <p>A square annulus: out as far as the outer face in one axis or the other, and no further
     * in than the inner face. The corners fall out of it rather than being a case.
     */
    public static boolean onWall(BlockPos pos, Settlement settlement) {
        int reach = Math.max(Math.abs(pos.getX() - settlement.center().getX()),
                Math.abs(pos.getZ() - settlement.center().getZ()));
        return reach >= wallInner(settlement) && reach <= wallOuter(settlement);
    }

    /**
     * How far into the wall's thickness a column is: 0 at the outer face, 3 at the inner.
     *
     * <p>The parapets are the two faces and the walkway is what is left, so this is the whole
     * cross-section in one number. At a corner the two axes disagree and the outer one wins,
     * which is what makes a corner read as a corner rather than as two walls crossing.
     */
    public static int wallDepth(BlockPos pos, Settlement settlement) {
        int reach = Math.max(Math.abs(pos.getX() - settlement.center().getX()),
                Math.abs(pos.getZ() - settlement.center().getZ()));
        return wallOuter(settlement) - reach;
    }

    /**
     * Whether a gate passes through here: the four points where the bell's own roads meet the
     * wall.
     *
     * <p>Nothing has to search for them. The bell sits in the middle of a crossroads and those
     * two roads run out to the wall, so a gate is simply where the wall is and the bell's road
     * still is - and because the roads are centred on the bell, each gate lands dead centre of
     * its side without anybody working out where the middle was.
     */
    public static boolean inGateway(BlockPos pos, Settlement settlement) {
        return onWall(pos, settlement) && acrossFromBellRoad(pos, settlement.center())
                <= GATE_WIDE / 2;
    }

    /**
     * How far anything on the wall reaches: the wall itself, plus what stands proud of it.
     *
     * <p>A gatehouse is eight deep against the wall's four and a corner tower eight to a side,
     * so both overhang by two. Everything that decides what can be built out here has to know
     * that, and the walk that decides what is reachable is the one that forgot.
     */
    public static int gateOuter(Settlement settlement) {
        return wallOuter(settlement) + STRUCTURE_PROUD;
    }

    /**
     * Whether a column belongs to a gatehouse, ramps included.
     *
     * <p>The rampart has to leave these alone, and so does anything built earlier that would
     * otherwise be standing on the site when the gatehouse comes to be built.
     */
    public static boolean inGatehouse(BlockPos pos, Settlement settlement) {
        BlockPos bell = settlement.center();
        int dx = Math.abs(pos.getX() - bell.getX());
        int dz = Math.abs(pos.getZ() - bell.getZ());
        int along = Math.max(dx, dz);
        return along >= wallInner(settlement) - STRUCTURE_PROUD
                && along <= gateOuter(settlement)
                && Math.min(dx, dz) <= GATE_WIDE / 2;
    }

    /**
     * Whether anything of ours is going to want this column.
     *
     * <p>Asked by the things that are built first. A lamp post inside a tower's footprint, or a
     * flight of steps inside a gatehouse's, is a three-block obstacle standing in the middle of
     * a site - and the walk that decides what is reachable cannot climb it, so the tower and the
     * gatehouse are never built at all. On flat ground it came to exactly one bad column per
     * tower and exactly two per gate, in all eight, which is what said it was geometry and not
     * terrain.
     */
    public static boolean reservedForWall(BlockPos pos, Settlement settlement) {
        return inTower(pos, settlement) || inGatehouse(pos, settlement);
    }

    /**
     * Whether a column belongs to a corner tower, ramps included.
     *
     * <p>The rampart leaves these alone: the tower owns its own approach, because the wall has
     * to arrive at the tower's height rather than four blocks below it.
     */
    public static boolean inTower(BlockPos pos, Settlement settlement) {
        int far = gateOuter(settlement);
        int near = far - (TOWER_FRAME - 1);
        int dx = Math.abs(pos.getX() - settlement.center().getX());
        int dz = Math.abs(pos.getZ() - settlement.center().getZ());
        return dx >= near && dx <= far && dz >= near && dz <= far;
    }

    /**
     * The arch itself: the three columns of road that pass through the gate.
     *
     * <p>Measured from the bell's own road rather than asked of {@link #onRoad}, which would say
     * yes to the entire wall. The wall stands exactly where a road would have been, so every
     * column of it is on a road line - and there is a road line every twenty blocks besides, so
     * that test would have opened a gate every twenty blocks instead of four in total.
     */
    public static boolean inArch(BlockPos pos, BlockPos bell) {
        return acrossFromBellRoad(pos, bell) <= ROAD / 2;
    }

    /**
     * How far a column is from the bell's own road, measured across the wall's run.
     *
     * <p>Which axis to measure is decided by which one the column is further out along: on the
     * east wall the run is north-south, so the distance that matters is the northerly one. At a
     * corner the two agree and the answer is large, which is what keeps a gate out of a corner.
     */
    public static int acrossFromBellRoad(BlockPos pos, BlockPos bell) {
        int dx = Math.abs(pos.getX() - bell.getX());
        int dz = Math.abs(pos.getZ() - bell.getZ());
        return dx > dz ? dz : dx;
    }

    /**
     * Whether a step goes along a road rather than across its width.
     *
     * <p>A road is three wide, so a third of the steps anything walking it takes are sideways
     * within the band. Those are not progress along the street and must not count as such -
     * hopping to whichever of the three lanes happens to be flat here would let a street cross
     * any hillside at all, one lane at a time.
     */
    public static boolean runsAlongRoad(int dx, int dz, BlockPos pos, BlockPos bell) {
        return dx != 0 ? isRoad(pos.getZ(), bell.getZ()) : isRoad(pos.getX(), bell.getX());
    }

    /**
     * The last phase of all: street and light, and no buildings.
     *
     * <p>A town whose outermost houses sit on its outermost road looks finished in a way no
     * settlement should. One more ring of street and lamps past the last house gives it an edge
     * that is going somewhere - and it is lit, which is where the mobs would otherwise be
     * standing when they walk in.
     */
    public static int outerPhase(Settlement settlement) {
        return maxPhase(settlement) + 1;
    }

    /** Whether a phase puts buildings up, or only street and light. */
    public static boolean housing(Settlement settlement, int phase) {
        return phase <= maxPhase(settlement);
    }

    /**
     * How far a phase reaches, in blocks from the bell.
     *
     * <p>The outer one stops one road short of closing: its blocks get the road on the inside
     * and the roads running out to it, but not the road that would join their far ends. Closed,
     * the town reads as a compound with a ring road round it. Open, the streets run out of it,
     * which is what a town on a map does.
     *
     * <pre>
     *   closed          open
     *   +---+---+       +---+---+
     *   |   |   |       |   |   |
     *   +---+---+       +---+---+
     *   |   |   |       |   |   |
     *   +---+---+       +   +   +
     * </pre>
     */
    public static int reachOf(Settlement settlement, int phase) {
        return phase > maxPhase(settlement) ? phaseReach(phase) - ROAD : phaseReach(phase);
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

    /** Which city block a lot sits on, along one axis. Two lots to a block. */
    public static int blockOf(int g) {
        return Math.floorDiv(g, LOTS_PER_BLOCK);
    }

    /**
     * Which phase a lot belongs to: how many city blocks out from the bell's crossroads it is.
     *
     * <p>Phase 0 is the four blocks that meet at the bell - sixteen lots. Phase 1 is the twelve
     * blocks around those, phase 2 the twenty around those. A settlement finishes a phase, roads
     * and lamps and buildings, before it starts the next one, so it is a town of some size at
     * every moment rather than a road network with a few houses scattered down it.
     */
    public static int phaseOf(CellPos cell) {
        return Math.max(ringIndex(blockOf(cell.gx())), ringIndex(blockOf(cell.gz())));
    }

    /**
     * How far a phase reaches from the bell, in blocks.
     *
     * <p>Its own city blocks plus the one road column that closes them off - a phase whose outer
     * road belonged to the next phase would be a ring of houses with no street on one side.
     */
    public static int phaseReach(int phase) {
        return (phase + 1) * PERIOD + 1;
    }

    /** How many city blocks a phase adds: 4, then 12, then 20. */
    public static int blocksInPhase(int phase) {
        return 4 * (2 * phase + 1);
    }

    /** The lots of one phase, nearest the bell first. */
    public static List<CellPos> lotsInPhase(int phase) {
        int hi = LOTS_PER_BLOCK * (phase + 1) - 1;
        List<CellPos> out = new ArrayList<>();
        for (int gz = -hi - 1; gz <= hi; gz++) {
            for (int gx = -hi - 1; gx <= hi; gx++) {
                CellPos cell = new CellPos(gx, gz);
                if (phaseOf(cell) == phase) {
                    out.add(cell);
                }
            }
        }
        out.sort((a, b) -> Integer.compare(order(a), order(b)));
        return List.copyOf(out);
    }

    /** Every lot the settlement will ever consider, nearest first. */
    public static List<CellPos> allLots(Settlement settlement) {
        List<CellPos> out = new ArrayList<>();
        for (int phase = 0; phase <= maxPhase(settlement); phase++) {
            out.addAll(lotsInPhase(phase));
        }
        return List.copyOf(out);
    }

    /** Ring first, then distance within the ring, so the order is stable and looks grown. */
    private static int order(CellPos c) {
        return ring(c) * 10000 + ringIndex(c.gx()) * ringIndex(c.gx())
                + ringIndex(c.gz()) * ringIndex(c.gz());
    }

    /**
     * The last phase a settlement will build, decided by its claim.
     *
     * <p>The claim is the promise the settlement made when it was registered, so it is what
     * bounds the town - not a cell count that had no relation to it. The config key is a
     * ceiling on top of that, for anyone who wants a smaller village on a big claim.
     */
    public static int maxPhase(Settlement settlement) {
        int claim = settlement.identity().claimRadiusChunks() * 16;
        int ceiling = SyVillageConfig.BUILD_MAX_PHASES.get();
        int phase = 0;
        // The last phase that fits <em>inside</em> the claim, not the first that covers it.
        // Covering it put houses out to 81 blocks on an 80-block claim, one more ring of street
        // and lamps past that, and the wall at 102 - and the last of those phases was 28 city
        // blocks of nothing but paving. One step smaller is 144 lots rather than 256, and the
        // whole town including its wall lands inside the claim it was registered with.
        while (phase + 1 < ceiling && phaseReach(phase + 1) <= claim) {
            phase++;
        }
        return phase;
    }
}
