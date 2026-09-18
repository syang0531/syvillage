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
 * {@link TownPlan#wallInner} - arithmetic on the bell, thirteen blocks inside the gap the outer
 * phase leaves by not closing itself.
 *
 * <p>It breaks where a road breaks: at water, at a cliff, at ground nobody can walk to. That
 * leaves holes in it, and the holes are the answer rather than the defect. Level the ground and
 * the next pass closes them; leave it and close them by hand. Letting the wall alone cut terrain
 * would invite the same question about houses the next day.
 */
public final class WallPlan {

    /** The rampart's three courses of body, and the height of its parapet above the footing. */
    private static final int BODY = 3;
    private static final int PARAPET = 4;

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

        // Swept by position along each side, and every position contributes its whole
        // cross-section at once. Sweeping by depth instead built the inner face all the way
        // round first - and a built inner face is a three-block step, so the walk that decides
        // what is reachable could no longer get past it to the other three. The wall walled
        // itself in: one column wide in most places, and then nothing left it could reach.
        int wanted = Math.max(TownPlan.WALL, batch);

        for (int along = -outer; along <= outer && todo.size() < wanted; along++) {
            for (int side = 0; side < 4 && todo.size() < wanted; side++) {
                List<Spans> slice = new ArrayList<>(TownPlan.WALL);
                if (!level(level, settlement, bell, along, side, craft.wall())) {
                    continue;   // not flat enough here; the player levels it or it stays open
                }
                for (int depth = 0; depth < TownPlan.WALL; depth++) {
                    BlockPos pos = columnAt(bell, side, along, outer - depth);
                    if (!TownPlan.onWall(pos, settlement)
                            || TownPlan.reservedForWall(pos, settlement)
                            || !level.hasChunkAt(pos) || !reach.has(pos)) {
                        // The gatehouses and towers are skipped here rather than when the
                        // blocks are laid - they bring their own stretch of rampart with them.
                        // Skipping them at laying time queued the same columns every second for
                        // ever: the plan wanted them, the laying refused them, and nothing ever
                        // changed to make the plan stop wanting them.
                        continue;
                    }
                    if (!seen.add(Reach.key(pos.getX(), pos.getZ()))) {
                        continue;   // the corners belong to two sides
                    }
                    int ground = GridSurvey.groundOrSkip(level, pos.getX(), pos.getZ());
                    if (ground == Ground.SKIP) {
                        continue;   // the wall stops at the water, like everything else
                    }
                    // Standing already, or somebody else's - one question, because a wall is a
                    // structure and structures upgrade by being knocked down, exactly as houses
                    // do. Asking whether the wall was of the current standard could not work
                    // anyway: a wall raises the ground reading of its own column by its own
                    // height, so "is there wall just above the ground" looks at the sky.
                    if (GridSurvey.builtOn(level, pos.getX(), pos.getZ())) {
                        continue;
                    }
                    slice.add(new Spans(pos.getX(), pos.getZ(), ground,
                            Clearance.topOf(level, pos.getX(), pos.getZ(), ground)));
                }
                for (Spans column : slice) {
                    todo.add(column);
                    profile.add(column.base());
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
     * Whether the ground here is flat enough to put a wall on.
     *
     * <p>Measured over this cross-section <em>and the one before it</em>, so it answers both of
     * the ways a wall came out wrong: four columns at four heights gave a walkway you could not
     * walk two abreast on, and one cross-section a block above the last gave a rampart that
     * came apart into steps down the hillside.
     *
     * <p>The same {@code maxCellSlope} a building lot is held to, because it is the same
     * question - how level does ground have to be before we put something on it - and because a
     * player who wants a wall over a hill can level the hill, which is the answer the mod gives
     * to everything else.
     */
    private static boolean level(ServerLevel level, Settlement settlement, BlockPos bell,
            int along, int side, BlockState stone) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        int outer = TownPlan.wallOuter(settlement);

        for (int back = 0; back <= 1; back++) {
            for (int depth = 0; depth < TownPlan.WALL; depth++) {
                BlockPos pos = columnAt(bell, side, along - back, outer - depth);
                if (!level.hasChunkAt(pos)) {
                    return false;
                }
                if (GridSurvey.groundOrSkip(level, pos.getX(), pos.getZ()) == Ground.SKIP) {
                    return false;   // water
                }
                // Read down through our own masonry to the ground it stands on. The cross-section
                // before this one is usually already built, and a built cross-section reads three
                // blocks higher than the ground - so this said "not level" beside every stretch
                // of wall that existed, and the rampart came out as separate parallel strips
                // with a gap between each one.
                int ground = GridSurvey.footingAt(level, pos.getX(), pos.getZ(), stone);
                lowest = Math.min(lowest, ground);
                highest = Math.max(highest, ground);
            }
        }
        return highest - lowest <= PlacitumConfig.MAX_CELL_SLOPE.get();
    }

    /** One column of the ring, by which side of the square it is on and how far along. */
    private static BlockPos columnAt(BlockPos bell, int side, int along, int out) {
        return switch (side) {
            case 0 -> bell.offset(along, 0, -out);
            case 1 -> bell.offset(along, 0, out);
            case 2 -> bell.offset(-out, 0, along);
            default -> bell.offset(out, 0, along);
        };
    }

    /**
     * A column of wall: three of body, then either walkway or parapet, then merlon or not.
     *
     * <p>The cross-section is the player's - two blocks of rampart saved from a creative world
     * as {@code rampart.nbt} and read here by eye: five wide, body of three, a parapet on the
     * two faces at four, merlons at five. Depth 0 and 4 are the faces; 1 to 3 are the walkway,
     * three abreast, left open to the sky.
     *
     * <p>Merlons alternate along the wall, and the two faces' merlons line up with each other -
     * that is how the sample was built. Cut from the coordinate that runs along the wall, which
     * is the same on both faces of a straight run.
     *
     * <p>The gatehouses and towers never reach here: {@link #plan} leaves their ground out, so
     * that the plan and the laying cannot disagree about it. They bring their own stretch of
     * rampart, at these same heights, in their templates.
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
            if (column.base() == Ground.SKIP) {
                continue;
            }
            int base = column.base();
            for (int dy = 1; dy <= BODY; dy++) {
                ops.add(new BuildOp(column.at(base + dy), stone));
            }
            int depth = depthOf(column, bell, outer);
            boolean parapet = depth == 0 || depth == TownPlan.WALL - 1;
            if (!parapet) {
                continue;
            }
            ops.add(new BuildOp(column.at(base + PARAPET), stone));
            if (Math.floorMod(alongOf(column, bell), 2) == 0) {
                ops.add(new BuildOp(column.at(base + TownPlan.WALL_HEIGHT), stone));
            }
        }
        ops.sort(Comparator.comparingInt((BuildOp op) -> op.pos().getY())
                .thenComparingInt(op -> op.pos().getX())
                .thenComparingInt(op -> op.pos().getZ()));
        return List.copyOf(ops);
    }

    /** The coordinate that runs along the wall at this column: x on the north and south sides,
     * z on the east and west. At a corner either will do, and x is taken. */
    private static int alongOf(Spans column, BlockPos bell) {
        int dx = Math.abs(column.x() - bell.getX());
        int dz = Math.abs(column.z() - bell.getZ());
        return dx > dz ? column.z() : column.x();
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
