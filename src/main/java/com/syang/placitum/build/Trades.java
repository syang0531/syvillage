package com.syang.placitum.build;

import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.data.Stage;
import com.syang.placitum.registry.ModVillagers;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.phys.AABB;

/**
 * What the villagers of a settlement entitle it to, asked of vanilla.
 *
 * <p>Professions are decided by workstations and vanilla decides them, so this reads an answer
 * rather than keeping one. The old design had a labour module that assigned jobs, retrained
 * people out of them and argued with vanilla about who was a farmer; it is gone, and this is the
 * two lines that replaced it.
 */
public final class Trades {

    private Trades() {}

    /**
     * The stage the village's trades entitle it to.
     *
     * <p>A village head means streets and houses; a lord means a wall. It is not the workstation
     * that does it but somebody taking the job at one - which needs a villager spare, which means
     * there are more villagers than jobs, which is the thing that actually says a village is
     * getting on.
     *
     * <p>The best trade in the claim wins, so a lord does not have to wait for a head to be
     * counted. Only the entitlement: whether the settlement has it is {@link Settlement#stage()},
     * which never goes down.
     */
    public static Stage earned(ServerLevel level, Settlement settlement) {
        int reach = PlacitumConfig.CLAIM_RADIUS_CHUNKS.get() * 16;
        AABB box = new AABB(settlement.center()).inflate(reach, 32, reach);
        Stage best = Stage.LIT;
        for (Villager villager : level.getEntitiesOfClass(Villager.class, box)) {
            best = best.or(of(villager.getVillagerData().profession()));
        }
        return best;
    }

    /** What one villager's trade is worth to the town. Vanilla's own trades are worth nothing. */
    private static Stage of(Holder<VillagerProfession> profession) {
        if (ModVillagers.isLord(profession)) {
            return Stage.WALLED;
        }
        if (ModVillagers.isVillageHead(profession)) {
            return Stage.HEADED;
        }
        return Stage.LIT;
    }
}
