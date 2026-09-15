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

    /** For {@code /placitum debug sim}. */
    String name();
}
