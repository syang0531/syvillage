package com.syang.placitum.build;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.BuildJob;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.BuildStage;
import com.syang.placitum.data.Settlement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The half of building that needs the world: PLAN and QUEUE.
 *
 * <p>A virtual settlement can decide it wants a wall, and can lay one block at a time once it
 * knows what the wall is. What it cannot do is look at the ground - and the recipe is nothing
 * but frozen ground. So the ordering runs in the simulation, the freezing runs here, and a
 * settlement nobody has visited holds at PLANNED until somebody walks in.
 *
 * <p>That wait is a real cost and worth naming: a village of four that nobody visits will queue
 * a palisade and never start it. The alternative is reading chunks from a simulation step,
 * which is the one thing principle 2 forbids outright.
 */
public final class ConstructionTick {

    private ConstructionTick() {}

    /** Freezes any job still waiting on a look at the ground. */
    public static Settlement run(ServerLevel level, Settlement settlement) {
        if (settlement.buildQueue().isEmpty()) {
            return settlement;
        }
        List<BuildJob> next = new ArrayList<>(settlement.buildQueue().size());
        boolean changed = false;

        for (BuildJob job : settlement.buildQueue()) {
            if (job.stage() != BuildStage.PLANNED && job.stage() != BuildStage.RESERVED) {
                next.add(job);
                continue;
            }
            BuildJob frozen = freeze(level, settlement, job);
            changed |= frozen != job;
            if (frozen != null) {
                next.add(frozen);
            }
        }
        return changed ? settlement.withBuildQueue(List.copyOf(next)) : settlement;
    }

    /** Null drops the job: there was nothing there to build. */
    private static BuildJob freeze(ServerLevel level, Settlement settlement, BuildJob job) {
        Optional<BuildRecipe> planned = WallPlanner.plan(level, settlement);
        if (planned.isEmpty()) {
            Placitum.LOGGER.debug("Dropping the wall order for '{}': nothing to enclose",
                    settlement.name());
            return null;
        }
        BuildRecipe recipe = planned.get();
        List<com.syang.placitum.data.BuildOp> ops = BuildPlanner.expand(recipe);
        if (ops.isEmpty()) {
            return null;
        }
        // Clearing a doorway is work, not material. Charging a log for every op would bill the
        // settlement for the air it takes out of its own gateways.
        int timber = 0;
        for (com.syang.placitum.data.BuildOp op : ops) {
            if (!op.state().isAir()) {
                timber++;
            }
        }
        Placitum.LOGGER.info("'{}' queued a {}: {} op(s), {} log(s)", settlement.name(),
                recipe.template().getPath(), ops.size(), timber);
        return new BuildJob(job.id(), job.plotId(), recipe, 0, costOf(timber),
                BuildStage.QUEUED);
    }

    /**
     * What it costs.
     *
     * <p>One log a block, which is the honest price: a palisade is logs, and charging less would
     * make the wall free in every way that matters. Salvage on demolition is what keeps
     * expanding a settlement from being ruinous - docs/construction.md, and M4's problem.
     */
    private static Map<Item, Integer> costOf(int blocks) {
        Map<Item, Integer> cost = new LinkedHashMap<>();
        cost.put(Items.OAK_LOG, blocks);
        return cost;
    }
}
