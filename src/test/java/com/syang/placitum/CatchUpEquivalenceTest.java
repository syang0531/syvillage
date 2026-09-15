package com.syang.placitum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.placitum.data.Settlement;
import com.syang.placitum.sim.SimParams;
import com.syang.placitum.sim.Simulation;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test 2 of docs/testing.md - catch-up equivalence.
 *
 * <p>One catch-up of 1000 ticks must equal ten of 100. If it does, the settling work can be
 * spread over as many ticks as the budget requires. If it does not, a village's history
 * depends on server TPS, and two players on different hardware get different worlds.
 */
class CatchUpEquivalenceTest {

    private static final SimParams PARAMS = SimParams.defaults();
    private static RegistryOps<Tag> ops;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
                .createSerializationContext(NbtOps.INSTANCE);
    }

    private static Tag encode(Settlement settlement) {
        return Settlement.CODEC.encodeStart(ops, settlement).getOrThrow();
    }

    @Test
    @DisplayName("an embodied settlement still advances its clock")
    void embodiedTimeStillPasses() {
        Settlement before = SettlementFixture.full(4, com.syang.placitum.data.ResidentState.MATERIALIZED);
        Settlement after = Simulation.catchUp(SettlementFixture.SEED, before, PARAMS,
                SettlementFixture.START_TICK + 2000);

        assertEquals(SettlementFixture.START_TICK + 2000, after.lastSimTick(),
                "time passes in a village somebody is standing in");
        assertTrue(after.simStep() > before.simStep());
    }

    @Test
    @DisplayName("embodied residents are not farmed and eaten twice over")
    void embodiedSkipsTheAccountingModules() {
        Settlement before = SettlementFixture.full(4, com.syang.placitum.data.ResidentState.MATERIALIZED);
        Settlement after = Simulation.catchUp(SettlementFixture.SEED, before, PARAMS,
                SettlementFixture.START_TICK + 4000);

        assertEquals(before.stockOf(net.minecraft.world.item.Items.WHEAT),
                after.stockOf(net.minecraft.world.item.Items.WHEAT),
                "production skips embodied residents while consumption counts everyone, so"
                        + " running either would drain the stores for nothing");
        assertEquals(before.population(), after.population(),
                "they live and die as entities; the formula must not bury them as well");
    }

    @Test
    @DisplayName("but a module with nothing to double-count still runs")
    void embodiedStillNoticesWhatItNeeds() {
        // The blanket guard meant a settlement could only decide it wanted a wall while nobody
        // was there to see it, and only act on that while somebody was.
        Settlement before = SettlementFixture.full(6, com.syang.placitum.data.ResidentState.MATERIALIZED);
        assertTrue(before.buildQueue().size() <= 1);

        Settlement after = Simulation.catchUp(SettlementFixture.SEED, before, PARAMS,
                SettlementFixture.START_TICK + 4000);

        assertFalse(after.buildQueue().isEmpty(),
                "needs must be noticed with a player standing in the village");
    }

    @Test
    @DisplayName("1000 ticks at once == 100 ticks ten times")
    void catchUpIsSliceIndependent() {
        Settlement whole = Simulation.catchUp(SettlementFixture.SEED, SettlementFixture.standard(),
                PARAMS, SettlementFixture.START_TICK + 1000);

        Settlement sliced = SettlementFixture.standard();
        long t = SettlementFixture.START_TICK;
        for (int i = 0; i < 10; i++) {
            t += 100;
            sliced = Simulation.catchUp(SettlementFixture.SEED, sliced, PARAMS, t);
        }

        assertEquals(encode(whole), encode(sliced));
        assertEquals(whole.simStep(), sliced.simStep());
    }

    @Test
    @DisplayName("the remainder is carried, not discarded")
    void remainderIsCarried() {
        // Each call is under one step, so on its own it does nothing - but ten of them add up.
        Settlement s = SettlementFixture.standard();
        long t = SettlementFixture.START_TICK;
        for (int i = 0; i < 10; i++) {
            t += 100;
            s = Simulation.catchUp(SettlementFixture.SEED, s, PARAMS, t);
        }
        assertEquals(SettlementFixture.standard().simStep() + 5, s.simStep(),
                "1000 ticks is 5 steps; dropping the remainder would leave 0");
        assertEquals(SettlementFixture.START_TICK + 1000, s.lastSimTick(),
                "lastSimTick is the boundary of the last completed step");
    }

    @Test
    @DisplayName("a partial step advances nothing at all")
    void partialStepIsANoOp() {
        Settlement before = SettlementFixture.standard();
        Settlement after = Simulation.catchUp(SettlementFixture.SEED, before, PARAMS,
                SettlementFixture.START_TICK + 199);
        assertEquals(encode(before), encode(after));
    }

    @Test
    @DisplayName("the same seed and step give the same stream; a different settlement does not")
    void randomStreamsAreSeparated() {
        var a = Simulation.rngFor(SettlementFixture.SEED, SettlementFixture.id(0), 7);
        var b = Simulation.rngFor(SettlementFixture.SEED, SettlementFixture.id(0), 7);
        assertEquals(a.nextLong(), b.nextLong());

        var other = Simulation.rngFor(SettlementFixture.SEED, SettlementFixture.id(1), 7);
        var shifted = Simulation.rngFor(SettlementFixture.SEED, SettlementFixture.id(0), 9);
        long base = Simulation.rngFor(SettlementFixture.SEED, SettlementFixture.id(0), 7).nextLong();
        // XOR-combined seeds collide exactly here: neighbouring settlements and neighbouring
        // steps land on the same stream.
        assertNotEquals(base, other.nextLong());
        assertNotEquals(base, shifted.nextLong());
    }

    @Test
    @DisplayName("food is actually produced and eaten, so the stock is not decoration")
    void foodMoves() {
        Settlement before = SettlementFixture.standard();
        Settlement after = Simulation.catchUp(SettlementFixture.SEED, before, PARAMS,
                SettlementFixture.START_TICK + 2000);

        assertNotEquals(before.stockOf(net.minecraft.world.item.Items.WHEAT),
                after.stockOf(net.minecraft.world.item.Items.WHEAT),
                "ten steps of production and consumption should move the wheat count");
        assertTrue(after.simStep() > before.simStep());
    }

    @Test
    @DisplayName("an absence longer than the cap is skipped, not simulated")
    void longAbsenceIsSummarised() {
        Settlement before = SettlementFixture.standard();
        long farFuture = SettlementFixture.START_TICK + 30L * 24000L;   // a month away

        Settlement after = Simulation.catchUp(SettlementFixture.SEED, before, PARAMS, farFuture);

        assertEquals(farFuture, after.lastSimTick(), "the clock still catches up to now");
        long simulatedSteps = after.simStep() - before.simStep();
        assertTrue(simulatedSteps <= PARAMS.maxCatchupTicks() / PARAMS.stepTicks(),
                "no more than maxCatchupTicks may actually be simulated");
        assertTrue(after.chronicle().entries().size() > before.chronicle().entries().size(),
                "the skipped span should leave a summary behind");
    }
}
