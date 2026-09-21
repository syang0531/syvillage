package com.syang.syvillage.registry;

import com.syang.syvillage.SyVillage;
import com.syang.syvillage.item.Blueprint;
import com.syang.syvillage.item.FreemansCharter;
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
 * <p>There were two more, and both were tokens: a seal that proved you had met a village head
 * and a heart that proved you had met a lord, each the one ingredient of a recipe. They said
 * "you are allowed to have this" and nothing else, which is the kind of thing this project keeps
 * deleting. What gates a building now is that an architect sells the drawing of it - vanilla's
 * own trade levels, which we do not count.
 *
 * <p>What is left is a villager on paper, for founding the next village: {@link FreemansCharter},
 * and the drawings themselves.
 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SyVillage.MODID);



    /** Sold by the village head. Used on the ground it is a villager. */
    public static final DeferredItem<Item> FREEMANS_CHARTER = register("freemans_charter",
            FreemansCharter::new);

    /** A building, drawn. Used on a block it is the building. */
    public static final DeferredItem<Item> BLUEPRINT = register("blueprint", Blueprint::new);

    private ModItems() {}

    private static DeferredItem<Item> register(String name) {
        return register(name, Item::new);
    }

    private static DeferredItem<Item> register(String name,
            Function<Item.Properties, Item> constructor) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(SyVillage.MODID, name));
        return ITEMS.register(name, () -> constructor.apply(new Item.Properties().setId(key)));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
