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
 * <p>Paved in whatever the settlement can build with. A village that gains a mason finds its
 * dirt tracks listed as work again and lays them a second time in stone, and no code here knows
 * that is an upgrade - it is the same question, asked of a village that can now answer it
 * differently.
 *
 * <p>Laid on any ground the street itself reaches. A village on a hillside still has streets,
 * and refusing to lay
 * one across a slope would leave the whole plan unanchored.
 */
public final class RoadPlan {

    public static final Identifier STREET =
            Identifier.fromNamespaceAndPath(Placitum.MODID, "road/street");

    private RoadPlan() {}

    /**
     * The next stretch of street with no path on it yet, or nothing when they are all laid.
     *
     * <p>Batched so the streets are watched going down rather than appearing, and it stops as
     * soon as it has a batch - the search must not walk the whole claim every tick looking for
     * work that was finished an hour ago.
     */
    public static Optional<BuildRecipe> plan(ServerLevel level, Settlement settlement,
            int phase, Reach reach) {
        BlockPos bell = settlement.center();
        int limit = TownPlan.reachOf(settlement, phase);
        int batch = PlacitumConfig.ROAD_BLOCKS_PER_JOB.get();

        List<Spans> todo = new ArrayList<>();
        List<Integer> profile = new ArrayList<>();

        // Outward in rings, so the streets by the bell are finished before the outskirts begin.
        for (int ring = 0; ring <= limit && todo.size() < batch; ring++) {
            for (int along = -ring; along <= ring && todo.size() < batch; along++) {
                for (BlockPos pos : new BlockPos[] {
                        bell.offset(along, 0, -ring), bell.offset(along, 0, ring),
                        bell.offset(-ring, 0, along), bell.offset(ring, 0, along)}) {
                    if (todo.size() >= batch || !TownPlan.onRoad(pos, bell)
                            || !level.hasChunkAt(pos)) {
                        continue;
                    }
                    if (!reach.street(pos)) {
                        // The far shore of a lake, or a shelf too high to climb. The street
                        // stops where a person walking down it would - reachable along the
                        // street, not reachable by some detour across a field, or the road
                        // reappears on the far side of a cliff with the cliff still in it.
                        continue;
                    }
                    int ground = GridSurvey.groundOrSkip(level, pos.getX(), pos.getZ());
                    if (ground == Ground.SKIP) {
                        continue;   // water: the street goes round
                    }
                    // The cheap question first. Once a phase is paved this is the only test
                    // almost every column reaches, and the settlement asks it of the whole
                    // phase every time it looks for work.
                    //
                    BlockState top = level.getBlockState(
                            new BlockPos(pos.getX(), ground, pos.getZ()));
                    if (Craft.isPaving(top)) {
                        // Ours already, in whatever palette laid it. Streets used to be taken
                        // up and relaid when the town's standard rose; there is no standard to
                        // rise now, and a town does not take up its own street.
                        continue;
                    }
                    if (GridSurvey.builtOn(level, pos.getX(), pos.getZ())) {
                        continue;   // somebody else's: the street goes round
                    }
                    // With the clearance: a street runs under a tree otherwise, because the
                    // ground reading walks down past the trunk on purpose.
                    todo.add(new Spans(pos.getX(), pos.getZ(), ground,
                            Clearance.topOf(level, pos.getX(), pos.getZ(), ground)));
                    profile.add(ground);
                }
            }
        }
        if (todo.isEmpty()) {
            return Optional.empty();
        }
        Placitum.LOGGER.debug("'{}' has {} block(s) of street to lay in phase {}",
                settlement.name(), todo.size(), phase);
        return Optional.of(new BuildRecipe(STREET,
                new BlockPos(todo.getFirst().x(), todo.getFirst().base(), todo.getFirst().z()),
                Rotation.NONE, settlement.craft().paletteId(),
                List.copyOf(profile), new BlockPos(todo.size(), 1, 0), Spans.encode(todo)));
    }

    /**
     * One path block per column, on the ground that was frozen, and whatever grew on it cut.
     *
     * <p>Only growth. A road that bulldozed whatever it met would carve through the houses the
     * village already has, which is why the columns something is built on were dropped at plan
     * time rather than cleared here.
     */
    public static List<BuildOp> expand(BuildRecipe recipe) {
        List<Spans> columns = Spans.decode(recipe.gates());
        BlockState paving = Craft.fromPalette(recipe.palette()).paving();
        List<BuildOp> ops = new ArrayList<>();
        Set<BlockPos> claimed = new HashSet<>();
        for (Spans column : columns) {
            if (column.base() == Ground.SKIP) {
                continue;
            }
            BlockPos at = column.at(column.base());
            claimed.add(at);
            ops.add(new BuildOp(at, paving));
        }
        ops.addAll(Clearance.ops(columns, claimed));
        ops.sort(Comparator.comparingInt((BuildOp op) -> op.pos().getY())
                .thenComparingInt(op -> op.pos().getX())
                .thenComparingInt(op -> op.pos().getZ()));
        return List.copyOf(ops);
    }
}
