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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The streets, laid out as a grid rather than a single cross.
 *
 * <p>Roads are the skeleton the plan hangs on - a lot is defined as the ground between them - so
 * they are less something the settlement decides to build than the shape it already has. What is
 * left is putting the blocks down, a stretch at a time, nearest the bell first.
 *
 * <p>Laid on any ground at all. A village on a hillside still has streets, and refusing to lay
 * one across a slope would leave the whole plan unanchored.
 */
public final class RoadPlan {

    public static final Identifier STREET =
            Identifier.fromNamespaceAndPath(Placitum.MODID, "road/street");

    private static final BlockState PATH = Blocks.DIRT_PATH.defaultBlockState();

    private RoadPlan() {}

    /**
     * The next stretch of street with no path on it yet, or nothing when they are all laid.
     *
     * <p>Batched so the streets are watched going down rather than appearing, and it stops as
     * soon as it has a batch - the search must not walk the whole claim every tick looking for
     * work that was finished an hour ago.
     */
    public static Optional<BuildRecipe> plan(ServerLevel level, Settlement settlement) {
        BlockPos bell = settlement.center();
        int reach = TownPlan.reachBlocks(settlement);
        int batch = PlacitumConfig.ROAD_BLOCKS_PER_JOB.get();

        List<BlockPos> todo = new ArrayList<>();
        List<Integer> profile = new ArrayList<>();

        // Outward in rings, so the streets by the bell are finished before the outskirts begin.
        for (int ring = 0; ring <= reach && todo.size() < batch; ring++) {
            for (int along = -ring; along <= ring && todo.size() < batch; along++) {
                for (BlockPos pos : new BlockPos[] {
                        bell.offset(along, 0, -ring), bell.offset(along, 0, ring),
                        bell.offset(-ring, 0, along), bell.offset(ring, 0, along)}) {
                    if (todo.size() >= batch || !TownPlan.onRoad(pos, bell)
                            || !level.hasChunkAt(pos)) {
                        continue;
                    }
                    int ground = GridSurvey.groundOrSkip(level, pos.getX(), pos.getZ());
                    if (ground == Ground.SKIP
                            || GridSurvey.builtOn(level, pos.getX(), pos.getZ())) {
                        continue;   // water, a building, the bell itself: the street goes round
                    }
                    if (level.getBlockState(new BlockPos(pos.getX(), ground, pos.getZ()))
                            .is(Blocks.DIRT_PATH)) {
                        continue;   // already a street
                    }
                    todo.add(new BlockPos(pos.getX(), 0, pos.getZ()));
                    profile.add(ground);
                }
            }
        }
        if (todo.isEmpty()) {
            return Optional.empty();
        }
        Placitum.LOGGER.debug("'{}' has {} block(s) of street to lay", settlement.name(),
                todo.size());
        return Optional.of(new BuildRecipe(STREET, todo.getFirst(), Rotation.NONE,
                Identifier.fromNamespaceAndPath(Placitum.MODID, "biome_palette/plains"),
                List.copyOf(profile), new BlockPos(todo.size(), 1, 0), Positions.encode(todo)));
    }

    /**
     * One path block per column, on the ground that was frozen.
     *
     * <p>Nothing is cleared above. A road that bulldozed whatever it met would carve through the
     * houses the village already has.
     */
    public static List<BuildOp> expand(BuildRecipe recipe) {
        List<Integer> profile = recipe.groundProfile();
        List<BlockPos> columns = Positions.decode(recipe.gates());
        if (columns.size() != profile.size()) {
            return List.of();
        }
        List<BuildOp> ops = new ArrayList<>();
        for (int i = 0; i < profile.size(); i++) {
            if (profile.get(i) == Ground.SKIP) {
                continue;
            }
            ops.add(new BuildOp(new BlockPos(columns.get(i).getX(), profile.get(i),
                    columns.get(i).getZ()), PATH));
        }
        ops.sort(Comparator.comparingInt((BuildOp op) -> op.pos().getY())
                .thenComparingInt(op -> op.pos().getX())
                .thenComparingInt(op -> op.pos().getZ()));
        return List.copyOf(ops);
    }
}
