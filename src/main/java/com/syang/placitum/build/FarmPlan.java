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
 * A field, which is what makes the rest of this mod's assumption true.
 *
 * <p>The bet is that a village given infrastructure grows on its own. That only holds if vanilla
 * breeding can happen, and vanilla breeding needs villagers carrying food - bread, carrots,
 * potatoes - which comes from a farmer villager harvesting a crop and sharing it. Beds without a
 * field is a village that will never have a second generation, however many houses it is given.
 *
 * <p>So this is not decoration. It is the half of the hypothesis that was missing.
 *
 * <p>Nothing tends it. A vanilla farmer villager claims the composter it already has, walks to
 * the nearest farmland, and does the rest - which is the whole point of not simulating any of it.
 */
public final class FarmPlan {

    public static final Identifier FIELD =
            Identifier.fromNamespaceAndPath(Placitum.MODID, "farm/field");

    /** Seven a side, like a cottage, so both fit one cell with a block to spare. */
    public static final int SIDE = 7;

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

    /**
     * Plans a field on the best free site, level with the village.
     *
     * <p>Shares site selection with houses, so a field lands beside a road like everything else -
     * a farmer has to be able to walk to it, and a field behind three houses is a field nobody
     * tends.
     */
    public static Optional<BuildRecipe> plan(ServerLevel level, Settlement settlement) {
        Optional<CellPos> site = HousePlanner.pickSite(level, settlement, FarmPlan::readable);
        if (site.isEmpty()) {
            return Optional.empty();
        }
        BlockPos northWest = settlement.grid().blockAt(site.get());
        List<Integer> profile = new ArrayList<>();
        for (BlockPos column : footprint(northWest)) {
            profile.add(GridSurvey.groundOrSkip(level, column.getX(), column.getZ()));
        }
        Placitum.LOGGER.debug("Planned a field for '{}' on cell {}", settlement.name(),
                site.get().toKey());
        return Optional.of(new BuildRecipe(FIELD, northWest, Rotation.NONE,
                Identifier.fromNamespaceAndPath(Placitum.MODID, "biome_palette/plains"),
                List.copyOf(profile), new BlockPos(SIDE, 1, SIDE), List.of()));
    }

    /** Every column has to be readable: a field half on unseen ground is a field full of holes. */
    private static boolean readable(ServerLevel level, BlockPos northWest) {
        for (BlockPos column : footprint(northWest)) {
            if (!level.hasChunkAt(column)
                    || GridSurvey.builtOn(level, column.getX(), column.getZ())
                    || GridSurvey.groundOrSkip(level, column.getX(), column.getZ())
                            == Ground.SKIP) {
                return false;
            }
        }
        return true;
    }

    /**
     * Farmland, water down the middle, wheat on top.
     *
     * <p>One level for the whole field, taken as the lowest ground under it. A field has to be
     * flat to hold water, and cutting down to the low point means the water sits in the field
     * rather than spilling out of it.
     *
     * <p>The water is a single row through the centre. Farmland stays hydrated within four
     * blocks, and three is the furthest any part of a seven-wide field gets from the middle.
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
            boolean middle = i % SIDE == SIDE / 2;

            // Clear whatever stands above the field's level, so a knoll does not leave a lump of
            // dirt in the middle of the crop.
            for (int y = floor + 1; y <= profile.get(i) + 2; y++) {
                ops.add(new BuildOp(new BlockPos(column.getX(), y, column.getZ()),
                        Blocks.AIR.defaultBlockState()));
            }
            if (middle) {
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
