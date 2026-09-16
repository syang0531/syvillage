package com.syang.placitum.build;

import com.syang.placitum.data.BuildOp;
import com.syang.placitum.data.BuildRecipe;
import java.util.List;

/**
 * Turns a recipe into the list of blocks it means.
 *
 * <p>A pure function of the recipe and nothing else. Op lists are never stored - a cottage is a
 * couple of hundred of them and the settlement save is rewritten whole whenever it changes - so
 * everything the world had to say was said once, at planning time, and frozen into
 * {@code groundProfile}.
 *
 * <p>Reading a block here would make the same recipe produce a different building on a different
 * day, which is the difference between a build that can be tested and one that can only be
 * watched.
 */
public final class BuildPlanner {

    private BuildPlanner() {}

    public static List<BuildOp> expand(BuildRecipe recipe) {
        if (recipe.template().equals(HousePlanner.COTTAGE)) {
            return CottagePlan.expand(recipe);
        }
        if (recipe.template().equals(RoadPlan.CROSS)) {
            return RoadPlan.expand(recipe);
        }
        if (recipe.template().equals(LampPlan.LAMPS)) {
            return LampPlan.expand(recipe);
        }
        if (recipe.template().equals(FarmPlan.FIELD)) {
            return FarmPlan.expand(recipe);
        }
        return List.of();
    }
}
