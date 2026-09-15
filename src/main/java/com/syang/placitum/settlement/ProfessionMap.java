package com.syang.placitum.settlement;

import com.syang.placitum.data.Assignment;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/**
 * Vanilla professions collapsed onto the six jobs the simulation knows.
 *
 * <p>Carrying all thirteen over would mean a production curve and a balance pass each. Trading
 * still runs off the vanilla profession - this mapping is simulation-only, so a librarian keeps
 * selling books whatever we decide to call them.
 */
public final class ProfessionMap {

    private ProfessionMap() {}

    public static Identifier of(Villager villager) {
        return of(villager.getVillagerData().profession());
    }

    public static Identifier of(Holder<VillagerProfession> profession) {
        if (profession.is(VillagerProfession.FARMER)
                || profession.is(VillagerProfession.FISHERMAN)
                || profession.is(VillagerProfession.SHEPHERD)
                || profession.is(VillagerProfession.BUTCHER)) {
            return Assignment.FARMER;
        }
        if (profession.is(VillagerProfession.TOOLSMITH)
                || profession.is(VillagerProfession.WEAPONSMITH)
                || profession.is(VillagerProfession.ARMORER)) {
            return Assignment.SMITH;
        }
        if (profession.is(VillagerProfession.MASON)
                || profession.is(VillagerProfession.CARTOGRAPHER)) {
            return Assignment.BUILDER;
        }
        if (profession.is(VillagerProfession.FLETCHER)
                || profession.is(VillagerProfession.LEATHERWORKER)) {
            return Assignment.WOODCUTTER;
        }
        if (profession.is(VillagerProfession.LIBRARIAN)
                || profession.is(VillagerProfession.CLERIC)) {
            return Assignment.SCHOLAR;
        }
        return Assignment.NONE;
    }

    /** Scholars do not fight. That trade-off is the point of the flag. */
    public static boolean militiaEligible(Identifier job) {
        return !job.equals(Assignment.SCHOLAR);
    }

    /** For diagnostics: what vanilla actually thinks this villager is. */
    public static String vanillaName(Villager villager) {
        return villager.getVillagerData().profession().getRegisteredName();
    }
}
