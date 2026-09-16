package com.syang.placitum.defense;

import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.AlertState;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.store.SettlementManager;
import com.syang.placitum.store.SettlementMut;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

/**
 * What defence does each tick while a settlement has bodies.
 *
 * <p>Order is the alarm's: see, decide, then act. Scanning after mustering would arm the
 * militia against a threat that walked off a second ago.
 */
public final class DefenseTick {

    private DefenseTick() {}

    public static Settlement run(ServerLevel level, SettlementManager manager, Settlement settlement) {
        long now = level.getGameTime();
        Settlement out = settlement;

        if (now % PlacitumConfig.WATCH_INTERVAL_TICKS.get() == 0L) {
            ThreatWatch.Sighting sighting = ThreatWatch.scan(level, out);
            AlertState wanted = ThreatWatch.levelFor(sighting);
            if (wanted != AlertState.PEACE) {
                out = AlertMachine.raise(out, wanted, now,
                        sighting.hostiles() + " hostile(s)" + (sighting.inside() ? " inside the claim" : " nearby"));
            } else {
                out = AlertMachine.relax(out, false, now);
            }
            out = applyAlert(level, manager, out);
        }

        if (now % 20L == 0L) {
            Curfew.enforce(level, manager, out);
            DoorWatch.closeUp(level, out);
        }
        return out;
    }

    /**
     * Brings the militia in line with the alarm.
     *
     * <p>Mustering and standing down both move items in the stock, so they go through the
     * mutable mirror and come back as a settlement.
     */
    private static Settlement applyAlert(ServerLevel level, SettlementManager manager,
            Settlement settlement) {
        boolean shouldBeArmed = settlement.alert() != AlertState.PEACE;
        boolean anyArmed = settlement.residents().stream().anyMatch(r -> r.gear().armed());
        if (shouldBeArmed == anyArmed) {
            return settlement;
        }

        SettlementMut mut = SettlementMut.of(settlement);
        List<Resident> updated = shouldBeArmed
                ? Conscription.muster(level, manager, settlement, mut)
                : Conscription.standDown(level, manager, settlement, mut);
        mut.residents.clear();
        mut.residents.addAll(updated);
        return mut.freeze();
    }

    /** The bell: the player's own way of raising the alarm. */
    public static Settlement soundAlarm(Settlement settlement, ServerLevel level,
            SettlementManager manager) {
        Settlement raised = AlertMachine.raise(settlement, AlertState.ALERT, level.getGameTime(),
                "the bell was rung");
        return applyAlert(level, manager, raised);
    }
}
