package com.syang.placitum.sim;

import com.syang.placitum.config.PlacitumConfig;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;

/**
 * The balance numbers one simulation run needs, resolved once at the edge.
 *
 * <p>The modules take values rather than reaching for config handles. That keeps the
 * deterministic core a pure function of (state, params, seed), which is what makes the
 * equivalence test possible at all - a config handle cannot be read before the config file has
 * loaded, so a simulation that reads config directly cannot be unit tested.
 */
public record SimParams(
        int stepTicks,
        long maxCatchupTicks,
        int consumptionPerHead,
        int yieldRate,
        int timberRate,
        int opsPerBuilderStep,
        int wallMinPopulation,
        boolean hostilesExist,
        PopulationParams population) {

    /**
     * Everything docs/population.md owns.
     *
     * <p>Grouped rather than flattened: SimParams would otherwise grow a dozen loose ints, and a
     * call site passing those positionally is a bug waiting to be written.
     */
    public record PopulationParams(
            double baseBirthRate,
            int elderThresholdDays,
            int infantDays,
            int childDays,
            int baseSafety,
            int minSafety,
            int safetyRatingDivisor,
            int safetyDeathPenalty,
            int foodWarningSteps,
            int famineGraceSteps,
            int famineMoralePenalty,
            double famineDeathChancePerStep,
            double elderDeathChancePerStep,
            boolean agingEnabled) {}

    /**
     * Reads the values, including whether this world has anything hostile in it.
     *
     * <p>Difficulty has to come in as a value like everything else. On Peaceful no hostile mob
     * ever spawns, so a real raid cannot happen - and a virtual raid that kills villagers anyway
     * would be the two halves of the game giving different answers in the most visible way
     * there is. docs/defense.md rules that out, and a player would notice immediately.
     */
    public static SimParams fromConfig(Level level) {
        return new SimParams(
                PlacitumConfig.STEP_TICKS.get(),
                PlacitumConfig.MAX_CATCHUP_TICKS.get(),
                PlacitumConfig.CONSUMPTION_PER_HEAD.get(),
                PlacitumConfig.YIELD_RATE.get(),
                PlacitumConfig.TIMBER_RATE.get(),
                opsPerBuilderStep(PlacitumConfig.STEP_TICKS.get(),
                        PlacitumConfig.BUILD_OP_INTERVAL_TICKS.get()),
                PlacitumConfig.WALL_MIN_POPULATION.get(),
                level.getDifficulty() != Difficulty.PEACEFUL,
                populationFromConfig());
    }

    private static PopulationParams populationFromConfig() {
        return new PopulationParams(
                PlacitumConfig.BASE_BIRTH_RATE.get(),
                PlacitumConfig.ELDER_THRESHOLD_DAYS.get(),
                PlacitumConfig.INFANT_DAYS.get(),
                PlacitumConfig.CHILD_DAYS.get(),
                PlacitumConfig.BASE_SAFETY.get(),
                PlacitumConfig.MIN_SAFETY.get(),
                PlacitumConfig.SAFETY_RATING_DIVISOR.get(),
                PlacitumConfig.SAFETY_DEATH_PENALTY.get(),
                PlacitumConfig.FOOD_WARNING_STEPS.get(),
                PlacitumConfig.FAMINE_GRACE_STEPS.get(),
                PlacitumConfig.FAMINE_MORALE_PENALTY.get(),
                PlacitumConfig.FAMINE_DEATH_CHANCE_PER_STEP.get(),
                PlacitumConfig.ELDER_DEATH_CHANCE_PER_STEP.get(),
                PlacitumConfig.ENABLE_AGING.get());
    }

    /** Defaults matching the shipped config, for tests and for headless tooling. */
    public static SimParams defaults() {
        return new SimParams(200, 72000L, 1, 3, 3, 20, 4, true,
                new PopulationParams(0.02, 90, 3, 20, 4, 4, 4, 3, 180, 18, 4, 0.01, 0.0008, true));
    }

    // Delegating accessors, so modules read params.baseBirthRate() rather than
    // params.population().baseBirthRate() on every line.

    public double baseBirthRate() {
        return population.baseBirthRate();
    }

    public int elderThresholdDays() {
        return population.elderThresholdDays();
    }

    public int infantDays() {
        return population.infantDays();
    }

    public int childDays() {
        return population.childDays();
    }

    public int baseSafety() {
        return population.baseSafety();
    }

    public int minSafety() {
        return population.minSafety();
    }

    public int safetyRatingDivisor() {
        return population.safetyRatingDivisor();
    }

    public int safetyDeathPenalty() {
        return population.safetyDeathPenalty();
    }

    public int foodWarningSteps() {
        return population.foodWarningSteps();
    }

    public int famineGraceSteps() {
        return population.famineGraceSteps();
    }

    public int famineMoralePenalty() {
        return population.famineMoralePenalty();
    }

    public double famineDeathChancePerStep() {
        return population.famineDeathChancePerStep();
    }

    public double elderDeathChancePerStep() {
        return population.elderDeathChancePerStep();
    }

    public boolean agingEnabled() {
        return population.agingEnabled();
    }


    /** One game day in steps. Per-step probabilities have to be read against this. */
    /**
     * Blocks one builder lays in a step, while nobody is watching.
     *
     * <p>Derived from the visible rate rather than configured separately. It has to equal what
     * BuildTick lays over the same span, or the wall builds at one speed while you watch and
     * another while you do not - and it did: twenty blocks a step in front of you against four
     * behind your back, so walking away made it five times slower. Two config keys that have to
     * agree are two keys that will not.
     *
     * <p>Resolved here at the edge, like every other number the simulation uses.
     */
    private static int opsPerBuilderStep(int stepTicks, int intervalTicks) {
        return Math.max(1, stepTicks / Math.max(1, intervalTicks));
    }

    public int stepsPerDay() {
        return 24000 / stepTicks;
    }
}
