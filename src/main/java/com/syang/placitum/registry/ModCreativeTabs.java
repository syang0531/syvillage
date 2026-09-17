package com.syang.placitum.registry;

import com.syang.placitum.Placitum;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Putting the mod's blocks somewhere a player can find them.
 *
 * <p>Registering a block gets it into the game; it does not get it into the creative menu, and
 * the creative search only knows about items that are in some tab. Both workstations were
 * invisible in creative and unfindable by search, which reads as "the mod did not load" rather
 * than as a missing eight lines.
 */
public final class ModCreativeTabs {

    private ModCreativeTabs() {}

    /** Listens on the mod bus, alongside the registers, rather than by annotation. */
    public static void register(IEventBus modBus) {
        modBus.addListener(ModCreativeTabs::onBuildContents);
    }

    private static void onBuildContents(BuildCreativeModeTabContentsEvent event) {
        // Functional blocks, next to the vanilla workstations, because that is what they are:
        // a villager claims one and takes the job.
        if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) {
            event.accept(ModBlocks.VILLAGE_HEAD_TABLE.get());
            event.accept(ModBlocks.LORDS_TABLE.get());
            event.accept(ModBlocks.GUARDIAN_STATUE.get());
        }
    }
}
