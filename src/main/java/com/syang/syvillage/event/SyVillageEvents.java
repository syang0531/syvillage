package com.syang.syvillage.event;

import com.syang.syvillage.SyVillage;
import com.syang.syvillage.build.Previews;
import com.syang.syvillage.build.Raise;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The server-side hooks, which are two.
 *
 * <p>Both are flourishes and neither is a planner. CLAUDE.md forbids a tick that walks the
 * world looking for work, and the distinction is worth keeping in view: these two are bounded
 * by what a player started in the last few seconds, they read nothing from the level, they hold
 * nothing that is saved, and when they are interrupted they finish rather than resume. A
 * planning tick was none of those things, and six of the settlement pipeline's bugs lived on
 * one.
 *
 * <p>There used to be handlers here for villager death, breeding, conversion and profession
 * changes, and later for a settlement's build queue. Vanilla runs its own villagers and there
 * is no queue, so what is left is somebody watching a tower go up.
 */
@EventBusSubscriber(modid = SyVillage.MODID)
public final class SyVillageEvents {

    private SyVillageEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Raise.tick();
        Previews.tick(event.getServer());
    }

    /** Half a tower is worse than a whole one. Whatever is still going up, finish it. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        Raise.finishAll();
    }
}
