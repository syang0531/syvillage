package com.syang.placitum.sim.module;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.AlertState;
import com.syang.placitum.data.BuildJob;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.BuildStage;
import com.syang.placitum.data.WallTier;
import com.syang.placitum.sim.SimModule;
import com.syang.placitum.sim.SimParams;
import com.syang.placitum.store.SettlementMut;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;

/**
 * Notices what the settlement is short of, and orders one thing at a time. Order 45.
 *
 * <p>Between population and construction: it needs this step's births and deaths to judge what
 * is short, and construction needs its order to work on.
 *
 * <p>One job at a time, on purpose. A settlement that ordered a wall, three houses and a farm
 * at once would reserve materials for all of them, start none, and report four things waiting -
 * which tells the player nothing about what to do first. The priority list exists precisely so
 * that something is first.
 */
public class NeedsModule implements SimModule {

    @Override
    public void step(SettlementMut settlement, SimParams params, RandomSource rng) {
        if (!settlement.buildQueue.isEmpty()) {
            return;   // finish what was started
        }
        // docs/construction.md: food, housing, defence, production, convenience - except that
        // defence goes first the moment the horn has sounded. Only defence exists so far; the
        // ordering is written out anyway so the next author adds a branch rather than a policy.
        if (needsWall(settlement, params)) {
            order(settlement, wallOrder(settlement));
        }
    }

    /**
     * Whether it is time to fortify.
     *
     * <p>A hamlet of two does not need a palisade, it needs more people, and timber spent on a
     * wall is timber not spent on a house. The population floor is what keeps the first thing a
     * settlement ever builds from being the least useful one.
     */
    private boolean needsWall(SettlementMut settlement, SimParams params) {
        if (settlement.defense.wall().tier() != WallTier.NONE) {
            return false;
        }
        boolean underThreat = settlement.defense.alert() != AlertState.PEACE;
        return underThreat || settlement.population() >= params.wallMinPopulation();
    }

    /**
     * The order, with no recipe in it yet.
     *
     * <p>PLANNED means "somebody has to look at the ground first". The recipe cannot be frozen
     * here: freezing it means sampling terrain, sampling terrain means loaded chunks, and this
     * runs in settlements nobody has visited for a week. The job waits in the queue until a
     * player walks in, which is the same bargain every other world-reading task in this mod
     * makes.
     */
    private BuildJob wallOrder(SettlementMut settlement) {
        return new BuildJob(
                UUID.randomUUID(),
                settlement.identity.id(),
                new BuildRecipe(
                        Identifier.fromNamespaceAndPath(Placitum.MODID, "wall/palisade"),
                        settlement.identity.center(),
                        Rotation.NONE,
                        Identifier.fromNamespaceAndPath(Placitum.MODID, "biome_palette/plains"),
                        List.of(),
                        BlockPos.ZERO),
                0,
                Map.of(),
                BuildStage.PLANNED);
    }

    private void order(SettlementMut settlement, BuildJob job) {
        settlement.buildQueue.add(job);
        Placitum.LOGGER.debug("  step {}: '{}' wants a {}", settlement.simStep(),
                settlement.identity.name(), job.recipe().template().getPath());
    }

    @Override
    public int order() {
        return 45;
    }

    @Override
    public String name() {
        return "needs";
    }
}
