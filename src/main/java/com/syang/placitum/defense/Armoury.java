package com.syang.placitum.defense;

import com.syang.placitum.data.GearSet;
import com.syang.placitum.store.SettlementMut;
import java.util.Optional;
import net.minecraft.world.item.Item;

/**
 * Handing weapons out of the settlement stock, and taking them back.
 *
 * <p>The stock is already a map of numbers, so this is arithmetic rather than inventory
 * plumbing. What matters is that it can fail: a settlement with nothing to fight with musters
 * nobody, and the player is told why.
 */
public final class Armoury {

    private Armoury() {}

    /** Takes the best weapon on the rack, or nothing if the rack is empty. */
    public static Optional<GearSet> draw(SettlementMut settlement) {
        for (Item weapon : GearTier.weapons()) {
            if (settlement.takeStock(weapon, 1) == 1) {
                return Optional.of(new GearSet(Optional.of(weapon), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(), 0));
            }
        }
        return Optional.empty();
    }

    /** Puts a survivor's weapon back. The fallen simply never reach this. */
    public static void returnGear(SettlementMut settlement, GearSet gear) {
        gear.weapon().ifPresent(weapon -> settlement.addStock(weapon, 1));
    }

    /**
     * The tier the settlement can arm its militia to.
     *
     * <p>Counted without taking anything, for {@code defenseRating} and for the raid formula,
     * both of which need to know the settlement's strength without changing it.
     */
    public static int bestAvailableTier(com.syang.placitum.data.Settlement settlement) {
        for (Item weapon : GearTier.weapons()) {
            if (settlement.stockOf(weapon) > 0) {
                return GearTier.tierOf(weapon);
            }
        }
        return 0;
    }

    /** How many can be armed at once - three iron swords means three armed villagers. */
    public static int armableCount(com.syang.placitum.data.Settlement settlement) {
        int n = 0;
        for (Item weapon : GearTier.weapons()) {
            n += settlement.stockOf(weapon);
        }
        return n;
    }
}
