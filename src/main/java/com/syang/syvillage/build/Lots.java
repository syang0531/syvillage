package com.syang.syvillage.build;

import com.syang.syvillage.config.SyVillageConfig;
import com.syang.syvillage.data.CellPos;
import com.syang.syvillage.data.CellState;
import com.syang.syvillage.data.Plot;
import com.syang.syvillage.data.Settlement;
import java.util.EnumMap;
import java.util.Map;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Whether a lot can be built on, and if not, which of the reasons it is.
 *
 * <p>Roads and lamps go down on any ground a person can walk to - a village on a hillside still
 * needs streets, and light is what keeps the mobs out. A building is different: it needs flat
 * ground, and if the ground is not flat the settlement waits rather than terracing the hill.
 * Cutting terrain is how a mod starts looking like griefing.
 *
 * <p>Waiting is not giving up. A lot rejected today is checked again, so a player who levels a
 * slope gets a house on it - which is a much better way to direct a village than any command.
 *
 * <p>The verdict is an enum rather than a boolean because of the most expensive thing this
 * project has learnt: "nothing is happening" always has more than one explanation, and a tool
 * that will not say which one it is costs a day every time. See docs/why-the-reset.md.
 */
public final class Lots {

    /** Why a lot is or is not available. One value per reason, never a bare false. */
    public enum Verdict {
        /** Free, flat, loaded, dry. Build here. */
        OK,
        /** The settlement has already built on it. */
        TAKEN,
        /** The player forbade it. */
        FORBIDDEN,
        /**
         * Nobody has held the village head's table here yet, so the settlement lights its
         * ground and builds nothing on it. Place the table; a spare villager takes the job.
         */
        WAITING_FOR_A_HEAD,
        /** Off the edge of what is loaded; it comes round again when somebody walks over. */
        UNLOADED,
        /** Somebody is standing on it - us, the player, or the village vanilla generated. */
        BUILT_ON,
        /** Water. A house in a pond is not a house. */
        WATER,
        /** Too steep. Level it and the settlement picks it up. */
        STEEP,
        /**
         * Flat, dry, empty - and there is no way to walk to it from the bell. An island, or a
         * shelf above a cliff. Bridge it or ramp it and the settlement builds there.
         */
        UNREACHABLE
    }

    private Lots() {}

    /**
     * Whether the plan may consider this lot at all, before anybody looks at the ground.
     *
     * <p>Two refusals, and they are different in kind. A lot already built on is a fact about
     * the settlement's own record. A forbidden lot is the player's veto, and the point of it is
     * that it outlives the survey that would otherwise hand the same ground back next tick and
     * start the argument over again.
     */
    public static boolean available(Settlement settlement, CellPos cell) {
        return record(settlement, cell) == Verdict.OK;
    }

    /** The part of the verdict that needs no world: the settlement's own record. */
    private static Verdict record(Settlement settlement, CellPos cell) {
        if (settlement.grid().stateAt(cell) == CellState.FORBIDDEN) {
            return Verdict.FORBIDDEN;
        }
        for (Plot plot : settlement.plots().values()) {
            if (plot.anchor().equals(cell)) {
                return Verdict.TAKEN;
            }
        }
        return Verdict.OK;
    }

    /** Every column of the lot is loaded, clear, dry, level, and somewhere you can walk to. */
    public static boolean buildable(ServerLevel level, Settlement settlement, CellPos cell,
            Reach reach) {
        return verdict(level, settlement, cell, reach) == Verdict.OK;
    }

    /** The whole answer: the record first, then the ground, then whether you can get there. */
    public static Verdict verdict(ServerLevel level, Settlement settlement, CellPos cell,
            Reach reach) {
        Verdict onPaper = record(settlement, cell);
        if (onPaper != Verdict.OK) {
            return onPaper;
        }
        if (!settlement.headed()) {
            return Verdict.WAITING_FOR_A_HEAD;
        }
        BlockPos corner = TownPlan.lotCorner(cell, settlement.center());
        if (!level.hasChunkAt(corner)
                || !level.hasChunkAt(corner.offset(TownPlan.LOT - 1, 0, TownPlan.LOT - 1))) {
            return Verdict.UNLOADED;
        }
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;

        boolean anyReachable = false;
        for (BlockPos column : TownPlan.lotColumns(cell, settlement.center())) {
            if (GridSurvey.builtOn(level, column.getX(), column.getZ())) {
                return Verdict.BUILT_ON;
            }
            int ground = GridSurvey.groundOrSkip(level, column.getX(), column.getZ());
            if (ground == Ground.SKIP) {
                return Verdict.WATER;
            }
            lowest = Math.min(lowest, ground);
            highest = Math.max(highest, ground);
            anyReachable |= reach.has(column);
        }
        if (highest - lowest > SyVillageConfig.MAX_CELL_SLOPE.get()) {
            return Verdict.STEEP;
        }
        // One column is enough: the lot is flat, so every column of it is at the same height and
        // they are all connected to each other. What is being asked is whether the lot as a
        // whole joins the rest of the town, not whether each square metre does separately.
        return anyReachable ? Verdict.OK : Verdict.UNREACHABLE;
    }

    /**
     * Every lot of the plan, counted by what is wrong with it.
     *
     * <p>This is what a settlement says when it has stopped building. "Nothing is happening" is
     * not an answer; "forty are taken, eleven are too steep, and ninety are not loaded" is.
     */
    public static Map<Verdict, Integer> tally(ServerLevel level, Settlement settlement,
            Reach reach) {
        Map<Verdict, Integer> counts = new EnumMap<>(Verdict.class);
        for (CellPos cell : TownPlan.allLots(settlement)) {
            counts.merge(verdict(level, settlement, cell, reach), 1, Integer::sum);
        }
        return counts;
    }

    /** The tally as one line, reasons only, commonest first. */
    public static Component describe(Map<Verdict, Integer> counts) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (Map.Entry<Verdict, Integer> e : counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).toList()) {
            out.append(Component.literal((first ? "" : ", ") + e.getValue() + " "))
                    .append(Component.translatable("syvillage.verdict."
                            + e.getKey().name().toLowerCase(Locale.ROOT)));
            first = false;
        }
        return first ? Component.translatable("syvillage.verdict.none") : out;
    }

    /** The same tally as plain English, for the log. */
    public static String describeForLog(Map<Verdict, Integer> counts) {
        StringBuilder out = new StringBuilder();
        counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .forEach(e -> out.append(out.isEmpty() ? "" : ", ")
                        .append(e.getValue()).append(' ')
                        .append(e.getKey().name().toLowerCase(Locale.ROOT)));
        return out.isEmpty() ? "no lots in the plan at all" : out.toString();
    }

    /** The level a building on this lot stands at: the highest ground under it. */
    public static int floorOf(ServerLevel level, Settlement settlement, CellPos cell) {
        int highest = Ground.SKIP;
        for (BlockPos column : TownPlan.lotColumns(cell, settlement.center())) {
            int ground = GridSurvey.groundOrSkip(level, column.getX(), column.getZ());
            if (ground != Ground.SKIP && (highest == Ground.SKIP || ground > highest)) {
                highest = ground;
            }
        }
        return highest;
    }
}
