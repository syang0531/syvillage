package com.syang.placitum.build;

import com.syang.placitum.Placitum;
import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.CellPos;
import com.syang.placitum.data.CellState;
import com.syang.placitum.data.PlotGrid;
import com.syang.placitum.data.Settlement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

/**
 * Street lamps, which are the answer to the question this mod was started to ask.
 *
 * <p>The original complaint was that mobs kill villagers at night. The first design answered it
 * with a raid simulation - alert states, militia, defence ratings, threat rolls - and spent days
 * being balanced while the actual mobs went on spawning inside the walls, because a wall does
 * not make light and light is what decides whether a mob spawns.
 *
 * <p>A lamp does. This is a smaller idea and a better one: it is visible, it is a block, and a
 * player can see whether it worked by standing there after dark.
 */
public final class LampPlan {

    public static final Identifier LAMPS =
            Identifier.fromNamespaceAndPath(Placitum.MODID, "light/lamps");

    /** Marks a column the lamp pass skips - water, a building, or already bright enough. */
    private static final int SKIP = WallGeometry.SKIP;

    private LampPlan() {}

    /**
     * One lamp per dark cell, on the cell's centre.
     *
     * <p>A lamp every cell is denser than a player would place them by hand, and that is the
     * point: the light level has to hold up everywhere, not on average. Cells that are already
     * lit are left alone, so this converges rather than lighting the same ground for ever.
     */
    public static List<CellPos> darkCells(ServerLevel level, Settlement settlement) {
        PlotGrid grid = settlement.grid();
        int radius = Math.min((grid.size() - 1) / 2, PlacitumConfig.BUILD_RADIUS_CELLS.get());
        int wanted = PlacitumConfig.MIN_LIGHT_LEVEL.get();
        List<CellPos> dark = new ArrayList<>();

        for (int gz = -radius; gz <= radius; gz++) {
            for (int gx = -radius; gx <= radius; gx++) {
                CellPos cell = new CellPos(gx, gz);
                CellState state = grid.stateAt(cell);
                if (state == CellState.BLOCKED || state == CellState.FORBIDDEN) {
                    continue;
                }
                BlockPos centre = grid.centreOf(cell);
                if (!level.hasChunkAt(centre)) {
                    continue;
                }
                BlockPos ground = new BlockPos(centre.getX(),
                        GridSurvey.groundAt(level, centre.getX(), centre.getZ()) + 1,
                        centre.getZ());
                // Block light only. Counting sunlight would call every cell bright at noon and
                // dark at midnight, which says nothing about where mobs can spawn tonight.
                if (level.getBrightness(LightLayer.BLOCK, ground) < wanted) {
                    dark.add(cell);
                }
            }
        }
        return List.copyOf(dark);
    }

    /**
     * Plans lamps for the cells that are dark, or nothing when the village is already lit.
     *
     * <p>Empty is the normal answer once a settlement has been standing a while, which is what
     * makes this safe to ask on every pass.
     */
    public static Optional<BuildRecipe> plan(ServerLevel level, Settlement settlement) {
        List<CellPos> dark = darkCells(level, settlement);
        if (dark.isEmpty()) {
            return Optional.empty();
        }
        int batch = Math.min(dark.size(), PlacitumConfig.LAMPS_PER_JOB.get());
        List<Integer> profile = new ArrayList<>();
        List<BlockPos> posts = new ArrayList<>();

        for (int i = 0; i < batch; i++) {
            BlockPos centre = settlement.grid().centreOf(dark.get(i));
            posts.add(centre);
            boolean blocked = GridSurvey.builtOn(level, centre.getX(), centre.getZ())
                    || settlement.onWall(centre);
            profile.add(blocked ? SKIP
                    : GridSurvey.groundOrSkip(level, centre.getX(), centre.getZ()));
        }
        if (profile.stream().allMatch(h -> h == SKIP)) {
            return Optional.empty();
        }
        Placitum.LOGGER.debug("'{}' has {} dark cell(s); lighting {}", settlement.name(),
                dark.size(), batch);

        // The posts are the anchor list: a lamp job has no single corner to measure from, so the
        // first post anchors it and the profile carries the rest in order.
        return Optional.of(new BuildRecipe(LAMPS, posts.getFirst(), Rotation.NONE,
                Identifier.fromNamespaceAndPath(Placitum.MODID, "biome_palette/plains"),
                List.copyOf(profile), new BlockPos(batch, 0, 0), encode(posts)));
    }

    /** Post positions ride in the gate list, which is just a list of ints on the wire. */
    private static List<Integer> encode(List<BlockPos> posts) {
        List<Integer> out = new ArrayList<>(posts.size() * 2);
        for (BlockPos post : posts) {
            out.add(post.getX());
            out.add(post.getZ());
        }
        return List.copyOf(out);
    }

    /**
     * A fence post with a lantern on top.
     *
     * <p>Not a torch: a torch on the ground is knocked off by anything walking into it and a
     * village that relights itself every morning is a village that was dark all night. The post
     * also puts the light a block higher, which is what stops the ground beside it spawning.
     */
    public static List<BuildOp> expand(BuildRecipe recipe) {
        List<Integer> profile = recipe.groundProfile();
        List<Integer> coords = recipe.gates();
        if (coords.size() != profile.size() * 2) {
            return List.of();
        }
        List<BuildOp> ops = new ArrayList<>();
        for (int i = 0; i < profile.size(); i++) {
            if (profile.get(i) == SKIP) {
                continue;
            }
            int x = coords.get(i * 2);
            int z = coords.get(i * 2 + 1);
            int ground = profile.get(i);
            ops.add(new BuildOp(new BlockPos(x, ground + 1, z),
                    Blocks.OAK_FENCE.defaultBlockState()));
            ops.add(new BuildOp(new BlockPos(x, ground + 2, z),
                    Blocks.OAK_FENCE.defaultBlockState()));
            ops.add(new BuildOp(new BlockPos(x, ground + 3, z),
                    Blocks.LANTERN.defaultBlockState()));
        }
        ops.sort(Comparator.comparingInt((BuildOp op) -> op.pos().getY())
                .thenComparingInt(op -> op.pos().getX())
                .thenComparingInt(op -> op.pos().getZ()));
        return List.copyOf(ops);
    }
}
