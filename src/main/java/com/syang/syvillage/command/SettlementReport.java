package com.syang.syvillage.command;

import com.syang.syvillage.build.BuildPlanner;
import com.syang.syvillage.build.Lots;
import com.syang.syvillage.build.Reach;
import com.syang.syvillage.build.TownPlan;
import com.syang.syvillage.data.BuildJob;
import com.syang.syvillage.data.CellState;
import com.syang.syvillage.data.PlotKind;
import com.syang.syvillage.data.Settlement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * What a settlement looks like, in a handful of lines.
 *
 * <p>Shared between {@code /syvillage info} and shift-right-clicking the bell, because they are
 * the same question asked two ways, and a player standing at the bell should not have to know a
 * settlement's id to be told what it is doing.
 *
 * <p>Every line is a translation key under {@code syvillage.report.*}: this is the face of
 * principle 12, and a player who reads Korean should not be told "3 steep, 1 water".
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
        lines.add(Component.translatable("syvillage.report.centre",
                settlement.center().toShortString(), settlement.dimension().identifier().toString()));
        long fields = settlement.plots().values().stream()
                .filter(p -> p.kind() == PlotKind.FARM).count();
        lines.add(Component.translatable("syvillage.report.counts", settlement.houseCount(), fields,
                settlement.grid().countOf(CellState.ROAD)));
        lines.add(Component.translatable("syvillage.report.stage",
                Component.translatable("syvillage.stage." + settlement.stage().getSerializedName()),
                Component.translatable("syvillage.craft." + settlement.craft().getSerializedName())));

        if (settlement.buildQueue().isEmpty()) {
            lines.add(Component.translatable("syvillage.report.building_nothing")
                    .withStyle(ChatFormatting.GRAY));
            if (level != null) {
                lines.add(Component.translatable("syvillage.report.lots",
                        TownPlan.maxPhase(settlement),
                        TownPlan.reachOf(settlement, TownPlan.outerPhase(settlement)),
                        Lots.describe(Lots.tally(level, settlement,
                                Reach.from(level, settlement, TownPlan.outerPhase(settlement)))))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        for (BuildJob job : settlement.buildQueue()) {
            lines.add(jobLine(job));
        }
        return List.copyOf(lines);
    }

    /** One job of the queue: what, how far along, and where. */
    public static Component jobLine(BuildJob job) {
        int total = BuildPlanner.expand(job.recipe()).size();
        return Component.translatable("syvillage.report.building", job.recipe().template().getPath(),
                job.progress(), total, job.recipe().anchor().toShortString());
    }
}
