package com.syang.placitum.sim.module;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.AlertState;
import com.syang.placitum.data.BuildJob;
import com.syang.placitum.data.BuildRecipe;
import com.syang.placitum.data.BuildStage;
import com.syang.placitum.data.WallTier;
import com.syang.placitum.build.HousePlanner;
import com.syang.placitum.build.WallPlanner;
import com.syang.placitum.population.Capacity;
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
        if (working(settlement)) {
            return;   // finish what was started
        }
        // docs/construction.md: food, housing, defence, production, convenience - except that
        // defence goes first the moment the horn has sounded. Only defence exists so far; the
        // ordering is written out anyway so the next author adds a branch rather than a policy.
        boolean underThreat = settlement.defense.alert() != AlertState.PEACE;
        if (underThreat && needsWall(settlement, params)) {
            order(settlement, order(settlement, WallPlanner.PALISADE, rng));
            return;
        }
        if (needsHouse(settlement, params)) {
            order(settlement, order(settlement, HousePlanner.COTTAGE, rng));
            return;
        }
        if (needsWall(settlement, params)) {
            order(settlement, order(settlement, WallPlanner.PALISADE, rng));
        }
    }

    /**
     * Whether anything in the queue still needs doing.
     *
     * <p>COMPLETE jobs linger until a visit lets their blocks be placed, so an empty-looking
     * queue is not the same as an idle one - and treating a finished job as work in progress
     * would stop a settlement ordering anything else until somebody walked past.
     */
    private static boolean working(SettlementMut settlement) {
        for (BuildJob job : settlement.buildQueue) {
            if (job.stage() != BuildStage.COMPLETE) {
                return true;
            }
        }
        return false;
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
     * Whether the settlement is short of somewhere to sleep.
     *
     * <p>Beds are the first thing docs/population.md counts, and a settlement at its bed limit
     * has stopped growing outright. Ordered one house ahead of the need rather than in response
     * to it - building takes days, and a village that waits until it is full has already spent
     * those days not growing.
     */
    private boolean needsHouse(SettlementMut settlement, SimParams params) {
        Capacity capacity = Capacity.of(settlement.freezeView(), params);
        return capacity.bottleneck() == Capacity.Bottleneck.BEDS
                && settlement.population() + 1 >= capacity.value();
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
    private BuildJob order(SettlementMut settlement, Identifier template,
            RandomSource rng) {
        // From the module's own stream, never UUID.randomUUID(). Principle 3 is not a
        // style rule: a random id here made catchUp(1000) and catchUp(100 x 10) encode
        // differently, so two players on different hardware would get different worlds.
        // It went unnoticed while walls were the only thing ordered and the equivalence
        // test caught it the moment houses were.
        return new BuildJob(
                new UUID(rng.nextLong(), rng.nextLong()),
                settlement.identity.id(),
                new BuildRecipe(
                        template,
                        settlement.identity.center(),
                        Rotation.NONE,
                        Identifier.fromNamespaceAndPath(Placitum.MODID, "biome_palette/plains"),
                        List.of(),
                        BlockPos.ZERO,
                        List.of()),
                0,
                Map.of(),
                BuildStage.PLANNED);
    }

    private void order(SettlementMut settlement, BuildJob job) {
        settlement.buildQueue.add(job);
        Placitum.LOGGER.debug("  step {}: '{}' wants a {}", settlement.simStep(),
                settlement.identity.name(), job.recipe().template().getPath());
    }

    /**
     * Yes - this is the module the blanket embodied guard was hurting most.
     *
     * <p>A settlement could otherwise only notice it wanted something while nobody was there,
     * and only act on it while somebody was. Nothing here reads a resident's output, so there
     * is nothing to count twice.
     */
    @Override
    public boolean runsWhileEmbodied() {
        return true;
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
