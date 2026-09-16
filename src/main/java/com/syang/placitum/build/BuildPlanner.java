package com.syang.placitum.build;

import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.GateNode;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Direction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns a recipe into the list of blocks it means.
 *
 * <p>A pure function of the recipe and nothing else. That is not a style preference: op lists
 * are never stored - a house is thousands of them and the settlement's save is rewritten whole
 * whenever it changes - so the same recipe has to expand to the same list weeks later, on a
 * different machine, against terrain that has since changed. Everything the world has to say
 * was said once, at QUEUE time, and frozen into {@code groundProfile}.
 *
 * <p>Read a block here and replay silently stops matching what the virtual simulation already
 * counted as built. See docs/data-model.md.
 */
public final class BuildPlanner {

    /**
     * A drop this size is left to the cliff.
     *
     * <p>docs/construction.md: step up 1-2, run a vertical segment at 3-4, give up at 5. The
     * last rule is the important one - carving a mountain to close a ring reads as griefing and
     * costs a fortune in logs, and a cliff is already a wall.
     */
    private static final int CLIFF = 5;

    /** What clearing a way through looks like as an op. */
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** An op and where on the ring it came from, so ordering can follow the wall. */
    private record Placed(int ringIndex, BuildOp op) {}

    private BuildPlanner() {}

    /**
     * Expands a wall recipe.
     *
     * <p>Y-ascending, so a builder can stand on what it has already laid. docs/construction.md
     * leans on that ordering instead of scaffolding, which would have to be put up, taken down,
     * and got wrong.
     */
    public static List<BuildOp> expand(BuildRecipe recipe) {
        if (recipe.template().equals(HousePlanner.COTTAGE)) {
            return CottagePlan.expand(recipe);
        }
        return expandWall(recipe);
    }

    private static List<BuildOp> expandWall(BuildRecipe recipe) {
        List<Integer> profile = recipe.groundProfile();
        WallGeometry.Box box = new WallGeometry.Box(
                recipe.anchor(), recipe.width(), recipe.depth());
        List<BlockPos> ring = WallGeometry.perimeter(box);
        if (ring.size() != profile.size() || recipe.height() <= 0) {
            // Refusing beats guessing. A mismatch means the recipe was written by one version
            // of the geometry and is being read by another, and half a wall in the wrong place
            // is worse than none.
            return List.of();
        }

        BlockState material = materialFor(recipe);
        Set<Integer> gates = new HashSet<>(recipe.gates());
        List<Placed> placed = new ArrayList<>();

        for (int i = 0; i < ring.size(); i++) {
            int ground = profile.get(i);
            if (ground == WallGeometry.SKIP) {
                continue;
            }
            if (gates.contains(i)) {
                // A gate is one block of fence gate with the way above it cleared. Logs over
                // the top would be a doorway with a ceiling a villager cannot path through,
                // which is the same as no gate at all - docs/defense.md is blunt about what
                // happens then: a farmer standing in front of it for ever.
                //
                // Cleared with air ops, not by leaving the column out. Leaving it out means
                // placing nothing, and placing nothing over a wall that is already standing
                // leaves the old logs exactly where they were: a gate was cut into a finished
                // palisade and stayed buried under it.
                BlockPos gate = ring.get(i);
                placed.add(new Placed(i, new BuildOp(
                        new BlockPos(gate.getX(), ground + 1, gate.getZ()), gateState(box, i))));
                for (int y = ground + 2; y <= ground + recipe.height(); y++) {
                    placed.add(new Placed(i, new BuildOp(
                            new BlockPos(gate.getX(), y, gate.getZ()), AIR)));
                }
                continue;
            }
            int top = ground + recipe.height();
            // Seal against a neighbour standing higher, so a 3-4 block step does not leave a
            // gap a skeleton can shoot through. Both neighbours, because the ring is a loop.
            top = Math.max(top, sealAgainst(profile, i - 1, ring.size(), ground));
            top = Math.max(top, sealAgainst(profile, i + 1, ring.size(), ground));

            BlockPos column = ring.get(i);
            for (int y = ground + 1; y <= top; y++) {
                placed.add(new Placed(i,
                        new BuildOp(new BlockPos(column.getX(), y, column.getZ()), material)));
            }
        }

        // Height first, then around the ring - never by coordinate. Sorting by x and z looks
        // tidy and scatters the work: on one course, stepping x by one gives a block on the
        // north edge and then a block on the south edge, a hundred and sixty blocks away. A
        // builder chasing that never places a second block. Ring order walks the wall.
        placed.sort(Comparator.comparingInt((Placed p) -> p.op().pos().getY())
                .thenComparingInt(Placed::ringIndex));

        List<BuildOp> ops = new ArrayList<>(placed.size());
        for (Placed p : placed) {
            ops.add(p.op());
        }
        return List.copyOf(ops);
    }

