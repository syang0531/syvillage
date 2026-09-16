package com.syang.placitum.build;

import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.CellPos;
import com.syang.placitum.data.CellState;
import com.syang.placitum.data.Plot;
import com.syang.placitum.data.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Whether a lot can be built on.
 *
 * <p>Roads and lamps go down on any ground at all - a village on a hillside still needs streets,
 * and light is what keeps the mobs out. A building is different: it needs flat ground, and if
 * the ground is not flat the settlement waits rather than terracing the hill. Cutting terrain is
 * how a mod starts looking like griefing.
 *
 * <p>Waiting is not giving up. A lot rejected today is checked again, so a player who levels a
 * slope gets a house on it - which is a much better way to direct a village than any command.
 */
public final class Lots {

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
        if (settlement.grid().stateAt(cell) == CellState.FORBIDDEN) {
            return false;
        }
        for (Plot plot : settlement.plots().values()) {
            if (plot.anchor().equals(cell)) {
                return false;
            }
        }
        return true;
    }

    /** Every column of the lot is loaded, clear, out of the water and level with its neighbours. */
    public static boolean buildable(ServerLevel level, Settlement settlement, CellPos cell) {
        BlockPos corner = TownPlan.lotCorner(cell, settlement.center());
        if (!level.hasChunkAt(corner)
                || !level.hasChunkAt(corner.offset(TownPlan.LOT - 1, 0, TownPlan.LOT - 1))) {
            return false;
        }
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;

        for (BlockPos column : TownPlan.lotColumns(cell, settlement.center())) {
            if (GridSurvey.builtOn(level, column.getX(), column.getZ())) {
                return false;   // somebody is already there, us or the player
            }
            int ground = GridSurvey.groundOrSkip(level, column.getX(), column.getZ());
            if (ground == Ground.SKIP) {
                return false;   // water, and a house in a pond is not a house
            }
            lowest = Math.min(lowest, ground);
            highest = Math.max(highest, ground);
        }
        return highest - lowest <= PlacitumConfig.MAX_CELL_SLOPE.get();
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
