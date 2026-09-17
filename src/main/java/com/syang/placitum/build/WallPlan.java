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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The town wall, which was deleted once and has come back for a reason.
 *
 * <p>It went because a fixed ring ends up in the wrong place as soon as the town grows past it,
 * because the thing that stops a mob spawning is light and not masonry, and because sixteen
 * hundred blocks of it solved nothing. The first of those stopped being true when the town
 * stopped growing: the plan now has a last phase, so a ring on it is a ring on the edge, for
 * good. The second was never the wall's job - light does the spawning and the wall does what
 * light cannot, which is the mobs that walk in from outside. That gap is written into
 * docs/design.md as the price of deleting it.
 *
 * <p>The third is answered by where it goes. The old ring was computed from wherever the
 * settlement had spread, and every bug it had came from that. This one is
 * {@link TownPlan#wallInner} - arithmetic on the bell, in the gap the outer phase leaves by not
 * closing itself.
 *
 * <p>It breaks where a road breaks: at water, at a cliff, at ground nobody can walk to. That
 * leaves holes in it, and the holes are the answer rather than the defect. Level the ground and
 * the next pass closes them; leave it and close them by hand. Letting the wall alone cut terrain
 * would invite the same question about houses the next day.
 */
public final class WallPlan {

    public static final Identifier RAMPART =
            Identifier.fromNamespaceAndPath(Placitum.MODID, "wall/rampart");

    private WallPlan() {}

    /**
     * The next stretch of wall that is not standing yet.
     *
     * <p>Ring order like the streets, so it goes up as a wall being built rather than as a
     * scattering of masonry.
     */
    public static Optional<BuildRecipe> plan(ServerLevel level, Settlement settlement,
            Reach reach) {
        if (!settlement.walled()) {
            return Optional.empty();
        }
        BlockPos bell = settlement.center();
        int outer = TownPlan.wallOuter(settlement);
        int batch = PlacitumConfig.WALL_COLUMNS_PER_JOB.get();
        Craft craft = settlement.craft();

        List<Spans> todo = new ArrayList<>();
        List<Integer> profile = new ArrayList<>();
        // The ring scan reaches a corner column from more than one direction, and a column laid
        // twice is a clearance op laid twice with it.
        Set<Long> seen = new HashSet<>();

        for (int ring = TownPlan.wallInner(settlement); ring <= outer && todo.size() < batch;
                ring++) {
            for (int along = -outer; along <= outer && todo.size() < batch; along++) {
                for (BlockPos pos : new BlockPos[] {
                        bell.offset(along, 0, -ring), bell.offset(along, 0, ring),
                        bell.offset(-ring, 0, along), bell.offset(ring, 0, along)}) {
                    if (todo.size() >= batch || !TownPlan.onWall(pos, settlement)
                            || !seen.add(Reach.key(pos.getX(), pos.getZ()))
                            || !level.hasChunkAt(pos) || !reach.has(pos)) {
                        continue;
                    }
                    int ground = GridSurvey.groundOrSkip(level, pos.getX(), pos.getZ());
                    if (ground == Ground.SKIP) {
                        continue;   // the wall stops at the water, like everything else
                    }
                    if (level.getBlockState(new BlockPos(pos.getX(), ground + 1, pos.getZ()))
                            .is(craft.wall().getBlock())) {
                        continue;   // this stretch is standing, and to the right standard
                    }
                    if (GridSurvey.builtOn(level, pos.getX(), pos.getZ())) {
                        continue;   // somebody is there; the wall is not worth a house
                    }
                    todo.add(new Spans(pos.getX(), pos.getZ(), ground,
                            Clearance.topOf(level, pos.getX(), pos.getZ(), ground)));
                    profile.add(ground);
                }
            }
        }
        if (todo.isEmpty()) {
            return Optional.empty();
        }
        Placitum.LOGGER.debug("'{}' has {} column(s) of wall to raise", settlement.name(),
                todo.size());
        // The bell, and the wall's two faces, frozen into the recipe. Expansion has to know
        // how deep into the wall each column is, and a settlement that grows a phase between
        // planning and laying would otherwise move the wall out from under a job in the queue.
        return Optional.of(new BuildRecipe(RAMPART, bell, Rotation.NONE, craft.paletteId(),
                List.copyOf(profile),
                new BlockPos(TownPlan.wallInner(settlement), TownPlan.WALL_HEIGHT, outer),
                Spans.encode(todo)));
    }

    /**
     * A column of wall: three of body, then either walkway or parapet.
     *
     * <p>The cross-section is decided by how deep into the wall the column is, which
     * {@link TownPlan#wallDepth} gives as one number, so there is no inside and outside to keep
     * straight. Depth 0 and 3 are the two faces and carry the parapet; 1 and 2 are the walkway
     * and are left open to the sky.
     *
     * <p>Crenellations alternate along the wall rather than across it, so the pattern reads the
     * way it does on a real rampart. They are cut from the sum of the coordinates, which is the
     * same on both faces of a straight run and turns the corner without a seam.
     *
     * <p>The four gateways are left open. See the comment on the skip.
     */
    public static List<BuildOp> expand(BuildRecipe recipe) {
        List<Spans> columns = Spans.decode(recipe.gates());
        if (columns.isEmpty()) {
            return List.of();
        }
        BlockState stone = Craft.fromPalette(recipe.palette()).wall();
        BlockPos bell = recipe.anchor();
        int outer = recipe.extent().getZ();   // frozen: the wall's outer face, in blocks
        List<BuildOp> ops = new ArrayList<>();

        for (Spans column : columns) {
            if (column.base() == Ground.SKIP || TownPlan.inArch(column.at(0), bell)) {
                // The gateway is left as a hole in the wall. An arch wants headroom the wall
                // does not have - it is four high and the body of it is three - so the arch
                // belongs to the gatehouse, which stands eight. Until that exists the roads run
                // out through a gap, which is at least honest about there being a way through.
                continue;
            }
            int base = column.base();
            for (int dy = 1; dy <= TownPlan.WALL_HEIGHT - 1; dy++) {
                ops.add(new BuildOp(column.at(base + dy), stone));
            }
            int depth = depthOf(column, bell, outer);
            boolean parapet = depth == 0 || depth == TownPlan.WALL - 1;
            boolean merlon = Math.floorMod(column.x() + column.z(), 2) == 0;
            if (parapet && merlon) {
                ops.add(new BuildOp(column.at(base + TownPlan.WALL_HEIGHT), stone));
            }
        }
        ops.sort(Comparator.comparingInt((BuildOp op) -> op.pos().getY())
                .thenComparingInt(op -> op.pos().getX())
                .thenComparingInt(op -> op.pos().getZ()));
        return List.copyOf(ops);
    }

    /**
     * How deep into the wall a frozen column sits, without asking the settlement.
     *
     * <p>The recipe carries the wall's two faces in its extent so that expansion stays a pure
     * function of it. A settlement that grows a phase between planning and laying would
     * otherwise move the wall out from under a job already in the queue.
     */
    private static int depthOf(Spans column, BlockPos bell, int outer) {
        int reach = Math.max(Math.abs(column.x() - bell.getX()),
                Math.abs(column.z() - bell.getZ()));
        return outer - reach;
    }
}
