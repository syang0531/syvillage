package com.syang.placitum.build;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.CellPos;
import com.syang.placitum.data.Settlement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

/**
 * A cottage on a lot the plan already chose.
 *
 * <p>There is no site search left. Which lot gets built on is decided by walking the town plan
 * outward from the bell and taking the first one that is empty and level - and every bug the old
 * search produced (houses down slopes, houses on other houses' roofs, houses on the wall) came
 * from a search that was right about one condition and wrong about another.
 */
public final class HousePlanner {

    public static final Identifier COTTAGE =
            Identifier.fromNamespaceAndPath(Placitum.MODID, "house/cottage");

    private HousePlanner() {}

    /**
     * Freezes a cottage for this lot.
     *
     * <p>The door faces north or south, never east or west. Every lot has a street on all four
     * sides, so the choice is about how the place reads rather than about access: houses whose
     * doors all face the nearest street look like a street, and doors picked per-house by
     * whatever happened to be closest look like a car park.
     */
    public static Optional<BuildRecipe> plan(ServerLevel level, Settlement settlement,
            CellPos cell) {
        BlockPos corner = TownPlan.buildingCorner(cell, settlement.center());
        Rotation facing = doorFacing(cell);

        List<Integer> profile = new ArrayList<>();
        for (BlockPos column : CottagePlan.footprint(corner, facing)) {
            if (!level.hasChunkAt(column)) {
                return Optional.empty();
            }
            profile.add(GridSurvey.groundOrSkip(level, column.getX(), column.getZ()));
        }
        // The clearance covers the whole lot, not just the house: a trunk a block from the wall
        // is still a tree the settlement decided was not there when it called the site flat.
        List<Spans> clearance = Clearance.spans(level,
                TownPlan.lotColumns(cell, settlement.center()));
        Placitum.LOGGER.debug("Planned a {} cottage for '{}' on lot {}, door {}",
                settlement.craft().getSerializedName(), settlement.name(), cell.toKey(),
                CottagePlan.doorFacing(facing));

        return Optional.of(new BuildRecipe(COTTAGE, corner, facing,
                settlement.craft().paletteId(),
                List.copyOf(profile),
                new BlockPos(CottagePlan.SIDE, CottagePlan.HEIGHT, CottagePlan.SIDE),
                Spans.encode(clearance)));
    }

    /**
     * Which wall the door is in: the one with the street behind it.
     *
     * <p>A city block holds two lots per axis, so a lot does not have a road on all four sides.
     * The northern of the pair has its street to the north and the southern has its street to
     * the south - it is a fact about which half of the block the lot is in, not a judgement, and
     * the previous rule (north of the bell face north) had houses in the middle of a block
     * opening onto their neighbour's back wall.
     */
    public static Rotation doorFacing(CellPos cell) {
        return Math.floorMod(cell.gz(), TownPlan.LOTS_PER_BLOCK) == 0
                ? Rotation.NONE            // the near street is to the north
                : Rotation.CLOCKWISE_180;  // ... and for the southern lot, to the south
    }
}
