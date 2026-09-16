package com.syang.placitum.build;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.CellPos;
import com.syang.placitum.data.CellState;
import com.syang.placitum.data.PlotGrid;
import com.syang.placitum.data.Settlement;
import java.util.Comparator;
import com.syang.placitum.config.PlacitumConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

/**
 * Chooses where a house goes and freezes the ground under it.
 *
 * <p>Site selection is the whole of a settlement's shape. docs/construction.md puts it in one
 * line - a free cell, next to a road, nearest the centre - and the middle condition is the one
 * that matters: without it houses scatter to wherever there happens to be flat ground and the
 * village stops looking like a village.
 */
public final class HousePlanner {

    public static final Identifier COTTAGE =
            Identifier.fromNamespaceAndPath(Placitum.MODID, "house/cottage");

    private HousePlanner() {}

    /** The best free cell touching a road, by distance alone. */
    public static Optional<CellPos> findSite(Settlement settlement) {
        List<CellPos> sites = findSites(settlement);
        return sites.isEmpty() ? Optional.empty() : Optional.of(sites.getFirst());
    }

    /**
     * Every free cell touching a road, nearest the centre first.
     *
     * <p>A list rather than a winner, because distance across the map is only half of what makes
     * a site good and the other half needs the world. Ordering is settled here from stored data;
     * the planner walks the list and applies what only loaded chunks can answer.
     */
    public static List<CellPos> findSites(Settlement settlement) {
        PlotGrid grid = settlement.grid();
        int mapRadius = (grid.size() - 1) / 2;
        // The tier is a budget on how far a settlement may spread - but never tighter than its
        // own edge plus one. An OUTPOST allowed three cells around the bell is allowed the
        // village square it was adopted with and nothing else: one house fits, and then beds
        // cannot grow, so population cannot grow, so the tier cannot rise, so the radius cannot
        // widen. A settlement may always build just outside itself.
        int buildRadius = Math.min(mapRadius,
                Math.max(PlacitumConfig.BUILD_RADIUS_CELLS.get(), occupiedRadius(grid) + 1));

        List<CellPos> sites = new ArrayList<>();
        for (int gz = -buildRadius; gz <= buildRadius; gz++) {
            for (int gx = -buildRadius; gx <= buildRadius; gx++) {
                CellPos cell = new CellPos(gx, gz);
                if (grid.stateAt(cell) == CellState.FREE && touchesRoad(grid, cell)) {
                    sites.add(cell);
                }
            }
        }
        sites.sort(Comparator.comparingInt(c -> c.gx() * c.gx() + c.gz() * c.gz()));
        return List.copyOf(sites);
    }

    /** How far out the settlement already reaches, in cells. */
    private static int occupiedRadius(PlotGrid grid) {
        int radius = 0;
        for (var entry : grid.cells().entrySet()) {
            CellState state = entry.getValue();
            if (state != CellState.BUILT && state != CellState.ROAD) {
                continue;
            }
            CellPos cell = entry.getKey();
            radius = Math.max(radius, Math.max(Math.abs(cell.gx()), Math.abs(cell.gz())));
        }
        return radius;
    }

