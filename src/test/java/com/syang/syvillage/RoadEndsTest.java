package com.syang.syvillage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.syvillage.build.Reach;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where a street stops.
 *
 * <p>A street that climbs two blocks up a hill and gives up on the third is a ramp to nowhere.
 * The walk cannot tell while it is walking - it finds out by failing to get any further - so the
 * ramps are pruned afterwards, and this is the pruning.
 */
class RoadEndsTest {

    private static long at(int x, int z) {
        return Reach.key(x, z);
    }

    /** A chain of columns, each reached from the one before it. */
    private static Map<Long, Long> chain(long... columns) {
        Map<Long, Long> cameFrom = new LinkedHashMap<>();
        for (int i = 1; i < columns.length; i++) {
            cameFrom.put(columns[i], columns[i - 1]);
        }
        return cameFrom;
    }

    @Test
    @DisplayName("a climb that gets back to level ground is a street")
    void aClimbThatArrivesIsKept() {
        long bell = at(0, 0);
        long up1 = at(0, 1);
        long up2 = at(0, 2);
        long flat = at(0, 3);
        Set<Long> seen = new LinkedHashSet<>(Set.of(bell, up1, up2, flat));

        Set<Long> kept = Reach.leadingSomewhere(seen, chain(bell, up1, up2, flat),
                Set.of(bell, flat));

        assertEquals(seen, kept,
                "the whole street is kept: it climbs a bank and carries on along the top");
    }

    @Test
    @DisplayName("a climb that stops on the hill is not a street")
    void aClimbThatArrivesNowhereIsDropped() {
        long bell = at(0, 0);
        long up1 = at(0, 1);
        long up2 = at(0, 2);
        Set<Long> seen = new LinkedHashSet<>(Set.of(bell, up1, up2));

        Set<Long> kept = Reach.leadingSomewhere(seen, chain(bell, up1, up2), Set.of(bell));

        assertEquals(Set.of(bell), kept,
                "two blocks of ramp up a hillside, ending in the hillside, is not a road");
    }

    @Test
    @DisplayName("one branch arriving does not save the branch that does not")
    void onlyTheBranchThatArrivesSurvives() {
        // The case that decides whether this is worth having: a junction where the street goes
        // on one way and peters out the other. Keeping the shared part and dropping only the
        // dead end is the whole job.
        long bell = at(0, 0);
        long fork = at(0, 1);
        long onwards = at(1, 1);
        long deadEnd = at(-1, 1);

        Map<Long, Long> cameFrom = new LinkedHashMap<>();
        cameFrom.put(fork, bell);
        cameFrom.put(onwards, fork);
        cameFrom.put(deadEnd, fork);
        Set<Long> seen = new LinkedHashSet<>(Set.of(bell, fork, onwards, deadEnd));

        Set<Long> kept = Reach.leadingSomewhere(seen, cameFrom, Set.of(bell, onwards));

        assertTrue(kept.contains(bell) && kept.contains(fork) && kept.contains(onwards));
        assertFalse(kept.contains(deadEnd), "the ramp that goes nowhere was paved anyway");
        assertEquals(3, kept.size());
    }

    @Test
    @DisplayName("level ground is never pruned, however it was reached")
    void levelGroundAlwaysSurvives() {
        long bell = at(0, 0);
        long flat = at(0, 1);
        Set<Long> kept = Reach.leadingSomewhere(new LinkedHashSet<>(Set.of(bell, flat)),
                chain(bell, flat), Set.of(bell, flat));
        assertEquals(2, kept.size());
    }
}
