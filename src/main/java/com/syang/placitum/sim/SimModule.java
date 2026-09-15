package com.syang.placitum.sim;

import com.syang.placitum.store.SettlementMut;
import net.minecraft.util.RandomSource;

/**
 * One unit of simulation, advanced in fixed steps.
 *
 * <p>Modules never call each other. Ordering is the only coupling, and it is fixed in
 * docs/simulation.md because it affects balance: needs are judged after this step's
 * completions are already counted, so nothing gets ordered twice.
 */
public interface SimModule {

    /** Advances one step. Must not use any randomness other than {@code rng}. */
    void step(SettlementMut settlement, SimParams params, RandomSource rng);

    /** Lower runs first. Also seeds this module's random stream, so it must stay stable. */
    int order();

    /**
     * Whether this module still has work to do while the residents have bodies.
     *
     * <p>False for almost everything, and docs/simulation.md says why: a materialised resident
     * farms, eats, fights and dies for real, so running the formula as well counts it twice -
     * and the halves do not even agree, because production skips embodied residents while
     * village-wide consumption counts everyone, which drains the stores for nothing.
     *
     * <p>That argument is about per-resident accounting, and not every module does any. Noticing
     * that a settlement has no wall double-counts nothing: there is no vanilla behaviour it
     * could be counted twice against. Blanket-skipping every module while a player stands in
     * the village left those modules unable to run at all - a settlement could only decide it
     * wanted a wall while nobody was there to see it, and only freeze the plan while somebody
     * was.
     *
     * <p>Safe to vary per module because every module draws from its own random stream, seeded
     * from its {@link #order()}. Skipping one does not shift another.
     */
    default boolean runsWhileEmbodied() {
        return false;
    }

    /** For {@code /placitum debug sim}. */
    String name();
}