    /**
     * Plans a cottage on the best free site.
     *
     * <p>Empty means there is nowhere to put one - every cell near a road is taken, blocked or
     * outside what this tier may build on. That is a real answer and the caller reports it,
     * because "the village stopped growing" with no reason given is the complaint this whole mod
     * exists to answer.
     */
    /** Nothing already standing on the footprint, our own wall included. */
    private static boolean clear(ServerLevel level, BlockPos corner) {
        for (int dx = 0; dx < CottagePlan.SIDE; dx++) {
            for (int dz = 0; dz < CottagePlan.SIDE; dz++) {
                if (GridSurvey.builtOn(level, corner.getX() + dx, corner.getZ() + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The best free site, judged by distance first and level ground second.
     *
     * <p>Shared with fields, because a field wants the same thing a house does: near the centre,
     * beside a road, and on ground the village is actually standing on. Site selection used to
     * measure distance alone and put every cottage eighteen blocks down a slope, out of sight.
     *
     * @param usable what the caller needs of the ground, checked once a candidate is chosen
     */
    public static Optional<CellPos> pickSite(ServerLevel level, Settlement settlement,
            java.util.function.BiPredicate<ServerLevel, BlockPos> usable) {
        int bellGround = GridSurvey.groundAt(level, settlement.center().getX(),
                settlement.center().getZ());
        int maxDrop = PlacitumConfig.MAX_SITE_DROP.get();

        CellPos chosen = null;
        int chosenDrop = Integer.MAX_VALUE;
        for (CellPos candidate : findSites(settlement)) {
            BlockPos corner = settlement.grid().blockAt(candidate);
            if (!level.hasChunkAt(corner) || !usable.test(level, corner)) {
                continue;
            }
            int drop = Math.abs(GridSurvey.groundAt(level, corner.getX() + CottagePlan.SIDE / 2,
                    corner.getZ() + CottagePlan.SIDE / 2) - bellGround);
            if (drop <= maxDrop) {
                return Optional.of(candidate);   // level enough, and nearest first
            }
            if (drop < chosenDrop) {
                // Kept only so a settlement on genuinely broken ground still builds somewhere
                // rather than stopping, but the flattest option rather than the closest.
                chosen = candidate;
                chosenDrop = drop;
            }
        }
        if (chosen != null) {
            Placitum.LOGGER.info("'{}' has no level ground left; building {} block(s) off the"
                    + " bell's level", settlement.name(), chosenDrop);
        }
        return Optional.ofNullable(chosen);
    }

    public static Optional<BuildRecipe> plan(ServerLevel level, Settlement settlement) {
        Optional<CellPos> site = pickSite(level, settlement, HousePlanner::clear);
        if (site.isEmpty()) {
            return Optional.empty();
        }
        BlockPos northWest = settlement.grid().blockAt(site.get());
        List<Integer> profile = new ArrayList<>();
        Rotation facing = towardsRoad(settlement, site.get());
        for (BlockPos column : CottagePlan.footprint(northWest, facing)) {
            profile.add(level.hasChunkAt(column)
                    ? GridSurvey.groundOrSkip(level, column.getX(), column.getZ())
                    : Ground.SKIP);
        }
        if (unreadable(profile)) {
            Placitum.LOGGER.debug("No house for '{}': the site at {} could not be read",
                    settlement.name(), site.get().toKey());
            return Optional.empty();
        }

        int low = profile.stream().mapToInt(Integer::intValue).min().orElse(0);
        int high = profile.stream().mapToInt(Integer::intValue).max().orElse(0);
        Placitum.LOGGER.info("Planned a cottage for '{}' on cell {} at {}: ground {}..{},"
                        + " floor {}, door {}", settlement.name(), site.get().toKey(),
                northWest.toShortString(), low, high, high, CottagePlan.doorFacing(facing));
        return Optional.of(new BuildRecipe(
                COTTAGE,
                northWest,
                facing,
                Identifier.fromNamespaceAndPath(Placitum.MODID, "biome_palette/plains"),
                List.copyOf(profile),
                new BlockPos(CottagePlan.SIDE, CottagePlan.HEIGHT, CottagePlan.SIDE),
                List.of()));
    }

    /** Which cell a house sits on, for registering the plot when it is finished. */
    public static Optional<CellPos> siteOf(Settlement settlement, BuildRecipe recipe) {
        return Optional.of(settlement.grid().cellAt(recipe.anchor()));
    }

    private static boolean unreadable(List<Integer> profile) {
        for (int height : profile) {
            if (height == Ground.SKIP) {
                return true;   // a house half on ground nobody has seen is not a house
            }
        }
        return false;
    }

    /**
     * The door faces the road it is next to.
     *
     * <p>Not cosmetic. A door on the far side means every resident walks round the house to get
     * in, which is what vanilla villages look like when their paths were laid after the fact.
     */
    private static Rotation towardsRoad(Settlement settlement, CellPos cell) {
        PlotGrid grid = settlement.grid();
        if (grid.stateAt(new CellPos(cell.gx(), cell.gz() - 1)) == CellState.ROAD) {
            return Rotation.NONE;             // north
        }
        if (grid.stateAt(new CellPos(cell.gx() + 1, cell.gz())) == CellState.ROAD) {
            return Rotation.CLOCKWISE_90;     // east
        }
        if (grid.stateAt(new CellPos(cell.gx(), cell.gz() + 1)) == CellState.ROAD) {
            return Rotation.CLOCKWISE_180;    // south
        }
        return Rotation.COUNTERCLOCKWISE_90;  // west
    }

    private static boolean touchesRoad(PlotGrid grid, CellPos cell) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            CellPos next = new CellPos(cell.gx() + side.getStepX(), cell.gz() + side.getStepZ());
            if (grid.stateAt(next) == CellState.ROAD) {
                return true;
            }
        }
        return false;
    }
}
