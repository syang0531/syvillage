package com.syang.placitum.settlement;

import com.syang.placitum.data.Assignment;
import com.syang.placitum.data.ChronicleEntry;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.population.Capacity;
import com.syang.placitum.sim.SimParams;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/**
 * What the bell tells you.
 *
 * <p>docs/population.md asks for a screen here. 26.2 rebuilt the GUI around render-state
 * extraction - GuiGraphics and drawString are gone - and learning that well enough to lay out a
 * panel is a job of its own, so this reports through chat for now. The graphical version is
 * recorded as outstanding rather than quietly dropped.
 *
 * <p>What matters is unchanged, and it is not the panel: the three capacity figures side by
 * side with the binding one marked, and the recent dead with their causes. That is the answer
 * to "why is my village not growing" and "what happened while I was away", which is the whole
 * of what this milestone owes the player.
 */
public final class SettlementReport {

    private static final int CHRONICLE_LINES = 8;

    private SettlementReport() {}

    public static List<Component> of(Settlement settlement, SimParams params) {
        Capacity capacity = Capacity.of(settlement, params);
        List<Component> out = new ArrayList<>();

        out.add(Component.literal(settlement.name() + "  (" + settlement.scale() + ")")
                .withStyle(ChatFormatting.GOLD));
        out.add(Component.literal("  population " + settlement.population()
                + " of " + capacity.value()));

        // The line the whole milestone is for.
        out.add(Component.literal("  " + bar("beds", capacity.beds(), capacity, Capacity.Bottleneck.BEDS)
                + bar("food", capacity.food(), capacity, Capacity.Bottleneck.FOOD)
                + bar("safety", capacity.safety(), capacity, Capacity.Bottleneck.SAFETY)));

        int farmers = count(settlement, Assignment.FARMER);
        int production = farmers * params.yieldRate();
        int consumption = settlement.population() * params.consumptionPerHead();
        int net = production - consumption;
        out.add(Component.literal("  stores " + settlement.stockOf(Items.WHEAT) + " wheat ("
                + (net >= 0 ? "+" : "") + net + "/step from " + farmers + " farmer(s))")
                .withStyle(net >= 0 ? ChatFormatting.WHITE : ChatFormatting.RED));

        int armed = 0;
        for (Resident r : settlement.residents()) {
            if (r.gear().armed()) {
                armed++;
            }
        }
        out.add(Component.literal("  alert " + settlement.alert() + ", " + armed + " under arms")
                .withStyle(settlement.alert() == com.syang.placitum.data.AlertState.PEACE
                        ? ChatFormatting.GRAY : ChatFormatting.RED));

        List<ChronicleEntry> recent = settlement.chronicle().recent(CHRONICLE_LINES);
        if (recent.isEmpty()) {
            out.add(Component.literal("  nothing has happened here yet")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return out;
        }
        out.add(Component.literal("  recently:").withStyle(ChatFormatting.GOLD));
        for (ChronicleEntry entry : recent) {
            out.add(Component.literal("    " + age(settlement.lastSimTick(), entry.gameTime())
                    + "  " + entry.subject()
                    + (entry.detail().isEmpty() ? "" : " - " + entry.detail()))
                    .withStyle(colour(entry)));
        }
        return out;
    }

    /** Marks the binding constraint. Three numbers without it is the same puzzle as before. */
    private static String bar(String label, int value, Capacity capacity,
            Capacity.Bottleneck kind) {
        boolean binding = capacity.bottleneck() == kind;
        return label + " " + value + (binding ? " <-  " : "   ");
    }

    private static String age(long now, long then) {
        long ticks = Math.max(0, now - then);
        long days = ticks / 24000L;
        return days > 0 ? days + "d" : Math.max(1, ticks / 1200L) + "m";
    }

    private static ChatFormatting colour(ChronicleEntry entry) {
        return switch (entry.type()) {
            case DEATH, RAID_LOST, FAMINE, ZOMBIFIED -> ChatFormatting.RED;
            case BIRTH, RAID_REPELLED, CURED, SCALE_UP -> ChatFormatting.GREEN;
            default -> ChatFormatting.GRAY;
        };
    }

    private static int count(Settlement settlement, net.minecraft.resources.Identifier job) {
        int n = 0;
        for (Resident r : settlement.residents()) {
            if (r.counts() && r.assignment().job().equals(job)) {
                n++;
            }
        }
        return n;
    }
}
