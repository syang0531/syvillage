package com.syang.placitum.build;

import com.syang.placitum.Placitum;
import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.Craft;
import com.syang.placitum.data.Settlement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The steps from the town up onto the wall.
 *
 * <p>Without these the walkway is scenery. The wall carries you between the gatehouses and the
 * towers carry you to the top of a corner, and none of it could be got at from the ground -
 * which is the kind of thing that only shows up when somebody stands in the street and looks up.
 *
 * <p>One flight beside each gatehouse, on the town side, far enough across to be clear of the
 * gateway. It climbs three and lands on the walkway, and it takes the inner parapet out where it
 * lands, because a flight of stairs that ends in a wall is worse than no flight.
 */
public final class StairPlan {

    public static final Identifier STEPS =
            Identifier.fromNamespaceAndPath(Placitum.MODID, "wall/steps");

    /** Two blocks wide, so two villagers can pass and a player does not walk off the side. */
    public static final int WIDE = 2;

    /** Clear of the gatehouse, which reaches four either side of the road. */
    private static final int ACROSS = TownPlan.GATE_WIDTH / 2 + 1;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private StairPlan() {}

    /**
     * The columns of one flight: three of steps, and the block of wall it lands on.
     *
     * <p>The wall column is in the footprint so that the parapet there can be taken out, and so
     * that the flight is levelled against the wall rather than against its own ground.
     */
    public static List<BlockPos> footprint(Settlement settlement, Direction side) {
        BlockPos bell = settlement.center();
        Direction across = side.getClockWise();
        int landing = TownPlan.wallInner(settlement);
        List<BlockPos> out = new ArrayList<>(WIDE * TownPlan.WALL_HEIGHT);

        for (int step = 0; step < TownPlan.WALL_HEIGHT; step++) {
            for (int lane = 0; lane < WIDE; lane++) {
                out.add(bell.relative(side, landing - TownPlan.WALL_HEIGHT + 1 + step)
                        .relative(across, ACROSS + lane));
            }
        }
        return out;
    }

    public static Optional<BuildRecipe> plan(ServerLevel level, Settlement settlement,
            Direction side, Reach reach) {
        if (!settlement.walled()) {
            return Optional.empty();
        }
        List<BlockPos> columns = footprint(settlement, side);
        List<Integer> profile = new ArrayList<>(columns.size());

        for (int i = 0; i < columns.size(); i++) {
            BlockPos column = columns.get(i);
            if (!level.hasChunkAt(column)) {
                return Optional.empty();
            }
            // The last column is the wall, and the wall is not walkable from the street - that
            // is the entire reason these steps exist. Asking it whether it was reachable meant
            // no flight was ever built once the wall it lands on was standing.
            boolean landing = i >= columns.size() - WIDE;
            if (!landing && !reach.has(column)) {
                return Optional.empty();
            }
            // The landing is read down through the wall to the ground it stands on. Levelling
            // the flight against the top of the wall would put it four blocks too high.
            int ground = landing
                    ? GridSurvey.footingAt(level, column.getX(), column.getZ(),
                            settlement.craft().wall())
                    : GridSurvey.groundOrSkip(level, column.getX(), column.getZ());
            if (ground == Ground.SKIP) {
                return Optional.empty();
            }
            profile.add(ground);
        }
        // Levelled against the wall's own footing, which is the last column of the footprint. A
        // flight that climbed from its own ground would arrive at the wrong height on a slope.
        int floor = profile.get(profile.size() - 1);

        // And the ground it climbs has to be level with that footing, for the same reason the
        // wall does. It is also what stops a loop: where the ground was above a tread, expand
        // wrote nothing there, the "is it standing" test never saw its block, and the settlement
        // laid the same flight a hundred and sixty-six times in one session.
        for (int ground : profile) {
            if (Math.abs(ground - floor) > PlacitumConfig.MAX_CELL_SLOPE.get()) {
                return Optional.empty();
            }
        }
        BlockPos first = columns.getFirst();
        if (level.getBlockState(new BlockPos(first.getX(), floor + 1, first.getZ()))
                .is(settlement.craft().wall().getBlock())) {
            return Optional.empty();   // standing already
        }
        Placitum.LOGGER.debug("Planned steps onto the wall for '{}' facing {}",
                settlement.name(), side);

        return Optional.of(new BuildRecipe(STEPS, columns.getFirst(), GatePlan.rotationOf(side),
                settlement.craft().paletteId(), List.copyOf(profile),
                new BlockPos(WIDE, TownPlan.WALL_HEIGHT, TownPlan.WALL_HEIGHT),
                Spans.encode(Clearance.spans(level, columns, profile))));
    }

    /**
     * Three treads and a landing.
     *
     * <p>The last column is the wall itself: nothing is added there, and the parapet is taken
     * out so the flight opens onto the walkway instead of at it.
     */
    public static List<BuildOp> expand(BuildRecipe recipe) {
        List<Integer> profile = recipe.groundProfile();
        Direction side = GatePlan.sideOf(recipe.rotation());
        Direction across = side.getClockWise();
        int steps = recipe.extent().getY();
        if (profile.size() != steps * WIDE) {
            return List.of();
        }
        int floor = profile.get(profile.size() - 1);
        BlockState stone = Craft.fromPalette(recipe.palette()).wall();
        List<BuildOp> ops = new ArrayList<>();

        for (int step = 0; step < steps; step++) {
            for (int lane = 0; lane < WIDE; lane++) {
                BlockPos column = recipe.anchor().relative(side, step).relative(across, lane);
                int i = step * WIDE + lane;
                if (step == steps - 1) {
                    // The wall. Take the inner parapet off and leave the rest of it alone.
                    ops.add(new BuildOp(new BlockPos(column.getX(),
                            floor + TownPlan.WALL_HEIGHT, column.getZ()), AIR));
                    continue;
                }
                for (int y = profile.get(i) + 1; y <= floor + step + 1; y++) {
                    ops.add(new BuildOp(new BlockPos(column.getX(), y, column.getZ()), stone));
                }
                // Headroom over each tread, so the flight is a flight and not a crawlspace.
                for (int head = 1; head <= 2; head++) {
                    ops.add(new BuildOp(new BlockPos(column.getX(),
                            floor + step + 1 + head, column.getZ()), AIR));
                }
            }
        }
        ops.addAll(Clearance.ops(Spans.decode(recipe.gates()), GatePlan.written(ops)));
        ops.sort(Comparator.comparingInt((BuildOp op) -> op.pos().getY())
                .thenComparingInt(op -> op.pos().getX())
                .thenComparingInt(op -> op.pos().getZ()));
        return List.copyOf(ops);
    }
}
