package com.syang.placitum.build;

import com.syang.placitum.Placitum;
import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.Craft;
import com.syang.placitum.data.Settlement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    public static List<BlockPos> unlitPosts(ServerLevel level, Settlement settlement, int phase,
            Reach reach) {
        BlockPos bell = settlement.center();
        int limit = TownPlan.reachOf(settlement, phase);
        List<BlockPos> todo = new ArrayList<>();

        for (int dz = -limit; dz <= limit; dz++) {
            for (int dx = -limit; dx <= limit; dx++) {
                BlockPos pos = bell.offset(dx, 0, dz);
                if (!TownPlan.isLampPost(pos, bell) || !level.hasChunkAt(pos)) {
                    continue;
                }
                if (settlement.lamps().contains(Reach.key(pos.getX(), pos.getZ()))) {
                    continue;   // lit once already; if it is dark now, somebody wanted it dark
                }
                int ground = GridSurvey.groundOrSkip(level, pos.getX(), pos.getZ());
                if (ground == Ground.SKIP || !reach.has(pos)
                        || GridSurvey.builtOn(level, pos.getX(), pos.getZ())) {
                    // No lamps in water, none where nobody can walk, and none on top of
                    // somebody's build - which includes the lamp we put here last time, so that
                    // last one is also what makes this converge.
                    continue;
                }
                if (!streetNear(reach, pos, bell)) {
                    continue;   // no street anywhere near means no town here, plan or no plan
                }
                if (TownPlan.reservedForWall(pos, settlement)) {
                    continue;   // a gatehouse or a tower is coming here; do not stand in it
                }
                todo.add(new BlockPos(pos.getX(), ground + 1, pos.getZ()));
            }
        }
        return List.copyOf(todo);
    }

    /**
     * Whether there is a street within reach of the block this post belongs to.
     *
     * <p>Two things pull against each other here and the config key is where they meet.
     *
     * <p>Walking distance from a street is not enough on its own: the ground walk spreads from
     * every paved column in the town, so a post in a meadow a long way from the nearest house is
     * reachable, and the town grew lamps in fields.
     *
     * <p>But light is not a road. Where the street gives up on a hillside the mobs do not - they
     * spawn on the dark slope and walk down it into the town - so the lit ground has to reach
     * past the paved ground rather than stopping with it. Hence a ring of blocks beyond the last
     * street, rather than the street's own blocks alone.
     */
    private static boolean streetNear(Reach reach, BlockPos post, BlockPos bell) {
        int blocks = PlacitumConfig.LAMP_BLOCKS_BEYOND_STREET.get();
        for (int bz = -blocks; bz <= blocks; bz++) {
            for (int bx = -blocks; bx <= blocks; bx++) {
                BlockPos within = post.offset(bx * TownPlan.PERIOD, 0, bz * TownPlan.PERIOD);
                for (BlockPos road : TownPlan.boundingRoads(within, bell)) {
                    if (reach.street(road)) {
                        return true;
                    }
                }
            }
        }
        return false;
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
        int limit = TownPlan.reachOf(settlement, TownPlan.outerPhase(settlement));
        int wanted = PlacitumConfig.MIN_LIGHT_LEVEL.get();
        List<BlockPos> dark = new ArrayList<>();

        for (int dz = -limit; dz <= limit; dz++) {
            for (int dx = -limit; dx <= limit; dx++) {
                BlockPos pos = bell.offset(dx, 0, dz);
                if (!TownPlan.isLampPost(pos, bell) || !level.hasChunkAt(pos)) {
                    continue;
                }
                if (settlement.lamps().contains(Reach.key(pos.getX(), pos.getZ()))) {
                    continue;   // lit once already; if it is dark now, somebody wanted it dark
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
            int phase, Reach reach) {
        List<BlockPos> dark = unlitPosts(level, settlement, phase, reach);
        if (dark.isEmpty()) {
            return Optional.empty();
        }
        int batch = Math.min(dark.size(), PlacitumConfig.LAMPS_PER_JOB.get());
        List<BlockPos> posts = new ArrayList<>(dark.subList(0, batch));
        List<Integer> profile = new ArrayList<>(posts.size());
        for (BlockPos post : posts) {
            profile.add(post.getY() - 1);   // reported at standing height; freeze the ground
        }
        // With the clearance, so a post standing in a thicket cuts its way out.
        List<Spans> columns = Clearance.spans(level, posts, profile);
        Placitum.LOGGER.debug("'{}' has {} bare post(s) in phase {}; lighting {}",
                settlement.name(), dark.size(), phase, batch);

        return Optional.of(new BuildRecipe(LAMPS, posts.getFirst(), Rotation.NONE,
                settlement.craft().paletteId(), List.copyOf(profile), new BlockPos(batch, 0, 0),
                Spans.encode(columns)));
    }

    /**
     * A block, a post on it, a lantern on that - in the palette's materials.
     *
     * <p>Not a torch on the ground: anything walking into one knocks it off, and a village that
     * relights itself every morning was dark all night. The post also lifts the light a block,
     * which is what keeps the ground beside it from spawning. The wall block under the post is
     * what two fence posts and a lantern lacked: a foot, something to read as a lamp rather
     * than a stick.
     */
    public static List<BuildOp> expand(BuildRecipe recipe) {
        List<Spans> posts = Spans.decode(recipe.gates());
        Craft craft = Craft.fromPalette(recipe.palette());
        List<BuildOp> ops = new ArrayList<>();
        Set<BlockPos> claimed = new HashSet<>();
        for (Spans post : posts) {
            if (post.base() == Ground.SKIP) {
                continue;
            }
            for (int dy = 1; dy <= 3; dy++) {
                claimed.add(post.at(post.base() + dy));
            }
            ops.add(new BuildOp(post.at(post.base() + 1), craft.lampBase()));
            ops.add(new BuildOp(post.at(post.base() + 2), craft.lampPost()));
            ops.add(new BuildOp(post.at(post.base() + 3), Blocks.LANTERN.defaultBlockState()));
        }
        ops.addAll(Clearance.ops(posts, claimed));
        ops.sort(Comparator.comparingInt((BuildOp op) -> op.pos().getY())
                .thenComparingInt(op -> op.pos().getX())
                .thenComparingInt(op -> op.pos().getZ()));
        return List.copyOf(ops);
    }
}
