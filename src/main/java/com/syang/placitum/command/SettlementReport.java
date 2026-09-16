package com.syang.placitum.command;

import com.syang.placitum.build.BuildPlanner;
import com.syang.placitum.build.Lots;
import com.syang.placitum.build.TownPlan;
import com.syang.placitum.data.BuildJob;
import com.syang.placitum.data.CellState;
import com.syang.placitum.data.PlotKind;
import com.syang.placitum.data.Settlement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * What a settlement looks like, in a handful of lines.
 *
 * <p>Shared between {@code /placitum info} and shift-right-clicking the bell, because they are
 * the same question asked two ways, and a player standing at the bell should not have to know a
 * settlement's id to be told what it is doing.
 */
public final class SettlementReport {

    private SettlementReport() {}

    public static List<Component> of(Settlement settlement) {
        return of(settlement, null);
    }

    /**
     * The same, plus why nothing is being built, when there is a world to ask.
     *
     * <p>"Building nothing" on its own reads as a bug. It usually is not one - the lots are
     * taken, or too steep, or not loaded - and which of those it is decides what the player
     * should do about it.
     */
    public static List<Component> of(Settlement settlement, ServerLevel level) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(settlement.name()).withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal("  centre " + settlement.center().toShortString() + " in "
                + settlement.dimension().identifier()));
        long fields = settlement.plots().values().stream()
                .filter(p -> p.kind() == PlotKind.FARM).count();
        lines.add(Component.literal("  " + settlement.houseCount() + " house(s), " + fields
                + " field(s), " + settlement.grid().countOf(CellState.ROAD) + " road cell(s)"));

        if (settlement.buildQueue().isEmpty()) {
            lines.add(Component.literal("  building nothing").withStyle(ChatFormatting.GRAY));
            if (level != null) {
                lines.add(Component.literal("  lots within " + TownPlan.radius(settlement)
                        + " cell(s): " + Lots.describe(Lots.tally(level, settlement)))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        for (BuildJob job : settlement.buildQueue()) {
            int total = BuildPlanner.expand(job.recipe()).size();
            lines.add(Component.literal("  building " + job.recipe().template().getPath() + "  "
                    + job.progress() + "/" + total + " at "
                    + job.recipe().anchor().toShortString()));
        }
        return List.copyOf(lines);
    }
}
