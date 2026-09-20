package com.syang.syvillage.build;

import com.syang.syvillage.SyVillage;
import com.syang.syvillage.data.BuildOp;
import com.syang.syvillage.data.BuildRecipe;
import com.syang.syvillage.data.CellPos;
import com.syang.syvillage.data.Craft;
import com.syang.syvillage.data.Settlement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

/**
 * A vanilla village building on a lot the plan already chose.
 *
 * <p>There is no site search. Which lot gets built on is decided by walking the town plan
 * outward from the bell and taking the first one that is empty, level and walkable; which
 * building goes on it is {@link Houses#pick}. What this file decides is only how the template
 * sits on the lot: turned so its front is on the street, and pushed up against that edge so the
 * doorstep meets the margin the street runs along.
 *
 * <p>The cottage this replaces was a five-by-five rule, and it was a cube. A vanilla house fits
 * the whole seven-by-seven lot roof and all - the five-by-five was only ever the floor, with the
 * roof allowed out to the lot - and it brings its own materials, so there is no house palette.
 */
public final class HousePlan {

    private HousePlan() {}

    /**
     * Which wall the door is in: the one with the street behind it.
     *
     * <p>A city block holds two lots per axis, so a lot does not have a road on all four sides.
     * The northern of the pair has its street to the north and the southern has its street to
     * the south - it is a fact about which half of the block the lot is in, not a judgement.
     */
    public static Direction doorFacing(CellPos cell) {
        return Math.floorMod(cell.gz(), TownPlan.LOTS_PER_BLOCK) == 0
                ? Direction.NORTH : Direction.SOUTH;
    }

    /** The turn that takes a template's own front to where the street is. */
    public static Rotation turnTo(Direction front, Direction street) {
        for (Rotation rotation : Rotation.values()) {
            if (rotation.rotate(front) == street) {
                return rotation;
            }
        }
        return Rotation.NONE;
    }

    /**
     * Where the template's turned box sits: against the street edge of the lot, centred along
     * it. A template narrower than the lot leaves the margin at the sides, where the old
     * cottage left it all round.
     */
    public static BlockPos originOf(Template template, Rotation rotation, CellPos cell, BlockPos bell,
            Direction street) {
        BlockPos lot = TownPlan.lotCorner(cell, bell);
        int wide = template.turnedWidth(rotation);
        int deep = template.turnedDepth(rotation);
        int dx = (TownPlan.LOT - wide) / 2;
        int dz = street == Direction.NORTH ? 0 : TownPlan.LOT - deep;
        return lot.offset(dx, 0, dz);
    }

    public static Optional<BuildRecipe> plan(ServerLevel level, Settlement settlement,
            CellPos cell, Identifier id) {
        Template template = Template.ensure(level.getStructureManager(), id);
        Direction street = doorFacing(cell);
        Rotation rotation = turnTo(template.front() == null ? Direction.NORTH : template.front(),
                street);
        BlockPos origin = originOf(template, rotation, cell, settlement.center(), street);

        List<BlockPos> columns = template.columnsAt(origin, rotation);
        List<Integer> profile = new ArrayList<>(columns.size());
        for (BlockPos column : columns) {
            if (!level.hasChunkAt(column)) {
                return Optional.empty();
            }
            int ground = GridSurvey.groundOrSkip(level, column.getX(), column.getZ());
            if (ground == Ground.SKIP) {
                return Optional.empty();
            }
            profile.add(ground);
        }
        // The clearance covers the whole lot, not just the house: a trunk a block from the wall
        // is still a tree the settlement decided was not there when it called the site flat.
        List<Spans> clearance = Clearance.spans(level,
                TownPlan.lotColumns(cell, settlement.center()));
        SyVillage.LOGGER.debug("Planned {} for '{}' on lot {}, door {}", id.getPath(),
                settlement.name(), cell.toKey(), street);
        return Optional.of(recipe(settlement, id, origin, rotation, profile, clearance));
    }

    /** The recipe for a building at a known origin on known ground. Public for tests. */
    public static BuildRecipe recipe(Settlement settlement, Identifier id, BlockPos origin,
            Rotation rotation, List<Integer> profile, List<Spans> clearance) {
        Template template = Template.of(id);
        return new BuildRecipe(id, origin, rotation, settlement.craft().paletteId(),
                List.copyOf(profile),
                new BlockPos(template.turnedWidth(rotation), template.sizeY(),
                        template.turnedDepth(rotation)),
                Spans.encode(clearance));
    }

    /**
     * The building, as blocks.
     *
     * <p>A vanilla house is authored with its floor at ground level - layer 0 replaces the
     * surface block, the door is on layer 1 - so it sits one lower than our own templates do.
     * {@link Template#lift} knows which is which. The floor is the ground under the ways in,
     * which on a lot the plan has already called level is the ground everywhere.
     */
    public static List<BuildOp> expand(BuildRecipe recipe) {
        Template template = Template.of(recipe.template());
        List<int[]> columns = template.columns();
        List<Integer> profile = recipe.groundProfile();
        if (profile.size() != columns.size()) {
            return List.of();
        }
        int floor = floorOf(template, profile);
        if (floor == Ground.SKIP) {
            return List.of();
        }
        Craft craft = Craft.fromPalette(recipe.palette());
        BlockPos origin = recipe.anchor();
        Rotation rotation = recipe.rotation();
        int lift = template.lift();
        List<BuildOp> ops = new ArrayList<>();

        // Foundation under every column with something on the lowest layer, up to just under
        // where that layer goes. For a vanilla house that is the block the floor replaces.
        for (int i = 0; i < columns.size(); i++) {
            int[] column = columns.get(i);
            if (!template.hasBase(column[0], column[1])) {
                continue;
            }
            BlockPos at = template.columnAt(origin, rotation, column[0], column[1]);
            for (int y = profile.get(i) + 1; y <= floor + lift - 1; y++) {
                ops.add(new BuildOp(new BlockPos(at.getX(), y, at.getZ()), craft.foundation()));
            }
        }
        ops.addAll(template.placeAt(origin, rotation, craft, floor + lift));
        ops.addAll(Clearance.ops(Spans.decode(recipe.gates()), TemplatePlan.written(ops)));
        ops.sort(Comparator.comparingInt((BuildOp op) -> op.pos().getY())
                .thenComparingInt(op -> op.pos().getX())
                .thenComparingInt(op -> op.pos().getZ()));
        return List.copyOf(ops);
    }

    /** The ground under the ways in; or, for a template with none, the highest ground. */
    private static int floorOf(Template template, List<Integer> profile) {
        List<int[]> columns = template.columns();
        List<Integer> under = new ArrayList<>();
        for (int[] way : template.entrances()) {
            for (int i = 0; i < columns.size(); i++) {
                if (columns.get(i)[0] == way[0] && columns.get(i)[1] == way[1]) {
                    under.add(profile.get(i));
                    break;
                }
            }
        }
        return under.isEmpty() ? Ground.highest(profile) : Ground.highest(under);
    }
}
