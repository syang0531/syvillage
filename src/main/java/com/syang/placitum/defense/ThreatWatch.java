package com.syang.placitum.defense;

import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.AlertState;
import com.syang.placitum.data.Settlement;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

/**
 * Looking for trouble, from watch points only.
 *
 * <p>Putting one box over the whole claim makes detection cost grow with settlement size, which
 * is exactly backwards - the bigger the village, the more it can least afford it. Scanning from
 * a bounded number of watch points ties cost to that number instead.
 *
 * <p>It also gives watchtowers a job. A settlement with nowhere to watch from does not see
 * anything until it is already inside, and that is a fair thing to lose for not building one.
 */
public final class ThreatWatch {

    /** What one scan found. */
    public record Sighting(int hostiles, boolean inside, BlockPos nearest) {
        public boolean any() {
            return hostiles > 0;
        }
    }

    public static final Sighting NOTHING = new Sighting(0, false, BlockPos.ZERO);

    private ThreatWatch() {}

    public static Sighting scan(ServerLevel level, Settlement settlement) {
        int radius = PlacitumConfig.WATCH_RADIUS.get();
        int claim = settlement.identity().claimRadiusChunks() * 16;
        BlockPos centre = settlement.center();

        Set<Integer> counted = new HashSet<>();
        int hostiles = 0;
        boolean inside = false;
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (BlockPos point : settlement.anchors().watchPoints()) {
            AABB box = new AABB(point).inflate(radius);
            for (Monster monster : level.getEntitiesOfClass(Monster.class, box, Monster::isAlive)) {
                // Watch circles overlap; without this a mob standing between two of them counts
                // twice and the settlement panics at half the real threat.
                if (!counted.add(monster.getId())) {
                    continue;
                }
                hostiles++;
                double dist = monster.blockPosition().distSqr(centre);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = monster.blockPosition();
                }
                if (dist <= (double) claim * claim) {
                    inside = true;
                }
            }
        }
        return hostiles == 0 ? NOTHING : new Sighting(hostiles, inside, nearest);
    }

    /** What the sighting means for the alarm. */
    public static AlertState levelFor(Sighting sighting) {
        if (!sighting.any()) {
            return AlertState.PEACE;
        }
        return sighting.inside() ? AlertState.COMBAT : AlertState.ALERT;
    }
}
