package com.syang.placitum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.placitum.data.Assignment;
import com.syang.placitum.data.ChronicleEntry;
import com.syang.placitum.data.EntryType;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.population.Capacity;
import com.syang.placitum.sim.SimParams;
import com.syang.placitum.sim.module.LabourModule;
import com.syang.placitum.sim.Simulation;
import java.util.List;
import java.util.Map;
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
    @DisplayName("a settlement too small to defend itself is not raided")
    void smallSettlementsAreLeftAlone() {
        // Measured: a village of three with a defence rating of 8 lost five residents to nine
        // raids and could not replace one of them. A raid is meant to be a reason to build a
        // wall, not a countdown on a village too small to build one - and nothing in vanilla
        // sends a pillager band after two villagers either.
        SimParams params = SimParams.defaults();
        assertTrue(params.raidMinPopulation() > 2,
                "the smallest legal settlement is two residents and it must survive being one");

        Settlement tiny = SettlementFixture.adopted(2, 10, Map.of())
                .withDefense(SettlementFixture.standard().defense()
                        .withWall(com.syang.placitum.data.WallState.NONE));
        Settlement after = run(tiny, 2000);

        assertTrue(after.chronicle().entries().stream()
                        .noneMatch(e -> e.type() == com.syang.placitum.data.EntryType.RAID_LOST
                                || e.type() == com.syang.placitum.data.EntryType.RAID_REPELLED),
                "something raided a village of two");
    }

    @Test
    @DisplayName("a village outlives its own founders")
    void theVillageOutlivesItsFounders() {
        // Measured in game: eleven residents became two over 295 game days, with seven houses
        // built and food piled to 130,000. Every adopted adult started at exactly 25, so the
        // whole village crossed into old age together and by then was too old to replace
        // itself. A settlement should not have a date of death.
        SimParams params = SimParams.defaults();
        Settlement village = SettlementFixture.adopted(8, 40);
        int founders = village.population();

        // Two hundred game days - twice the lifespan of anyone alive at the start.
        Settlement after = run(village, 200 * params.stepsPerDay());

        assertTrue(after.population() > 0,
                "the village died out: " + founders + " founders, nobody left after 200 days");
        assertTrue(after.residents().stream()
                        .anyMatch(r -> r.ageDays() < params.elderThresholdDays()),
                "everyone left is past child-bearing age, so this village is finished even"
                        + " though it is not empty yet");
    }

    @Test
    @DisplayName("building a house never makes the village smaller")
    void aNewPlotCannotLowerCapacity() {
        // Preferring plots over anchors meant the first cottage erased the beds the village was
        // adopted with - five became two, capacity fell, and the settlement answered by
        // building another house. The loop ran backwards.
        Settlement adopted = SettlementFixture.adopted(6, 5, Map.of());
        int before = adopted.bedCount();
        assertEquals(5, before, "premise: this village came with five beds");

        Settlement withCottage = adopted.withPlots(Map.of(SettlementFixture.id(500),
                new com.syang.placitum.data.Plot(SettlementFixture.id(500),
                        new com.syang.placitum.data.CellPos(1, 0), 1, 1,
                        net.minecraft.world.level.block.Rotation.NONE,
                        com.syang.placitum.build.HousePlanner.COTTAGE,
                        com.syang.placitum.data.PlotKind.HOUSE, 2, List.of())));

        assertTrue(withCottage.bedCount() >= before,
                "a house with two beds cannot leave a five-bed village with two: "
                        + withCottage.bedCount());
    }

    @Test
    @DisplayName("idle residents are put to the work the settlement is short of")
    void idleResidentsAreEmployed() {
        // Nothing is ever a woodcutter or a builder otherwise: neither job has a vanilla
        // profession behind it, so adoption can never produce one, and the first real wall
        // wanted 1761 logs from a settlement that produced none.
        Settlement before = SettlementFixture.adopted(6, 40, Map.of());
        long idleBefore = before.residents().stream()
                .filter(r -> r.assignment().job().equals(Assignment.NONE)).count();

        Settlement after = run(before, 400);
        long idleAfter = after.residents().stream()
                .filter(r -> r.assignment().job().equals(Assignment.NONE)).count();

        assertTrue(idleAfter < idleBefore || idleBefore == 0,
                "unemployed residents stayed unemployed: " + idleBefore + " -> " + idleAfter);
    }

    @Test
    @DisplayName("somebody retrains when nobody at all does the job that is needed")
    void aVillageOfFarmersCanStillCutTimber() {
        // Two residents, both made farmers while food was short, a house waiting on 111 logs and
        // nobody able to cut one. Four thousand wheat in store and the settlement could not
        // build anything, so beds stayed at two, capacity stayed at two, no child was ever born,
        // and the first death of old age finished it.
        SimParams params = SimParams.defaults();
        Settlement farmersOnly = SettlementFixture.standard()
                .withResidents(SettlementFixture.standard().residents().stream()
                        .map(r -> r.withAssignment(r.assignment().withJob(Assignment.FARMER)))
                        .toList())
                .withBuildQueue(List.of(SettlementFixture.standard().buildQueue().get(0)
                        .withStage(com.syang.placitum.data.BuildStage.WAITING_MATERIALS)));
        assertEquals(Assignment.WOODCUTTER, LabourModule.mostNeeded(farmersOnly, params),
                "premise: what this village needs is timber");

        // One step. Longer and the need has already moved on - materials arrive, the job goes
        // to EXECUTING, and what the settlement wants next is a builder.
        Settlement after = run(farmersOnly, 1);

        assertTrue(after.residents().stream()
                        .anyMatch(r -> r.assignment().job().equals(Assignment.WOODCUTTER)),
                "everyone is still a farmer, so the house will never be built");
        assertTrue(after.residents().stream()
                        .anyMatch(r -> r.assignment().job().equals(Assignment.FARMER)),
                "the last farmer was taken as well, which trades one starvation for another");
    }

    @Test
    @DisplayName("the job a settlement needs most follows what is actually short")
    void neededJobFollowsTheShortage() {
        SimParams params = SimParams.defaults();

        // No farmers at all: food is the binding constraint and nothing else matters, because
        // hunger kills and a missing wall does not.
        Settlement hungry = SettlementFixture.adopted(6, 40, Map.of())
                .withResidents(SettlementFixture.standard().residents().stream()
                        .map(r -> r.withAssignment(r.assignment()
                                .withJob(com.syang.placitum.data.Assignment.NONE)))
                        .toList());
        assertEquals(Assignment.FARMER, LabourModule.mostNeeded(hungry, params));

        // Fed, with a build waiting on timber: somebody has to go and cut it. Everyone farms,
        // so food is comfortably ahead of the population and stops being the binding limit -
        // otherwise this would pass for the reason the case above does.
        Settlement waiting = SettlementFixture.standard()
                .withResidents(SettlementFixture.standard().residents().stream()
                        .map(r -> r.withAssignment(r.assignment().withJob(Assignment.FARMER)))
                        .toList())
                .withBuildQueue(List.of(SettlementFixture.standard().buildQueue().get(0)
                        .withStage(com.syang.placitum.data.BuildStage.WAITING_MATERIALS)));
        assertNotEquals(Capacity.Bottleneck.FOOD,
                Capacity.of(waiting, params).bottleneck(), "premise: this village eats");
        assertEquals(Assignment.WOODCUTTER, LabourModule.mostNeeded(waiting, params),
                "a wall nobody is cutting timber for is a wall that never gets built");
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

    /**
     * Completion criterion 3: the warning comes first.
     *
     * <p>A village that starves with no notice is the original complaint in another costume, so
     * the ordering is the feature. Asserted as an ordering rather than as timings, because the
     * timings are config and will be tuned.
     */
    @Test
    @DisplayName("low food is announced before anybody starves")
    void warningPrecedesFamine() {
        // Strip the granary: the fixture ships 340 wheat, which is months of warning away.
        // One game day, not four hundred steps. The chronicle drops its oldest past 200
        // entries, and a long famine buries the very warning this test is looking for.
        Settlement after = run(SettlementFixture.adopted(6, 40, Map.of()), 120);

        List<ChronicleEntry> famine = after.chronicle().entries().stream()
                .filter(e -> e.type() == EntryType.FAMINE).toList();
        assertFalse(famine.isEmpty(), "an empty granary should say something");

        ChronicleEntry first = famine.get(0);
        assertTrue(first.detail().startsWith("Food is running low"),
                "the first word on food must be the warning, not the funeral: " + first.detail());

        long firstDeath = after.chronicle().entries().stream()
                .filter(e -> e.type() == EntryType.DEATH && e.detail().equals("starved"))
                .mapToLong(ChronicleEntry::gameTime).min().orElse(Long.MAX_VALUE);
        assertTrue(firstDeath != Long.MAX_VALUE,
                "nobody starved, so the ordering this asserts was never actually tested");
        assertTrue(first.gameTime() < firstDeath,
                "the warning must land before the first starvation, not alongside it");
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
