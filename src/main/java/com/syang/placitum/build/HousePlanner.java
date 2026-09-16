package com.syang.placitum.build;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.CellPos;
import com.syang.placitum.data.CellState;
import com.syang.placitum.data.PlotGrid;
import com.syang.placitum.data.ScaleTier;
import com.syang.placitum.data.Settlement;
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

    /** A free cell touching a road, or nothing. */
    public static Optional<CellPos> findSite(Settlement settlement) {
        PlotGrid grid = settlement.grid();
        int mapRadius = (grid.size() - 1) / 2;
        // The tier is a budget on how far a settlement may spread - but never tighter than its
        // own edge plus one. An OUTPOST allowed three cells around the bell is allowed the
        // village square it was adopted with and nothing else: one house fits, and then beds
        // cannot grow, so population cannot grow, so the tier cannot rise, so the radius cannot
        // widen. A settlement may always build just outside itself.
        int buildRadius = Math.min(mapRadius,
                Math.max(settlement.scale().buildRadiusCells(), occupiedRadius(grid) + 1));

        CellPos best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int gz = -buildRadius; gz <= buildRadius; gz++) {
            for (int gx = -buildRadius; gx <= buildRadius; gx++) {
                CellPos cell = new CellPos(gx, gz);
                if (grid.stateAt(cell) != CellState.FREE || !touchesRoad(grid, cell)) {
                    continue;
                }
                int distance = gx * gx + gz * gz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = cell;
                }
            }
        }
        return Optional.ofNullable(best);
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
    public static Optional<BuildRecipe> plan(ServerLevel level, Settlement settlement) {
        Optional<CellPos> site = findSite(settlement);
        if (site.isEmpty()) {
            return Optional.empty();
        }
        BlockPos northWest = settlement.grid().blockAt(site.get());
        List<Integer> profile = new ArrayList<>();
        for (BlockPos column : CottagePlan.footprint(northWest)) {
            if (!level.hasChunkAt(column)) {
                profile.add(WallGeometry.SKIP);
                continue;
            }
            // The grid said this cell was free and the world is the one that knows. A roof
            // reads as ground, so without this the second house goes on top of the first.
            if (GridSurvey.builtOn(level, column.getX(), column.getZ())) {
                Placitum.LOGGER.debug("No house for '{}': cell {} already has something on it",
                        settlement.name(), site.get().toKey());
                return Optional.empty();
            }
            profile.add(GridSurvey.groundOrSkip(level, column.getX(), column.getZ()));
        }
        if (unreadable(profile)) {
            Placitum.LOGGER.debug("No house for '{}': the site at {} could not be read",
                    settlement.name(), site.get().toKey());
            return Optional.empty();
        }

        Rotation facing = towardsRoad(settlement, site.get());
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
            if (height == WallGeometry.SKIP) {
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
