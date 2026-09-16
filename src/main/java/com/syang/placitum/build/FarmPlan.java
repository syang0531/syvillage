package com.syang.placitum.build;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.CellPos;
import com.syang.placitum.data.Settlement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

/**
 * A field, which is the half of this mod's assumption that was missing.
 *
 * <p>The bet is that a village given infrastructure grows on its own. That only holds if vanilla
 * breeding can happen, and vanilla breeding needs villagers carrying food - bread, carrots,
 * potatoes - which comes from a farmer villager harvesting a crop and sharing it. Beds without a
 * field is a village that has a first generation and no second, however many houses it is given.
 *
 * <p>Nothing tends it. A vanilla farmer claims the composter it already has, walks to the nearest
 * farmland, and does the rest - which is the whole point of not simulating any of it.
 */
public final class FarmPlan {

    public static final Identifier FIELD =
            Identifier.fromNamespaceAndPath(Placitum.MODID, "farm/field");

    /** Same footprint as a cottage, so either fits the same lot. */
    public static final int SIDE = TownPlan.BUILDING;

    private FarmPlan() {}

    /** Columns of the field, in the order the ground profile stores them. */
    public static List<BlockPos> footprint(BlockPos northWest) {
        List<BlockPos> out = new ArrayList<>(SIDE * SIDE);
        for (int dz = 0; dz < SIDE; dz++) {
            for (int dx = 0; dx < SIDE; dx++) {
                out.add(northWest.offset(dx, 0, dz));
            }
        }
        return out;
    }

    public static Optional<BuildRecipe> plan(ServerLevel level, Settlement settlement,
            CellPos cell) {
        BlockPos corner = TownPlan.buildingCorner(cell, settlement.center());
        List<Integer> profile = new ArrayList<>();
        for (BlockPos column : footprint(corner)) {
            if (!level.hasChunkAt(column)) {
                return Optional.empty();
            }
            profile.add(GridSurvey.groundOrSkip(level, column.getX(), column.getZ()));
        }
        Placitum.LOGGER.debug("Planned a field for '{}' on cell {}", settlement.name(),
                cell.toKey());
        return Optional.of(new BuildRecipe(FIELD, corner, Rotation.NONE,
                Identifier.fromNamespaceAndPath(Placitum.MODID, "biome_palette/plains"),
                List.copyOf(profile), new BlockPos(SIDE, 1, SIDE), List.of()));
    }

    /**
     * Farmland, a channel of water down the middle, wheat on top.
     *
     * <p>One level for the whole field, taken as the lowest ground under it. A field has to be
     * flat to hold water, and cutting down to the low point keeps the water in the field rather
     * than spilling out of it.
     *
     * <p>Farmland stays hydrated within four blocks and two is the furthest any part of a
     * five-wide field gets from the middle, so one row of water does the whole thing.
     */
    public static List<BuildOp> expand(BuildRecipe recipe) {
        List<Integer> profile = recipe.groundProfile();
        List<BlockPos> columns = footprint(recipe.anchor());
        if (profile.size() != columns.size()) {
            return List.of();
        }
        int floor = lowest(profile);
        if (floor == Ground.SKIP) {
            return List.of();
        }
        List<BuildOp> ops = new ArrayList<>();

        for (int i = 0; i < columns.size(); i++) {
            BlockPos column = columns.get(i);
            boolean channel = i % SIDE == SIDE / 2;

            // Clear whatever stands above the field's level, so a knoll does not leave a lump of
            // dirt in the middle of the crop.
            for (int y = floor + 1; y <= profile.get(i) + 2; y++) {
                ops.add(new BuildOp(new BlockPos(column.getX(), y, column.getZ()),
                        Blocks.AIR.defaultBlockState()));
            }
            if (channel) {
                ops.add(new BuildOp(new BlockPos(column.getX(), floor, column.getZ()),
                        Blocks.WATER.defaultBlockState()));
                continue;
            }
            ops.add(new BuildOp(new BlockPos(column.getX(), floor, column.getZ()),
                    Blocks.FARMLAND.defaultBlockState()));
            ops.add(new BuildOp(new BlockPos(column.getX(), floor + 1, column.getZ()),
                    Blocks.WHEAT.defaultBlockState()));
        }

        ops.sort(Comparator.comparingInt((BuildOp op) -> op.pos().getY())
                .thenComparingInt(op -> op.pos().getX())
                .thenComparingInt(op -> op.pos().getZ()));
        return List.copyOf(ops);
    }

    /** The lowest ground under the field, so the water has somewhere to sit. */
    private static int lowest(List<Integer> profile) {
        int best = Ground.SKIP;
        for (int height : profile) {
            if (height != Ground.SKIP && (best == Ground.SKIP || height < best)) {
                best = height;
            }
        }
        return best;
    }
}
