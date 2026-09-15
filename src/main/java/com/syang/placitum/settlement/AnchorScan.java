package com.syang.placitum.settlement;

import com.syang.placitum.Placitum;
import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.AnchorSet;
import com.syang.placitum.data.Plot;
import com.syang.placitum.data.PlotKind;
import com.syang.placitum.data.Settlement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

/**
 * Works out where a settlement's shelters, watch points and muster point are.
 *
 * <p>Runs only while the settlement has bodies, because it reads POIs and POIs need loaded
 * chunks. The result is stored on the settlement so the virtual side never has to ask.
 *
 * <p>Plots win when construction has built any. Until then the answers come from the vanilla
 * village that was registered - the beds villagers already sleep in, and the bell they already
 * gather at. That is not a placeholder so much as an admission: a village that has stood for a
 * hundred in-game days already knows where its people sleep, and ignoring that to wait for M3
 * would make the first two milestones untestable.
 */
public final class AnchorScan {

    private AnchorScan() {}

    public static AnchorSet scan(ServerLevel level, Settlement settlement) {
        List<BlockPos> shelters = shelters(level, settlement);
        BlockPos centre = settlement.center();

        AnchorSet scanned = new AnchorSet(
                shelters,
                watchPoints(settlement, shelters, centre),
                Optional.of(muster(settlement, centre)),
                shelters.size(),
                level.getGameTime());

        Placitum.LOGGER.debug("Anchors for '{}': {} shelter(s), {} watch point(s), muster {}",
                settlement.name(), scanned.shelters().size(), scanned.watchPoints().size(),
                scanned.muster().map(BlockPos::toShortString).orElse("none"));
        return scanned;
    }

    /** Somewhere with a roof. Beds are the only reliable marker a vanilla village offers. */
    private static List<BlockPos> shelters(ServerLevel level, Settlement settlement) {
        List<BlockPos> fromPlots = new ArrayList<>();
        for (Plot plot : settlement.plots().values()) {
            if (plot.kind() == PlotKind.HOUSE && plot.bedCount() > 0) {
                fromPlots.add(settlement.grid().blockAt(plot.anchor()));
            }
        }
        if (!fromPlots.isEmpty()) {
            return List.copyOf(fromPlots);
        }

        int radius = settlement.identity().claimRadiusChunks() * 16;
        PoiManager poi = level.getPoiManager();
        List<BlockPos> beds = poi
                .getInRange(holder -> holder.is(PoiTypes.HOME), settlement.center(), radius,
                        PoiManager.Occupancy.ANY)
                .map(record -> record.getPos().immutable())
                .sorted(Comparator.comparingInt((BlockPos p) -> p.getX())
                        .thenComparingInt(BlockPos::getZ)
                        .thenComparingInt(BlockPos::getY))
                .toList();
        return beds;
    }

    /**
     * Where to look out from.
     *
     * <p>Watchtowers and gates are the design's answer and neither exists yet, so the outermost
     * shelters stand in: the edge of where people live is where trouble arrives first. Cost
     * still scales with the number of points rather than with claim area, which is the rule
     * that actually matters.
     */
    private static List<BlockPos> watchPoints(Settlement settlement, List<BlockPos> shelters,
            BlockPos centre) {
        List<BlockPos> points = new ArrayList<>();
        points.add(centre);

        int wanted = PlacitumConfig.MAX_WATCH_POINTS.get() - 1;
        if (wanted > 0 && !shelters.isEmpty()) {
            List<BlockPos> byDistance = new ArrayList<>(shelters);
            byDistance.sort(Comparator.comparingDouble((BlockPos p) -> p.distSqr(centre)).reversed());
            for (BlockPos pos : byDistance) {
                if (points.size() > wanted) {
                    break;
                }
                if (farEnoughFromAll(points, pos)) {
                    points.add(pos);
                }
            }
        }
        return List.copyOf(points);
    }

    /** Keeps watch points spread out; two towers in the same courtyard watch the same place. */
    private static boolean farEnoughFromAll(List<BlockPos> chosen, BlockPos candidate) {
        int spacing = PlacitumConfig.WATCH_RADIUS.get();
        for (BlockPos pos : chosen) {
            if (pos.distSqr(candidate) < (double) spacing * spacing) {
                return false;
            }
        }
        return true;
    }

    /**
     * Where the militia forms up.
     *
     * <p>The design sends them through the armoury so the entity swap happens out of sight. The
     * bell does the same job and says the same thing - ringing it is how the player raises the
     * alarm, so gathering there is what a player already expects to see.
     */
    private static BlockPos muster(Settlement settlement, BlockPos centre) {
        for (Plot plot : settlement.plots().values()) {
            if (plot.kind() == PlotKind.ARMORY) {
                return settlement.grid().blockAt(plot.anchor());
            }
        }
        return centre;
    }
}
