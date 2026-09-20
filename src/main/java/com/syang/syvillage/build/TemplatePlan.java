package com.syang.syvillage.build;

import com.syang.syvillage.SyVillage;
import com.syang.syvillage.config.SyVillageConfig;
import com.syang.syvillage.data.BuildOp;
import com.syang.syvillage.data.BuildRecipe;
import com.syang.syvillage.data.Craft;
import com.syang.syvillage.data.Settlement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A structure the settlement builds from a {@link Template}: where it goes, whether it can,
 * whether it already has, and what blocks it is.
 *
 * <p>The gatehouse and the tower are the same thing in every respect but one - where the
 * template sits relative to the bell before it is turned - so that one difference is the only
 * thing each of them supplies. Everything that went wrong with the two of them when they were
 * separate rules (the floor read from the wrong square, the probe put to a column that was
 * never built, the second storey on the roof) went wrong in code that is now written once.
 */
public final class TemplatePlan {

    private final Identifier id;
    private final String templateName;
    private final Function<Settlement, int[]> originNorth;
    private final String what;

    /**
     * @param originNorth where the template's (0, 0) sits relative to the bell, in the frame the
     *                    template was authored in, as {@code {dx, dz}}
     */
    public TemplatePlan(Identifier id, String templateName,
            Function<Settlement, int[]> originNorth, String what) {
        this.id = id;
        this.templateName = templateName;
        this.originNorth = originNorth;
        this.what = what;
    }

    public Identifier id() {
        return id;
    }

    public Template template() {
        return Template.of(templateName);
    }

    /** Every column the structure puts a block in, in the world, in template order. */
    public List<BlockPos> footprint(Settlement settlement, Rotation rotation) {
        return footprint(settlement.center(), originNorth.apply(settlement), rotation);
    }

    private List<BlockPos> footprint(BlockPos bell, int[] origin, Rotation rotation) {
        List<BlockPos> out = new ArrayList<>();
        for (int[] column : template().columns()) {
            out.add(Template.columnAt(bell, origin, rotation, column[0], column[1]));
        }
        return out;
    }

    public Optional<BuildRecipe> plan(ServerLevel level, Settlement settlement,
            Rotation rotation, Reach reach) {
        if (!settlement.walled()) {
            return Optional.empty();
        }
        BlockState stone = settlement.craft().wall();
        int[] origin = originNorth.apply(settlement);
        List<BlockPos> columns = footprint(settlement.center(), origin, rotation);
        List<Integer> profile = new ArrayList<>(columns.size());

        // Every column in the footprint is one the structure stands on - the footprint is the
        // template's occupied columns, not its box - so every one has to be there, dry and
        // walkable. Read down through our own masonry, because a structure that is already up
        // reads as ground ten blocks above the ground.
        for (BlockPos column : columns) {
            if (!level.hasChunkAt(column) || !reach.has(column)) {
                return Optional.empty();
            }
            int ground = GridSurvey.footingOrSkip(level, column.getX(), column.getZ(), stone);
            if (ground == Ground.SKIP) {
                return Optional.empty();
            }
            profile.add(ground);
        }
        if (isStanding(level, settlement, origin, rotation, profile)) {
            return Optional.empty();
        }
        if (siteTrouble(profile) != null) {
            return Optional.empty();   // the ways in are not level, or a hillside is in the way
        }
        SyVillage.LOGGER.debug("Planned a {} for '{}' turned {}", what, settlement.name(),
                rotation);
        return Optional.of(recipe(settlement, rotation, profile,
                Clearance.spans(level, columns, profile)));
    }

    /**
     * The recipe for this structure on this ground.
     *
     * <p>The origin is frozen into the recipe's extent, so that expansion does not ask the
     * settlement where its wall is - a settlement that grew between planning and laying would
     * otherwise move the structure out from under a job in the queue.
     */
    public BuildRecipe recipe(Settlement settlement, Rotation rotation, List<Integer> profile,
            List<Spans> spans) {
        int[] origin = originNorth.apply(settlement);
        return new BuildRecipe(id, settlement.center(), rotation,
                settlement.craft().paletteId(), List.copyOf(profile),
                new BlockPos(origin[0], template().sizeY(), origin[1]), Spans.encode(spans));
    }

