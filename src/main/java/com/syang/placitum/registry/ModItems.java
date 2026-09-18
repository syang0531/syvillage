package com.syang.placitum.registry;

import com.syang.placitum.Placitum;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The two items that lock the two tables.
 *
 * <p>Neither can be crafted. Each is the one ingredient of a recipe that only a villager sells -
 * the seal by a village head, the heart by a lord - so the lord's table cannot be reached
 * without a village head, and the guardian statue cannot be reached without a lord. That is the
 * whole of the mod's progression, and it runs on vanilla's own trading rather than on anything
 * we count.
 *
 * <p>Items rather than the tables themselves, for one reason: what happens when the villager
 * dies. An item can be kept in a chest. A table would mean raising another head first.
 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Placitum.MODID);

    /** Sold by the village head. The lord's table needs one. */
    public static final DeferredItem<Item> LORDS_SEAL = register("lords_seal");

    /** Sold by the lord. The guardian statue needs one, in place of the carved pumpkin. */
    public static final DeferredItem<Item> GOLEM_HEART = register("golem_heart");

    private ModItems() {}

    private static DeferredItem<Item> register(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(Placitum.MODID, name));
        return ITEMS.register(name, () -> new Item(new Item.Properties().setId(key)));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
