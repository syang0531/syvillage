package com.syang.placitum.sim.module;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.Assignment;
import com.syang.placitum.data.EntryType;
import com.syang.placitum.data.GearSet;
import com.syang.placitum.data.LifeStage;
import com.syang.placitum.data.Lineage;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.ResidentState;
import com.syang.placitum.data.ResidentTask;
import com.syang.placitum.data.Vitals;
import com.syang.placitum.population.Capacity;
import com.syang.placitum.population.Demographics;
import com.syang.placitum.settlement.NameGenerator;
import com.syang.placitum.sim.SimModule;
import com.syang.placitum.sim.SimParams;
import com.syang.placitum.store.SettlementMut;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/**
 * Births, ageing and deaths. Order 40.
 *
 * <p>After threat so a raid's casualties are already on the books before capacity is judged,
 * and before construction so a village that just lost half its people does not keep building
 * for the population it used to have.
 *
 * <p>Every death carries a cause. That is the milestone's whole argument: villagers dying is
 * not the complaint, villagers dying unaccountably is.
 */
public class PopulationModule implements SimModule {

    @Override
    public void step(SettlementMut settlement, SimParams params, RandomSource rng) {
        Capacity capacity = Capacity.of(settlement.freezeView(), params);

        age(settlement, params);
        starve(settlement, params, rng);
        die(settlement, params, rng);
        birth(settlement, capacity, params, rng);
    }

    /**
     * One step of ageing, and the transitions that follow from it.
     *
     * <p>A step is ten seconds, so a game day is 120 steps. Ageing by a day per 120 steps keeps
     * resident ages on the same scale as everything else the player sees.
     */
    private void age(SettlementMut settlement, SimParams params) {
        if (!params.agingEnabled()) {
            return;
        }
        int stepsPerDay = params.stepsPerDay();
        if (settlement.simStep() % stepsPerDay != 0) {
            return;   // one day of age per game day
        }
        for (int i = 0; i < settlement.residents.size(); i++) {
            Resident r = settlement.residents.get(i);
            int age = r.ageDays() + 1;
            LifeStage stage = stageFor(age, params);
            if (stage != r.stage()) {
                settlement.record(EntryType.BIRTH, r.lineage().fullName(),
                        "grew from " + r.stage() + " to " + stage);
            }
            settlement.residents.set(i, withAge(r, age, stage));
        }
    }

    private static LifeStage stageFor(int ageDays, SimParams params) {
        if (ageDays < params.infantDays()) {
            return LifeStage.INFANT;
        }
        if (ageDays < params.childDays()) {
            return LifeStage.CHILD;
        }
        return ageDays >= params.elderThresholdDays() ? LifeStage.ELDER : LifeStage.ADULT;
    }

    /**
     * Hunger, morale and eventually starvation.
     *
     * <p>{@code hunger} is already moved by consumption; this reads it. The grace period is what
     * gives the warning time to matter - a settlement that empties its granary has a day and a
     * half before anyone dies, which is long enough for a player to do something about it.
     */
    private void starve(SettlementMut settlement, SimParams params, RandomSource rng) {
        boolean starving = settlement.stockOf(net.minecraft.world.item.Items.WHEAT) <= 0
                && settlement.population() > 0;
        if (!starving) {
            settlement.famineSteps = 0;
            return;
        }
        settlement.famineSteps++;

        for (int i = 0; i < settlement.residents.size(); i++) {
            Resident r = settlement.residents.get(i);
            Vitals v = r.vitals();
            settlement.residents.set(i,
                    r.withVitals(v.withMorale(v.morale() - params.famineMoralePenalty())));
        }
        if (settlement.famineSteps == params.famineGraceSteps()) {
            settlement.record(EntryType.FAMINE, settlement.identity.name(),
                    "The stores are empty and people are starting to go hungry.");
            Placitum.LOGGER.info("FAMINE in '{}': stores empty for {} step(s)",
                    settlement.identity.name(), settlement.famineSteps);
        }
    }

