package com.syang.syvillage.registry;

import com.syang.syvillage.SyVillage;
import com.syang.syvillage.block.DraftingMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The one screen with a slot in it. */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, SyVillage.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<DraftingMenu>> DRAFTING =
            MENUS.register("drafting", () -> IMenuTypeExtension.create(DraftingMenu::new));

    private ModMenus() {}

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
        // Named inside the branch, not at the top of the file: a dedicated server must never
        // load a class that imports the client.
        if (FMLEnvironment.getDist().isClient()) {
            com.syang.syvillage.client.DraftingScreens.register(modBus);
        }
    }
}
