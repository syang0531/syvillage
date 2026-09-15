package com.syang.placitum.defense;

import com.syang.placitum.Placitum;
import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.AlertState;
import com.syang.placitum.data.DefenseState;
import com.syang.placitum.data.Settlement;

/**
 * The settlement's alarm, which every resident obeys.
 *
 * <p>Left to decide individually, half the villagers would fight and half would run. A village
 * that panics in two directions dies in both. So the state lives on the settlement and the
 * residents follow it.
 *
 * <p>Stepping back down happens one stage at a time and only after {@code alertCooldownTicks}
 * of quiet, so a village does not return to work while the last skeleton is still walking home.
 */
public final class AlertMachine {

    private AlertMachine() {}

    /** Raises the alarm to at least the given level. Never lowers it. */
    public static Settlement raise(Settlement settlement, AlertState to, long now, String reason) {
        AlertState current = settlement.alert();
        if (to.ordinal() <= current.ordinal()) {
            // Already here or worse, but the clock restarts - the threat is still present.
            return settlement.withDefense(touch(settlement.defense(), now));
        }
        Placitum.LOGGER.info("ALERT {} -> {} in '{}': {}", current, to, settlement.name(), reason);
        return settlement.withDefense(withAlert(settlement.defense(), to, now));
    }

    /**
     * Steps the alarm down one stage if it has been quiet long enough.
     *
     * @param threatVisible whether anything hostile is currently in sight
     */
    public static Settlement relax(Settlement settlement, boolean threatVisible, long now) {
        DefenseState defense = settlement.defense();
        if (threatVisible) {
            return settlement.withDefense(touch(defense, now));
        }
        if (defense.alert() == AlertState.PEACE) {
            return settlement;
        }
        if (now - defense.alertSince() < PlacitumConfig.ALERT_COOLDOWN_TICKS.get()) {
            return settlement;
        }
        AlertState next = AlertState.values()[defense.alert().ordinal() - 1];
        Placitum.LOGGER.info("ALERT {} -> {} in '{}': quiet for {} tick(s)", defense.alert(), next,
                settlement.name(), PlacitumConfig.ALERT_COOLDOWN_TICKS.get());
        return settlement.withDefense(withAlert(defense, next, now));
    }

    /**
     * Winds the alarm down while the settlement is virtual.
     *
     * <p>With no entities there is nothing in sight by definition, so the cooldown simply runs.
     * Leaving this out was a quiet disaster: an alerted settlement that the player then walked
     * away from stayed at ALERT for ever, and since raids are suppressed during an alarm, the
     * one village that had been warned was the one village that could never be raided again.
     */
    public static DefenseState relaxed(DefenseState defense, long now) {
        if (defense.alert() == AlertState.PEACE) {
            return defense;
        }
        if (now - defense.alertSince() < PlacitumConfig.ALERT_COOLDOWN_TICKS.get()) {
            return defense;
        }
        AlertState next = AlertState.values()[defense.alert().ordinal() - 1];
        return withAlert(defense, next, now);
    }

    private static DefenseState withAlert(DefenseState defense, AlertState alert, long now) {
        return new DefenseState(alert, now, defense.wall(), defense.lightingScore(),
                defense.recentCasualties(), defense.famineSteps(), defense.foodWarned());
    }

    /** Restarts the cooldown without changing the stage. */
    private static DefenseState touch(DefenseState defense, long now) {
        return new DefenseState(defense.alert(), now, defense.wall(), defense.lightingScore(),
                defense.recentCasualties(), defense.famineSteps(), defense.foodWarned());
    }
}
