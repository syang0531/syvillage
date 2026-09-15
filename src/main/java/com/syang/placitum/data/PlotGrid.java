package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * The 8-block cell grid a settlement builds on.
 *
 * <p>Not aligned to chunks: chunk alignment wastes the space that straddles a boundary and
 * makes settlements grow in chunk-shaped blocks. See docs/construction.md.
 *
 * <p>Cell states are stored, never rescanned. Rescanning would need the chunks loaded, which
 * breaks principle 2.
 */
public record PlotGrid(BlockPos origin, int size, Map<CellPos, CellState> cells) {

    public static final int CELL_BLOCKS = 8;

    public static final Codec<PlotGrid> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("origin").forGetter(PlotGrid::origin),
            Codec.INT.fieldOf("size").forGetter(PlotGrid::size),
            Codec.unboundedMap(CellPos.CODEC, CellState.CODEC).fieldOf("cells").forGetter(PlotGrid::cells)
    ).apply(i, PlotGrid::new));

    public PlotGrid {
        cells = sorted(cells);
    }

    /** Iteration order has to be deterministic, so the map is rebuilt in CellPos order. */
    private static Map<CellPos, CellState> sorted(Map<CellPos, CellState> in) {
        List<CellPos> keys = new ArrayList<>(in.keySet());
        Collections.sort(keys);
        Map<CellPos, CellState> out = new LinkedHashMap<>();
        for (CellPos key : keys) {
            out.put(key, in.get(key));
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * How many cells across a grid must be to cover a settlement claim.
     *
     * <p>The extent of the grid is a question about ground, so it is answered by the claim -
     * not by {@link ScaleTier#gridSize()}, which is a question about how far a settlement of
     * that size is allowed to spread. Sizing the map from the tier is how an adopted village
     * ends up with a 24-block grid sitting entirely inside its own market square: every cell
     * built, nowhere to put a house, and a growth loop that can never close.
     *
     * <p>Always odd, so there is a centre cell for the bell to stand in.
     */
    public static int sizeForClaim(int claimRadiusChunks) {
        int radiusBlocks = claimRadiusChunks * 16;
        int radiusCells = (radiusBlocks + CELL_BLOCKS - 1) / CELL_BLOCKS;
        return radiusCells * 2 + 1;
    }

    /**
     * The same grid over more ground, keeping every cell already surveyed.
     *
     * <p>Cell coordinates are relative to the origin and the origin does not move, so growing
     * the grid cannot invalidate what is already in it. Shrinking would, which is why this
     * refuses to.
     */
    public PlotGrid grownTo(int newSize) {
        return newSize <= size ? this : new PlotGrid(origin, newSize, cells);
    }

    public static PlotGrid empty(BlockPos origin, int size) {
        return new PlotGrid(origin, size, Map.of());
    }

    public CellState stateAt(CellPos pos) {
        return cells.getOrDefault(pos, CellState.FREE);
    }

    public PlotGrid with(CellPos pos, CellState state) {
        Map<CellPos, CellState> next = new LinkedHashMap<>(cells);
        next.put(pos, state);
        return new PlotGrid(origin, size, next);
    }

    /**
     * World position of a cell north-west corner.
     *
     * <p>Shifted half a cell so that cell (0,0) is centred on the origin rather than starting
     * at it. Without the shift the bell sits on the corner of the middle cell and the grid
     * leans one cell east and south - 21 cells reached 80 blocks west but 87 east, which is not
     * what "centred on the bell" means and not how a settlement should grow.
     */
    public BlockPos blockAt(CellPos pos) {
        int half = CELL_BLOCKS / 2;
        return origin.offset(pos.gx() * CELL_BLOCKS - half, 0, pos.gz() * CELL_BLOCKS - half);
    }

    /** Centre of a cell, which is what site selection measures distances between. */
    public BlockPos centreOf(CellPos pos) {
        return blockAt(pos).offset(CELL_BLOCKS / 2, 0, CELL_BLOCKS / 2);
    }

    public int countOf(CellState state) {
        int n = 0;
        for (CellState s : cells.values()) {
            if (s == state) {
                n++;
            }
        }
        return n;
    }
}
