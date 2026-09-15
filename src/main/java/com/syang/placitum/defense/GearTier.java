package com.syang.placitum.defense;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * What the settlement's armoury can actually field.
 *
 * <p>Gear is a real cost, not a flag. The best weapon in stock sets the tier, one villager
 * takes one weapon, and anything carried by the fallen is gone from the stock for good. That
 * turns "my village keeps getting raided" into something a player can act on by donating three
 * iron swords - which is the opposite of the helplessness the mod exists to fix.
 */
public final class GearTier {

    /** Best first. A settlement arms its militia from the top of this list down. */
    private static final List<Item> WEAPONS = List.of(
            Items.NETHERITE_SWORD,
            Items.DIAMOND_SWORD,
            Items.IRON_SWORD,
            Items.STONE_SWORD,
            Items.IRON_AXE,
            Items.STONE_AXE,
            Items.WOODEN_SWORD,
            Items.WOODEN_AXE);

    private GearTier() {}

    public static List<Item> weapons() {
        return WEAPONS;
    }

    /** 4 for netherite down to 1 for wood; 0 means nothing to fight with. */
    public static int tierOf(Item weapon) {
        if (weapon == Items.NETHERITE_SWORD) {
            return 4;
        }
        if (weapon == Items.DIAMOND_SWORD) {
            return 3;
        }
        if (weapon == Items.IRON_SWORD || weapon == Items.IRON_AXE) {
            return 2;
        }
        return 1;
    }
}