    /**
     * The level the structure stands at: the ground under its ways in.
     *
     * <p>A gatehouse stands where its arch meets the road and its stairs meet the street; a
     * tower where its ground stairs do. Levelled against the highest ground under the whole
     * footprint instead, either stood a storey above its own front door on any hillside. If a
     * template has no ways in at all, the solid columns decide, as they used to.
     */
    private int floorOf(List<Integer> profile) {
        List<Integer> under = groundUnder(template().entrances(), profile);
        if (under.isEmpty()) {
            Template template = template();
            List<int[]> columns = template.columns();
            for (int i = 0; i < columns.size(); i++) {
                if (template.solidToTop(columns.get(i)[0], columns.get(i)[1])) {
                    under.add(profile.get(i));
                }
            }
        }
        return Ground.highest(under);
    }

    /** The profile entries for these template columns. */
    private List<Integer> groundUnder(List<int[]> wanted, List<Integer> profile) {
        List<int[]> columns = template().columns();
        List<Integer> out = new ArrayList<>();
        for (int[] w : wanted) {
            for (int i = 0; i < columns.size(); i++) {
                if (columns.get(i)[0] == w[0] && columns.get(i)[1] == w[1]) {
                    out.add(profile.get(i));
                    break;
                }
            }
        }
        return out;
    }

    /**
     * What is wrong with this ground for this structure, or null if nothing.
     *
     * <p>Two things can be. The ways in have to be level with each other - the road under
     * the arch and the foot of the stairs are the same layer of the template, so they had
     * better be the same height in the world. And no column may stand more than one above
     * the floor: one block of hillside is built over, more would bury the structure, and we
     * do not cut the hill. Low ground is fine; the foundation reaches down to it.
     *
     * <p>Said in words so that the idle report can say them, because a gatehouse that never
     * appears is indistinguishable from one that is broken unless something says why.
     */
    public String siteTrouble(List<Integer> profile) {
        Template template = template();
        List<Integer> entrances = groundUnder(template.entrances(), profile);
        if (!entrances.isEmpty()) {
            int lowest = Ground.SKIP;
            int highest = Ground.SKIP;
            for (int g : entrances) {
                lowest = lowest == Ground.SKIP ? g : Math.min(lowest, g);
                highest = highest == Ground.SKIP ? g : Math.max(highest, g);
            }
            // The same tolerance a lot is held to, which by default is none. One block was
            // tried - the floor to the highest way in, a block of foundation under the others -
            // and the step it left at the foot of the stairs looked wrong. A structure that
            // waits for the player to level its doorstep is the loop this mod is built on.
            if (highest - lowest > SyVillageConfig.MAX_CELL_SLOPE.get()) {
                return "the ways in are not level (" + lowest + " to " + highest + ")";
            }
        }
        int floor = floorOf(profile);
        if (floor == Ground.SKIP) {
            return "no ground to stand on";
        }
        int above = 0;
        int[] first = null;
        List<int[]> columns = template.columns();
        for (int i = 0; i < columns.size(); i++) {
            if (profile.get(i) > floor + 1) {
                above++;
                if (first == null) {
                    first = columns.get(i);
                }
            }
        }
        if (above > 0) {
            return above + " column(s) of hillside above the floor at " + floor
                    + ", first at template " + first[0] + "," + first[1];
        }
        return null;
    }

    public String status(ServerLevel level, Settlement settlement, Rotation rotation,
            Reach reach) {
        int[] origin = originNorth.apply(settlement);
        List<BlockPos> columns = footprint(settlement.center(), origin, rotation);
        List<Integer> profile = grounds(level, columns, settlement.craft().wall());
        if (isStanding(level, settlement, origin, rotation, profile)) {
            return "standing";
        }
        String ground = trouble(level, columns, reach, i -> true);
        if (!"nothing".equals(ground)) {
            return ground;
        }
        String site = siteTrouble(profile);
        return site == null ? "nothing" : site;
    }

    /** The footing under a footprint, with water marked rather than guessed at. */
    static List<Integer> grounds(ServerLevel level, List<BlockPos> columns, BlockState ours) {
        List<Integer> out = new ArrayList<>(columns.size());
        for (BlockPos column : columns) {
            out.add(level.hasChunkAt(column)
                    ? GridSurvey.footingOrSkip(level, column.getX(), column.getZ(), ours)
                    : Ground.SKIP);
        }
        return out;
    }

