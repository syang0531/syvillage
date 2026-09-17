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
 * What the settlement knows about each lot of the town plan.
 *
 * <p>A cell is a lot, not a square of ground: the plan decides where lots are, and the grid only
 * remembers what was found on them. It used to own the geometry as well, with a cell size that
 * had to agree with the plan's period - two calculations for "which cell is this" that agreed
 * most of the time.
 *
 * <p>Cell states are stored, never rescanned on demand. Rescanning needs loaded chunks.
 */
public record PlotGrid(BlockPos origin, int size, Map<CellPos, CellState> cells) {

    /**
     * Blocks from one lot to the next, averaged.
     *
     * <p>Only ever used to work out how many lots fit across a claim. The lots themselves are
     * not evenly spaced - eight blocks apart inside a city block, twelve across a road - so this
     * is a density, not a position. Positions come from the plan.
     */
    public static final int LOT_STRIDE = 10;

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
     * not by how far a settlement of that size is allowed to spread, which is a different
     * question and now belongs to the town plan's phases. Sizing the map from a population tier
     * is how an adopted village ended up with a grid sitting entirely inside its own market
     * square: every cell built, nowhere to put a house, and a growth loop that could not close.
     *
     * <p>Always odd, so there is a centre cell for the bell to stand in.
     */
    public static int sizeForClaim(int claimRadiusChunks) {
        int radiusBlocks = claimRadiusChunks * 16;
        int radiusCells = (radiusBlocks + LOT_STRIDE - 1) / LOT_STRIDE;
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
