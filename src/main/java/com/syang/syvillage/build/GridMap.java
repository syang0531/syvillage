package com.syang.syvillage.build;

import com.syang.syvillage.data.CellPos;
import com.syang.syvillage.data.CellState;
import com.syang.syvillage.data.PlotGrid;
import com.syang.syvillage.data.Settlement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Draws the plot grid as text.
 *
 * <p>docs/open-questions.md leaves one thing to be decided by looking rather than reasoning:
 * whether a grid of lots laid over a village vanilla already built leaves enough free ones to
 * be worth having. A vanilla house is five to nine blocks across and aligned to nothing, so it
 * can straddle four cells and cost all four.
 *
 * <p>That question has a number attached, and the number is why this prints a tally underneath.
 * A picture says the grid looks crowded; the tally says forty-one of eighty-one cells are free,
 * which is the form the answer has to take before anyone can act on it.
 */
public final class GridMap {

    private GridMap() {}

    /** North is up, west is left, matching F3. One character a cell. */
    public static List<Component> render(Settlement settlement) {
        PlotGrid grid = settlement.grid();
        int radius = (grid.size() - 1) / 2;
        List<Component> lines = new ArrayList<>();

        int side = grid.size() * PlotGrid.LOT_STRIDE;
        lines.add(Component.translatable("syvillage.grid.header", settlement.name(), grid.size(),
                        grid.size(), TownPlan.LOT, TownPlan.LOT, side, side,
                        grid.origin().toShortString())
                .withStyle(ChatFormatting.GOLD));

        for (int gz = -radius; gz <= radius; gz++) {
            MutableComponent line = Component.literal("  ");
            for (int gx = -radius; gx <= radius; gx++) {
                CellPos cell = new CellPos(gx, gz);
                boolean centre = gx == 0 && gz == 0;
                CellState state = grid.stateAt(cell);
                line.append(Component.literal(centre ? "@" : glyph(state))
                        .withStyle(centre ? ChatFormatting.AQUA : colour(state)));
            }
            lines.add(line);
        }

        lines.add(tally(grid));
        lines.add(Component.translatable("syvillage.grid.legend")
                .withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /**
     * The line the open question is actually asking for.
     *
     * <p>Free cells as a share of the grid, because "how many" means nothing without "out of
     * how many" - and because the decision it feeds is whether the grid needs re-origining,
     * which is a judgement about proportion.
     */
    private static Component tally(PlotGrid grid) {
        int total = grid.size() * grid.size();
        int free = grid.countOf(CellState.FREE);
        int surveyed = grid.cells().size();
        int unknown = total - surveyed;

        Component forbidden = grid.countOf(CellState.FORBIDDEN) > 0
                ? Component.translatable("syvillage.grid.tally.forbidden",
                        grid.countOf(CellState.FORBIDDEN))
                : Component.empty();
        Component unsurveyed = unknown > 0
                ? Component.translatable("syvillage.grid.tally.unsurveyed", unknown)
                : Component.empty();
        return Component.translatable("syvillage.grid.tally", free, grid.countOf(CellState.BUILT),
                        grid.countOf(CellState.ROAD), grid.countOf(CellState.BLOCKED), forbidden,
                        unsurveyed, Math.round(100.0 * free / Math.max(1, total)))
                .withStyle(unknown > 0 ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
    }

    private static String glyph(CellState state) {
        return switch (state) {
            case FREE -> ".";
            case ROAD -> "=";
            case RESERVED -> "o";
            case BUILT -> "#";
            case BLOCKED -> "x";
            case FORBIDDEN -> "!";
        };
    }

    private static ChatFormatting colour(CellState state) {
        return switch (state) {
            case FREE -> ChatFormatting.GREEN;
            case ROAD -> ChatFormatting.GRAY;
            case RESERVED -> ChatFormatting.YELLOW;
            case BUILT -> ChatFormatting.GOLD;
            case BLOCKED -> ChatFormatting.RED;
            case FORBIDDEN -> ChatFormatting.LIGHT_PURPLE;
        };
    }
}