    /**
     * Whether this structure is already up: our masonry at the top of the column the template
     * names as its probe, at the height it would be if the structure stood on this floor.
     */
    private boolean isStanding(ServerLevel level, Settlement settlement, int[] origin,
            Rotation rotation, List<Integer> profile) {
        int floor = floorOf(profile);
        if (floor == Ground.SKIP) {
            return false;
        }
        int[] probe = template().probe();
        BlockPos column = Template.columnAt(settlement.center(), origin, rotation, probe[0],
                probe[1]);
        return level.getBlockState(new BlockPos(column.getX(), floor + 1 + probe[2],
                column.getZ())).is(settlement.craft().wall().getBlock());
    }

    /**
     * Why a footprint cannot be built on, counted by reason, with the first blocking column
     * named. Every column of a template footprint is one the structure stands on, so the
     * "under the build" count and the total are the same thing here.
     */
    public static String trouble(ServerLevel level, List<BlockPos> columns, Reach reach,
            IntPredicate ours) {
        int unloaded = 0;
        int unreachable = 0;
        int wet = 0;
        int blocking = 0;
        BlockPos first = null;

        for (int i = 0; i < columns.size(); i++) {
            BlockPos column = columns.get(i);
            boolean bad = true;
            if (!level.hasChunkAt(column)) {
                unloaded++;
            } else if (GridSurvey.groundOrSkip(level, column.getX(), column.getZ())
                    == Ground.SKIP) {
                wet++;
            } else if (!reach.has(column)) {
                unreachable++;
            } else {
                bad = false;
            }
            if (bad && ours.test(i)) {
                blocking++;
                if (first == null) {
                    first = column;
                }
            }
        }
        if (unloaded + unreachable + wet == 0) {
            return "nothing";
        }
        return unreachable + " unreachable, " + wet + " water, " + unloaded + " unloaded of "
                + columns.size() + " - " + blocking + " under the build"
                + (first == null ? "" : ", first at " + first.getX() + "," + first.getZ());
    }

    /**
     * The structure, as blocks.
     *
     * <p>Foundation first, under every column whose lowest template layer is masonry: up to and
     * including the floor, so nothing floats where the ground falls away. Then the template,
     * turned and in the palette's materials, with its lowest layer on the floor. Then the
     * felling of whatever grew on the site, everywhere the structure did not just write.
     */
    public List<BuildOp> expand(BuildRecipe recipe) {
        Template template = template();
        List<int[]> columns = template.columns();
        List<Integer> profile = recipe.groundProfile();
        if (profile.size() != columns.size()) {
            return List.of();
        }
        int floor = floorOf(profile);
        if (floor == Ground.SKIP) {
            return List.of();
        }
        BlockPos bell = recipe.anchor();
        int[] origin = {recipe.extent().getX(), recipe.extent().getZ()};
        Rotation rotation = recipe.rotation();
        Craft craft = Craft.fromPalette(recipe.palette());
        BlockState stone = craft.wall();
        List<BuildOp> ops = new ArrayList<>();

        // Under every column that has something on the template's lowest layer - masonry,
        // a stair, a fence post. A stair with nothing under it is a stair over a hole.
        for (int i = 0; i < columns.size(); i++) {
            int[] column = columns.get(i);
            if (!template.hasBase(column[0], column[1])) {
                continue;
            }
            BlockPos at = Template.columnAt(bell, origin, rotation, column[0], column[1]);
            for (int y = profile.get(i) + 1; y <= floor; y++) {
                ops.add(new BuildOp(new BlockPos(at.getX(), y, at.getZ()), stone));
            }
        }
        ops.addAll(template.place(bell, origin, rotation, craft, floor));
        ops.addAll(Clearance.ops(Spans.decode(recipe.gates()), written(ops)));
        ops.sort(Comparator.comparingInt((BuildOp op) -> op.pos().getY())
                .thenComparingInt(op -> op.pos().getX())
                .thenComparingInt(op -> op.pos().getZ()));
        return List.copyOf(ops);
    }

    /** The positions a build has already spoken for. */
    static Set<BlockPos> written(List<BuildOp> ops) {
        Set<BlockPos> out = new HashSet<>(ops.size());
        for (BuildOp op : ops) {
            out.add(op.pos());
        }
        return out;
    }
}
