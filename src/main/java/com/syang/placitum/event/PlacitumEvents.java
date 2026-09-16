package com.syang.placitum.event;

import com.syang.placitum.Placitum;
import com.syang.placitum.build.SettlementTick;
import com.syang.placitum.command.PlacitumCommand;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.data.SettlementId;
import com.syang.placitum.store.SettlementManager;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The server-side hooks, which are now almost nothing.
 *
 * <p>There used to be handlers here for villager death, breeding, conversion, arrival and
 * profession changes, because the mod kept its own copy of every villager. It does not any more,
 * so vanilla is left to run its own villagers and this is a tick loop and a command registration.
 */
@EventBusSubscriber(modid = Placitum.MODID)
public final class PlacitumEvents {

    private PlacitumEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        PlacitumCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    /**
     * Builds, for every settlement whose ground is loaded.
     *
     * <p>Loaded is the whole condition. There is no virtual half to fall back on, so a village
     * nobody visits does not grow - which is the trade that removes the entire LOD boundary and
     * the six bugs that lived on it.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        SettlementManager manager = SettlementManager.peek();
        if (manager == null) {
            return;
        }
        for (SettlementId entry : manager.listed()) {
            ServerLevel level = event.getServer().getLevel(entry.dimension());
            if (level == null || !level.isLoaded(entry.center())) {
                continue;
            }
            Settlement settlement = manager.find(entry.id()).orElse(null);
            if (settlement == null) {
                continue;
            }
            Settlement after = SettlementTick.run(level, settlement);
            if (after != settlement) {
                manager.put(after);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        SettlementManager.get(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SettlementManager.clear();
    }
}
