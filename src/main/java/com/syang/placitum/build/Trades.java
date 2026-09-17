package com.syang.placitum.build;

import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.Craft;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.registry.ModVillagers;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.phys.AABB;

/**
 * What the villagers of a settlement can do, asked of vanilla.
 *
 * <p>Professions are decided by workstations and vanilla decides them, so this reads an answer
 * rather than keeping one. The old design had a labour module that assigned jobs, retrained
 * people out of them and argued with vanilla about who was a farmer; it is gone, and this is the
 * two lines that replaced it.
 */
public final class Trades {

    private Trades() {}

    /**
     * The standard the village's trades entitle it to.
     *
     * <p>A mason means the village builds in stone; a village head means stone brick. It is not
     * the workstation that does it but somebody taking the job at one - which needs a villager
     * spare, which means there are more villagers than jobs, which is the thing that actually
     * says a village is getting on.
     *
     * <p>The best trade in the claim wins, so a head does not have to wait for a mason.
     *
     * <p>Only the entitlement. Whether the settlement uses it is
     * {@link Settlement#craft()}, which never goes down.
     */
    public static Craft earned(ServerLevel level, Settlement settlement) {
        int reach = PlacitumConfig.CLAIM_RADIUS_CHUNKS.get() * 16;
        AABB box = new AABB(settlement.center()).inflate(reach, 32, reach);
        Craft best = Craft.TIMBER;
        for (Villager villager : level.getEntitiesOfClass(Villager.class, box)) {
            best = best.or(of(villager.getVillagerData().profession()));
        }
        return best;
    }

    /** What one villager's trade is worth to the town. */
    private static Craft of(Holder<VillagerProfession> profession) {
        if (ModVillagers.isVillageHead(profession)) {
            return Craft.MASONRY;
        }
        return profession.is(VillagerProfession.MASON) ? Craft.STONE : Craft.TIMBER;
    }
}
