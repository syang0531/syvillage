package com.syang.placitum.build;

import com.syang.placitum.data.CellPos;
import com.syang.placitum.data.CellState;
import com.syang.placitum.data.PlotGrid;
import com.syang.placitum.data.Settlement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Where a wall goes, and in what order its blocks are counted.
 *
 * <p>The ordering is the load-bearing part. The planner samples ground heights along the ring
 * and {@link BuildPlanner} expands the ring into blocks, and those two walks happen weeks of
 * game time apart. If they disagree about which position is index 7, every height in the frozen
 * profile is attached to the wrong column and the wall replays through the side of a hill. So
 * both call the same method and neither is allowed its own opinion.
 */
public final class WallGeometry {

    /** Marks a position the wall skips - water, or the far side of a cliff. */
    public static final int SKIP = Integer.MIN_VALUE;

    private WallGeometry() {}

    /** A rectangle in block space: north-west corner, width and depth. */
    public record Box(BlockPos northWest, int width, int depth) {

        public int perimeterLength() {
            return 2 * (width + depth) - 4;
        }
    }

    /**
     * The smallest box holding everything the settlement has, plus room to grow.
     *
     * <p>docs/construction.md rejects tracing the outline of occupied cells: the result is a
     * ragged concave wall that costs more to build and encloses less. A box is cruder and
     * better - it is cheap to compute, cheap to path along, and the margin leaves somewhere for
     * the next house to go inside the walls rather than immediately outside them.
     *
     * <p>Empty when the settlement occupies nothing yet. A wall around nowhere is not a thing to
     * build cheaply and get wrong; it is a thing not to build.
     */
    public static Optional<Box> enclose(Settlement settlement, int marginCells) {
        PlotGrid grid = settlement.grid();
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (var entry : grid.cells().entrySet()) {
            CellState state = entry.getValue();
            if (state != CellState.BUILT && state != CellState.ROAD) {
                continue;
            }
            CellPos cell = entry.getKey();
            minX = Math.min(minX, cell.gx());
            minZ = Math.min(minZ, cell.gz());
            maxX = Math.max(maxX, cell.gx());
            maxZ = Math.max(maxZ, cell.gz());
        }
        if (minX == Integer.MAX_VALUE) {
            return Optional.empty();
        }

        int radius = (grid.size() - 1) / 2;
        minX = Math.max(-radius, minX - marginCells);
        minZ = Math.max(-radius, minZ - marginCells);
        maxX = Math.min(radius, maxX + marginCells);
        maxZ = Math.min(radius, maxZ + marginCells);

        BlockPos nw = grid.blockAt(new CellPos(minX, minZ));
        int width = (maxX - minX + 1) * PlotGrid.CELL_BLOCKS;
        int depth = (maxZ - minZ + 1) * PlotGrid.CELL_BLOCKS;
        return Optional.of(new Box(nw, width, depth));
    }

    /**
     * Which way a position on the ring looks out.
     *
     * <p>A gate has to face out of the settlement or a villager opens it into the wall. The
     * index alone says which edge it is on, because the walk is fixed: north edge first, then
     * east, south, west.
     */
    public static Direction outwardAt(Box box, int index) {
        int w = box.width();
        int d = box.depth();
        if (index < w) {
            return Direction.NORTH;
        }
        if (index < w + d - 1) {
            return Direction.EAST;
        }
        return index < 2 * w + d - 2 ? Direction.SOUTH : Direction.WEST;
    }

    /**
     * The ring, clockwise from the north-west corner, with no position repeated.
     *
     * <p>Y is always zero: these are columns, not blocks. What height each column starts at is
     * the frozen ground profile's business, and mixing the two here is how a ring sampled on
     * one day expands against another day's terrain.
     */
    public static List<BlockPos> perimeter(Box box) {
        List<BlockPos> out = new ArrayList<>(Math.max(0, box.perimeterLength()));
        int x0 = box.northWest().getX();
        int z0 = box.northWest().getZ();
        int x1 = x0 + box.width() - 1;
        int z1 = z0 + box.depth() - 1;

        for (int x = x0; x <= x1; x++) {
            out.add(new BlockPos(x, 0, z0));
        }
        for (int z = z0 + 1; z <= z1; z++) {
            out.add(new BlockPos(x1, 0, z));
        }
        for (int x = x1 - 1; x >= x0; x--) {
            out.add(new BlockPos(x, 0, z1));
        }
        for (int z = z1 - 1; z > z0; z--) {
            out.add(new BlockPos(x0, 0, z));
        }
        return out;
    }
}
