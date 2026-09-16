package com.syang.placitum.build;

import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.PlotKind;
import com.syang.placitum.data.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

/**
 * What the village is short of, asked of the world rather than of a model.
 *
 * <p>Villagers and beds are both things vanilla already counts, so counting them again here
 * would be two answers to one question - and the old design's entire population model existed to
 * produce the second one. Beds are POIs, villagers are entities, and both are exactly as true as
 * the world is.
 */
public final class Need {

    /** What to build next on a free lot. */
    public enum Kind { HOUSE, FARM }

    private Need() {}

    /**
     * A house if there is nowhere to sleep, a field otherwise.
     *
     * <p>Beds first because vanilla breeding needs a free bed per child and will simply not
     * happen without one. Fields second because it also needs the villagers to be carrying food,
     * which is a farmer's harvest - a village of beds and no crops has a first generation and no
     * second.
     */
    public static Kind next(ServerLevel level, Settlement settlement) {
        int villagers = villagerCount(level, settlement);
        int beds = bedCount(level, settlement);
        if (beds <= villagers) {
            return Kind.HOUSE;
        }
        // Enough beds for now. One field per three houses keeps a village fed without turning
        // the whole plan into farmland.
        int farms = countPlots(settlement, PlotKind.FARM);
        int houses = countPlots(settlement, PlotKind.HOUSE);
        return farms == 0 || houses >= farms * 3 ? Kind.FARM : Kind.HOUSE;
    }

    /**
     * How far out villagers and beds count: the whole claim, not the part built on yet.
     *
     * <p>Deliberately not the plan's reach. A new settlement reaches one cell, and counting
     * inside that would miss the villagers standing forty blocks away in the village vanilla
     * generated - so the very first decision it made would be the wrong one.
     */
    private static int claimBlocks() {
        return PlacitumConfig.CLAIM_RADIUS_CHUNKS.get() * 16;
    }

    /** Villagers standing inside the claim. Vanilla's count, not ours. */
    public static int villagerCount(ServerLevel level, Settlement settlement) {
        int reach = claimBlocks();
        BlockPos centre = settlement.center();
        AABB box = new AABB(centre).inflate(reach, 32, reach);
        return level.getEntitiesOfClass(Villager.class, box).size();
    }

    /** Beds inside the claim, as the game itself counts them. */
    public static int bedCount(ServerLevel level, Settlement settlement) {
        PoiManager poi = level.getPoiManager();
        return (int) poi.getInRange(holder -> holder.is(PoiTypes.HOME), settlement.center(),
                claimBlocks(), PoiManager.Occupancy.ANY).count();
    }

    public static int countPlots(Settlement settlement, PlotKind kind) {
        int n = 0;
        for (var plot : settlement.plots().values()) {
            if (plot.kind() == kind) {
                n++;
            }
        }
        return n;
    }
}
