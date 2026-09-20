package com.syang.syvillage.build;

import com.syang.syvillage.SyVillage;
import com.syang.syvillage.data.BuildOp;
import com.syang.syvillage.data.BuildRecipe;
import com.syang.syvillage.data.Settlement;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

/**
 * A watchtower on each corner of the wall.
 *
 * <p>A template: {@code data/syvillage/structure/tower.nbt}, built by hand and saved with a
 * structure block, authored as the north-west corner. Seventeen square: a nine-by-nine tower
 * standing two proud of the wall on both outer faces, and an arm along each of the two walls
 * that meet there, with the steps from the town up to the walkway and from the walkway to the
 * top cut into it. The other three corners are the same template turned about the bell.
 *
 * <p>The old tower was a rule - a solid bastion with ramps cut into the rampart - and every
 * bug it had came from the rule being read three different ways in three places. A template
 * is read one way.
 */
public final class TowerPlan {

    public static final Identifier TOWER =
            Identifier.fromNamespaceAndPath(SyVillage.MODID, "wall/tower");

    private static final int PROUD = TownPlan.STRUCTURE_PROUD;

    private static final TemplatePlan PLAN = new TemplatePlan(TOWER, "tower",
            TowerPlan::originNorthWest, "tower");

    private TowerPlan() {}

    /** The four corners, as the pair of signs that puts a tower in that quadrant. */
    public static List<int[]> corners() {
        return List.of(new int[] {-1, -1}, new int[] {1, -1}, new int[] {1, 1}, new int[] {-1, 1});
    }

    /**
     * Where the template's (0, 0) sits relative to the bell, as the north-west corner.
     *
     * <p>The template's own (0, 0) is its outermost corner, two beyond the wall's outer face on
     * both axes.
     */
    private static int[] originNorthWest(Settlement settlement) {
        int out = TownPlan.wallOuter(settlement) + PROUD;
        return new int[] {-out, -out};
    }

    /** Which corner this is, carried in the recipe as a rotation of the north-west template. */
    public static Rotation quadrantOf(int[] corner) {
        if (corner[0] < 0) {
            return corner[1] < 0 ? Rotation.NONE : Rotation.COUNTERCLOCKWISE_90;
        }
        return corner[1] < 0 ? Rotation.CLOCKWISE_90 : Rotation.CLOCKWISE_180;
    }

    /** Every column of the tower in the world, in template order. */
    public static List<BlockPos> footprint(Settlement settlement, int[] corner) {
        return PLAN.footprint(settlement, quadrantOf(corner));
    }

    public static Optional<BuildRecipe> plan(ServerLevel level, Settlement settlement,
            int[] corner, Reach reach) {
        return PLAN.plan(level, settlement, quadrantOf(corner), reach);
    }

    public static List<BuildOp> expand(BuildRecipe recipe) {
        return PLAN.expand(recipe);
    }

    /** Standing, or what is stopping it. For the log when a settlement has gone quiet. */
    public static String status(ServerLevel level, Settlement settlement, int[] corner,
            Reach reach) {
        return PLAN.status(level, settlement, quadrantOf(corner), reach);
    }

    /** The recipe for a tower on known ground. For tests, which have no level to read one from. */
    public static BuildRecipe recipe(Settlement settlement, int[] corner, List<Integer> profile,
            List<Spans> spans) {
        return PLAN.recipe(settlement, quadrantOf(corner), profile, spans);
    }

    /** The template itself, for tests that want to look at the shape. */
    public static Template template() {
        return PLAN.template();
    }
}
