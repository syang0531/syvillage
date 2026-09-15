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

    /** World position of a cell's north-west corner. */
    public BlockPos blockAt(CellPos pos) {
        return origin.offset(pos.gx() * CELL_BLOCKS, 0, pos.gz() * CELL_BLOCKS);
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
