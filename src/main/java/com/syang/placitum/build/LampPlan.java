package com.syang.placitum.build;

import com.syang.placitum.Placitum;
import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.BuildRecipe;
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
 * <p>The complaint was mobs killing villagers at night. The first design answered it with a raid
 * simulation and spent days being balanced while the actual mobs went on spawning inside the
 * walls, because a wall does not make light and light is what decides whether a mob spawns.
 *
 * <p>Lamps stand in the margin between street and lot - never on a lot, which was the previous
 * version's mistake: lit so densely there was nowhere left to build.
 */
public final class LampPlan {

    public static final Identifier LAMPS =
            Identifier.fromNamespaceAndPath(Placitum.MODID, "light/lamps");

    private LampPlan() {}

    /**
     * Posts of the plan with nothing standing on them yet.
     *
     * <p>Every post gets a lamp, whether or not the spot happens to be lit. Skipping the lit
     * ones sounds thriftier and is how two of the four posts around the bell never appeared:
     * the posts either side of a three-wide road are four blocks apart, a lantern is light 15,
     * so the first one of a pair lights the second past the threshold and the second is never
     * built. The result was a lamp here and no lamp there with no rule a player could see.
     *
     * <p>Whether the village is actually dark is a different question, and
     * {@link #darkPosts} answers that one for {@code /placitum light}.
     */
    public static List<BlockPos> unlitPosts(ServerLevel level, Settlement settlement, int phase) {
        BlockPos bell = settlement.center();
        int reach = TownPlan.phaseReach(phase);
        List<BlockPos> todo = new ArrayList<>();

        for (int dz = -reach; dz <= reach; dz++) {
            for (int dx = -reach; dx <= reach; dx++) {
                BlockPos pos = bell.offset(dx, 0, dz);
                if (!TownPlan.isLampPost(pos, bell) || !level.hasChunkAt(pos)) {
                    continue;
                }
                int ground = GridSurvey.groundOrSkip(level, pos.getX(), pos.getZ());
                if (ground == Ground.SKIP
                        || GridSurvey.builtOn(level, pos.getX(), pos.getZ())) {
                    // No lamps in water, and none on top of somebody's build - which includes
                    // the lamp we put here last time, so this is also what makes it converge.
                    continue;
                }
                todo.add(new BlockPos(pos.getX(), ground + 1, pos.getZ()));
            }
        }
        return List.copyOf(todo);
    }

    /**
     * Where a mob can still spawn: ground inside the town below the light threshold.
     *
     * <p>Block light only. Counting sunlight would call everywhere bright at noon and dark at
     * midnight, which says nothing about where a mob can spawn tonight.
     *
     * <p>A report, not a plan. It is the question the whole mod exists to answer, and it is
     * worth being able to ask it separately from what has been built.
     */
    public static List<BlockPos> darkPosts(ServerLevel level, Settlement settlement) {
        BlockPos bell = settlement.center();
        int reach = TownPlan.phaseReach(TownPlan.maxPhase(settlement));
        int wanted = PlacitumConfig.MIN_LIGHT_LEVEL.get();
        List<BlockPos> dark = new ArrayList<>();

        for (int dz = -reach; dz <= reach; dz++) {
            for (int dx = -reach; dx <= reach; dx++) {
                BlockPos pos = bell.offset(dx, 0, dz);
                if (!TownPlan.isLampPost(pos, bell) || !level.hasChunkAt(pos)) {
                    continue;
                }
                int ground = GridSurvey.groundOrSkip(level, pos.getX(), pos.getZ());
                if (ground == Ground.SKIP) {
                    continue;
                }
                BlockPos standing = new BlockPos(pos.getX(), ground + 1, pos.getZ());
                if (level.getBrightness(LightLayer.BLOCK, standing) < wanted) {
                    dark.add(standing);
                }
            }
        }
        return List.copyOf(dark);
    }

    /**
     * Lamps for the posts that are dark, or nothing when the village is lit.
     *
     * <p>Empty is the normal answer once a settlement has stood a while, which is what makes it
     * safe to ask on every pass.
     */
    public static Optional<BuildRecipe> plan(ServerLevel level, Settlement settlement,
            int phase) {
        List<BlockPos> dark = unlitPosts(level, settlement, phase);
        if (dark.isEmpty()) {
            return Optional.empty();
        }
        int batch = Math.min(dark.size(), PlacitumConfig.LAMPS_PER_JOB.get());
        List<BlockPos> posts = new ArrayList<>(dark.subList(0, batch));
        List<Integer> profile = new ArrayList<>(posts.size());
        for (BlockPos post : posts) {
            profile.add(post.getY() - 1);   // reported at standing height; freeze the ground
        }
        Placitum.LOGGER.debug("'{}' has {} bare post(s) in phase {}; lighting {}",
                settlement.name(), dark.size(), phase, batch);

        return Optional.of(new BuildRecipe(LAMPS, posts.getFirst(), Rotation.NONE,
                Identifier.fromNamespaceAndPath(Placitum.MODID, "biome_palette/plains"),
                List.copyOf(profile), new BlockPos(batch, 0, 0), Positions.encode(posts)));
    }

    /**
     * A fence post with a lantern on top.
     *
     * <p>Not a torch on the ground: anything walking into one knocks it off, and a village that
     * relights itself every morning was dark all night. The post also lifts the light a block,
     * which is what keeps the ground beside it from spawning.
     */
    public static List<BuildOp> expand(BuildRecipe recipe) {
        List<Integer> profile = recipe.groundProfile();
        List<BlockPos> posts = Positions.decode(recipe.gates());
        if (posts.size() != profile.size()) {
            return List.of();
        }
        List<BuildOp> ops = new ArrayList<>();
        for (int i = 0; i < posts.size(); i++) {
            if (profile.get(i) == Ground.SKIP) {
                continue;
            }
            int x = posts.get(i).getX();
            int z = posts.get(i).getZ();
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