    /** Old age and starvation. Combat deaths are the threat module's business. */
    private void die(SettlementMut settlement, SimParams params, RandomSource rng) {
        boolean pastGrace = settlement.famineSteps > params.famineGraceSteps();

        List<Resident> dead = new ArrayList<>();
        for (Resident r : settlement.residents) {
            if (!r.counts()) {
                continue;
            }
            if (pastGrace && rng.nextDouble() < params.famineDeathChancePerStep()) {
                dead.add(r);
                settlement.record(EntryType.DEATH, r.lineage().fullName(), "starved");
                continue;
            }
            if (params.agingEnabled() && r.stage() == LifeStage.ELDER
                    && rng.nextDouble() < params.elderDeathChancePerStep()) {
                dead.add(r);
                settlement.record(EntryType.DEATH, r.lineage().fullName(),
                        "died of old age at " + r.ageDays() + " days");
            }
        }
        settlement.residents.removeAll(dead);
    }

    /**
     * A birth, at most one per step.
     *
     * <p>Infants exist only as records - never spawned. A vanilla baby villager wandering out of
     * the village and dying is one of the commonest ways a settlement quietly empties, and the
     * cheapest fix is for it not to have a body until it can look after itself.
     */
    private void birth(SettlementMut settlement, Capacity capacity, SimParams params,
            RandomSource rng) {
        double chance = Demographics.birthChance(settlement, capacity, params);
        if (chance <= 0.0 || rng.nextDouble() >= chance) {
            return;
        }
        Optional<Resident> mother = pickParent(settlement, params, rng, null);
        if (mother.isEmpty()) {
            return;
        }
        Optional<Resident> father = pickParent(settlement, params, rng, mother.get().id());
        if (father.isEmpty()) {
            return;
        }

        Set<String> taken = new HashSet<>();
        for (Resident r : settlement.residents) {
            taken.add(r.lineage().fullName());
        }
        Lineage base = NameGenerator.founder(rng, taken);
        // The family name comes down the father's line. Villagers nobody can tell apart are
        // villagers nobody minds losing; a surname that persists is most of what makes a
        // settlement feel like it has families in it rather than units.
        Lineage lineage = new Lineage(base.givenName(), father.get().lineage().familyName(),
                Optional.of(mother.get().id()), Optional.of(father.get().id()));

        Resident child = new Resident(
                UUID.randomUUID(),
                lineage,
                LifeStage.INFANT,
                0,
                Assignment.unassigned(),
                Vitals.HEALTHY,
                false,
                false,
                GearSet.EMPTY,
                ResidentTask.IDLE,
                mother.get().coarsePos(),
                ResidentState.VIRTUAL,
                new CompoundTag());

        settlement.residents.add(child);
        settlement.record(EntryType.BIRTH, lineage.fullName(),
                "born to " + mother.get().lineage().fullName()
                        + " and " + father.get().lineage().fullName());
        Placitum.LOGGER.debug("  step {}: {} born ({} of capacity {})", settlement.simStep(),
                lineage.fullName(), settlement.population(), capacity.value());
    }

    private Optional<Resident> pickParent(SettlementMut settlement, SimParams params,
            RandomSource rng, UUID exclude) {
        List<Resident> candidates = new ArrayList<>();
        for (Resident r : settlement.residents) {
            if (r.stage() == LifeStage.ADULT && r.counts()
                    && r.ageDays() < params.elderThresholdDays()
                    && !r.id().equals(exclude)) {
                candidates.add(r);
            }
        }
        return candidates.isEmpty()
                ? Optional.empty()
                : Optional.of(candidates.get(rng.nextInt(candidates.size())));
    }

    private static Resident withAge(Resident r, int ageDays, LifeStage stage) {
        return new Resident(r.id(), r.lineage(), stage, ageDays, r.assignment(), r.vitals(),
                r.militiaEligible(), r.zombified(), r.gear(), r.task(), r.coarsePos(), r.state(),
                r.vanillaState());
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public String name() {
        return "population";
    }
}
