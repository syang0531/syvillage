package com.syang.placitum.registry;

import com.syang.placitum.Placitum;
import com.syang.placitum.item.FreemansCharter;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The items only a villager sells.
 *
 * <p>Two lock the two tables. Neither can be crafted: each is the one ingredient of a recipe
 * that only a villager sells - the seal by a village head, the heart by a lord - so the lord's
 * table cannot be reached without a village head, and the guardian statue cannot be reached
 * without a lord. That is the whole of the mod's progression, and it runs on vanilla's own
 * trading rather than on anything we count.
 *
 * <p>Items rather than the tables themselves, for one reason: what happens when the villager
 * dies. An item can be kept in a chest. A table would mean raising another head first.
 *
 * <p>The third is a villager on paper, for founding the next village: {@link FreemansCharter}.
 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Placitum.MODID);

    /** Sold by the village head. The lord's table needs one. */
    public static final DeferredItem<Item> LORDS_SEAL = register("lords_seal");

    /** Sold by the lord. The guardian statue needs one, in place of the carved pumpkin. */
    public static final DeferredItem<Item> GOLEM_HEART = register("golem_heart");

    /** Sold by the village head. Used on the ground it is a villager. */
    public static final DeferredItem<Item> FREEMANS_CHARTER = register("freemans_charter",
            FreemansCharter::new);

    private ModItems() {}

    private static DeferredItem<Item> register(String name) {
        return register(name, Item::new);
    }

    private static DeferredItem<Item> register(String name,
            Function<Item.Properties, Item> constructor) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(Placitum.MODID, name));
        return ITEMS.register(name, () -> constructor.apply(new Item.Properties().setId(key)));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
