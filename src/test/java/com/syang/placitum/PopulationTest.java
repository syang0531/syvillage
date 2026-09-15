package com.syang.placitum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.placitum.data.ChronicleEntry;
import com.syang.placitum.data.EntryType;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.population.Capacity;
import com.syang.placitum.sim.SimParams;
import com.syang.placitum.sim.Simulation;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * M2 - the population half of the loop.
 *
 * <p>These exist because "no births in a hundred steps" is indistinguishable by eye from a
 * birth path that never fires at all. A per-step probability cannot be confirmed by playing;
 * it has to be run long enough for the law of large numbers to answer.
 */
class PopulationTest {

    private static final SimParams PARAMS = SimParams.defaults();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Settlement roomy(int beds) {
        return SettlementFixture.adopted(6, beds);
    }

    /**
     * Runs {@code steps} steps for real.
     *
     * <p>In chunks, because a single catch-up longer than {@code maxCatchupTicks} is summarised
     * rather than simulated - by design, and it silently turned an earlier version of this test
     * into a 360-step run wearing a 2000-step label.
     */
    private static Settlement run(Settlement s, int steps) {
        long t = s.lastSimTick();
        int perChunk = (int) (PARAMS.maxCatchupTicks() / PARAMS.stepTicks());
        for (int done = 0; done < steps; done += perChunk) {
            t += (long) Math.min(perChunk, steps - done) * PARAMS.stepTicks();
            s = Simulation.catchUp(SettlementFixture.SEED, s, PARAMS, t);
        }
        return s;
    }

    private static long count(Settlement s, EntryType type) {
        return s.chronicle().entries().stream().filter(e -> e.type() == type).count();
    }

    @Test
    @DisplayName("given room, the settlement fills up to its capacity and then stops")
    void growsToCapacityAndHolds() {
        Settlement before = roomy(40);
        int capacity = Capacity.of(before, PARAMS).value();
        assertTrue(capacity > before.population(),
                "the fixture must have headroom, or this test proves nothing");

        Settlement after = run(before, 4000);

        assertTrue(count(after, EntryType.BIRTH) > 0,
                "four thousand steps with room to grow and not one birth means the path is dead");
        assertTrue(after.population() > before.population(),
                "the roll fired but the population did not move");
        // Both halves matter. Births alone would be satisfied by a village that grows without
        // limit, which is how a server ends up with five hundred villagers in it.
        assertTrue(after.population() <= Capacity.of(after, PARAMS).value(),
                "population " + after.population() + " is over capacity; the logistic term is"
                        + " not damping");
    }

    @Test
    @DisplayName("with no beds nothing is born, and beds are named as the reason")
    void bedsBindGrowth() {
        Settlement before = roomy(0);
        Capacity capacity = Capacity.of(before, PARAMS);
        assertEquals(Capacity.Bottleneck.BEDS, capacity.bottleneck(),
                "the player is owed the reason, not just the number");

        Settlement after = run(before, 4000);
        assertEquals(0, count(after, EntryType.BIRTH),
                "a village with nowhere to sleep must not grow");
    }

    @Test
    @DisplayName("coming of age is not filed as a birth")
    void ageingIsNotBirth() {
        Settlement after = run(roomy(0), 4000);

        // roomy(0) can have no births at all, so every BIRTH entry here would be a mislabelled
        // stage change - which is exactly what this used to record.
        for (ChronicleEntry e : after.chronicle().entries()) {
            assertFalse(e.type() == EntryType.BIRTH && e.detail().startsWith("grew from"),
                    "stage changes belong to CAME_OF_AGE: " + e.detail());
        }
    }
}
