package com.syang.syvillage.client;

import com.syang.syvillage.registry.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Which screen belongs to the board's menu.
 *
 * <p>Registered from the mod's constructor rather than by annotation. 26.2 took the bus
 * parameter off {@code @EventBusSubscriber}, and whether what is left reaches a mod-bus event
 * like this one is not something to guess at when being wrong means the screen is silently
 * missing and opening the table crashes.
 */
public final class DraftingScreens {

    private DraftingScreens() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterMenuScreensEvent.class,
                event -> event.register(ModMenus.DRAFTING.get(), DraftingScreen::new));
    }
}