    /**
     * A fence gate lying along the wall, hinged so it opens outward.
     *
     * <p>A fence gate rather than a door, because villagers open and close them on their own and
     * mobs do not. An iron door would be worse than a wall: pathfinding treats it as solid, so
     * the village would seal itself in.
     */
    private static BlockState gateState(WallGeometry.Box box, int index) {
        Direction outward = WallGeometry.outwardAt(box, index);
        return Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, outward);
    }

    /** Where the gates ended up, as pathfinding nodes. */
    public static List<GateNode> gatesOf(BuildRecipe recipe) {
        List<Integer> profile = recipe.groundProfile();
        WallGeometry.Box box = new WallGeometry.Box(
                recipe.anchor(), recipe.width(), recipe.depth());
        List<BlockPos> ring = WallGeometry.perimeter(box);
        if (ring.size() != profile.size()) {
            return List.of();
        }
        List<GateNode> out = new ArrayList<>();
        for (int index : recipe.gates()) {
            if (index < 0 || index >= ring.size() || profile.get(index) == WallGeometry.SKIP) {
                continue;
            }
            BlockPos column = ring.get(index);
            out.add(new GateNode(new BlockPos(column.getX(), profile.get(index) + 1,
                    column.getZ()), WallGeometry.outwardAt(box, index), true));
        }
        return List.copyOf(out);
    }

    /**
     * The ground-level footprint of a finished wall.
     *
     * <p>One position a column, not one a block: the ring is what pathfinding and the defence
     * rating ask about, and neither of them cares how many logs are stacked on it.
     */
    public static List<BlockPos> ringOf(BuildRecipe recipe) {
        List<Integer> profile = recipe.groundProfile();
        List<BlockPos> ring = WallGeometry.perimeter(
                new WallGeometry.Box(recipe.anchor(), recipe.width(), recipe.depth()));
        if (ring.size() != profile.size()) {
            return List.of();
        }
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < ring.size(); i++) {
            if (profile.get(i) != WallGeometry.SKIP) {
                BlockPos column = ring.get(i);
                out.add(new BlockPos(column.getX(), profile.get(i) + 1, column.getZ()));
            }
        }
        return List.copyOf(out);
    }

    /**
     * How high this column must reach to meet a taller neighbour.
     *
     * <p>Zero when the neighbour is lower, absent, or across a cliff - a cliff is where the wall
     * stops rather than something to climb.
     */
    private static int sealAgainst(List<Integer> profile, int index, int size, int ground) {
        int wrapped = Math.floorMod(index, size);
        int neighbour = profile.get(wrapped);
        if (neighbour == WallGeometry.SKIP) {
            return 0;
        }
        int rise = neighbour - ground;
        return rise > 0 && rise < CLIFF ? neighbour + 1 : 0;
    }

    /** Whether two adjacent ground heights are too far apart to wall across. */
    public static boolean isCliff(int a, int b) {
        return a != WallGeometry.SKIP && b != WallGeometry.SKIP && Math.abs(a - b) >= CLIFF;
    }

    /**
     * What a palisade is made of.
     *
     * <p>One material for now. Biome palette substitution arrives with houses, where it earns
     * its keep by letting one set of templates serve every biome; a wall has no templates to
     * multiply, so adding the machinery here would buy nothing.
     */
    private static BlockState materialFor(BuildRecipe recipe) {
        return Blocks.OAK_LOG.defaultBlockState();
    }
}
