package com.syang.syvillage.build;

import com.syang.syvillage.SyVillage;
import com.syang.syvillage.data.BuildOp;
import com.syang.syvillage.data.BuildRecipe;
import com.syang.syvillage.data.Settlement;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

/**
 * A gatehouse, where one of the bell's two roads leaves the town.
 *
 * <p>Four of them, and nobody works out where. The bell stands in the middle of a crossroads
 * and those roads run to the wall, so a gate is where the wall is and the bell's road still is
 * - which, because the roads are centred on the bell, puts each one dead centre of its side.
 *
 * <p>The shape is a template, built by hand in a creative world and saved with a structure
 * block: {@code data/syvillage/structure/gatehouse.nbt}, twenty-five across the road, nine
 * deep, ten tall, authored facing north. The road passes through the middle of it under a
 * three-wide arch with fence gates the player may open or leave shut; the rampart's own
 * cross-section runs through its two ends at template rows 2 to 6; the steps from the town up
 * to the walkway and from the walkway up to the deck are cut into it, which is why there is no
 * longer a separate flight of steps anywhere.
 *
 * <p>Everything it used to compute for itself - the arch, the ramps, the deck, the merlons, the
 * lanterns - is now somebody's build. What this file still knows is where it goes.
 */
public final class GatePlan {

    public static final Identifier GATEHOUSE =
            Identifier.fromNamespaceAndPath(SyVillage.MODID, "wall/gatehouse");

    /** How far the template stands proud of the wall's outer face. Rows 0 and 1 of it. */
    private static final int PROUD = TownPlan.STRUCTURE_PROUD;

    private static final TemplatePlan PLAN = new TemplatePlan(GATEHOUSE, "gatehouse",
            GatePlan::originNorth, "gatehouse");

    private GatePlan() {}

    /** The four outward directions a gate faces, in a fixed order. */
    public static List<Direction> sides() {
        return List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    }

    /**
     * Where the template's (0, 0) sits relative to the bell, facing north.
     *
     * <p>Its road runs down the middle column, so that column goes on the bell's own x; its row
     * 0 is the outermost, two beyond the wall's outer face.
     */
    private static int[] originNorth(Settlement settlement) {
        return new int[] {-(TownPlan.GATE_WIDE / 2), -(TownPlan.wallOuter(settlement) + PROUD)};
    }

    public static Rotation rotationOf(Direction side) {
        return switch (side) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public static Direction sideOf(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> Direction.EAST;
            case CLOCKWISE_180 -> Direction.SOUTH;
            case COUNTERCLOCKWISE_90 -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }

    /** Every column of the gatehouse in the world, in template order. */
    public static List<BlockPos> footprint(Settlement settlement, Direction side) {
        return PLAN.footprint(settlement, rotationOf(side));
    }

    public static Optional<BuildRecipe> plan(ServerLevel level, Settlement settlement,
            Direction side, Reach reach) {
        return PLAN.plan(level, settlement, rotationOf(side), reach);
    }

    public static List<BuildOp> expand(BuildRecipe recipe) {
        return PLAN.expand(recipe);
    }

    /** Standing, or what is stopping it. For the log when a settlement has gone quiet. */
    public static String status(ServerLevel level, Settlement settlement, Direction side,
            Reach reach) {
        return PLAN.status(level, settlement, rotationOf(side), reach);
    }

    /** The recipe for a gate on known ground. For tests, which have no level to read one from. */
    public static BuildRecipe recipe(Settlement settlement, Direction side, List<Integer> profile,
            List<Spans> spans) {
        return PLAN.recipe(settlement, rotationOf(side), profile, spans);
    }

    /** What is wrong with this ground for a gatehouse, or null. For tests. */
    public static String siteTrouble(List<Integer> profile) {
        return PLAN.siteTrouble(profile);
    }

    /** The template itself, for tests that want to look at the shape. */
    public static Template template() {
        return PLAN.template();
    }
}
