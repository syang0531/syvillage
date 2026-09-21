package com.syang.syvillage.event;

import com.syang.syvillage.SyVillage;
import com.syang.syvillage.build.Raise;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The server-side hooks, which are two, and both are flourishes.
 *
 * <p>CLAUDE.md forbids a tick that walks the world looking for work, and the distinction is
 * worth keeping in view: this one is bounded by what a player started in the last few seconds,
 * it reads nothing from the level, it holds nothing that is saved, and when it is interrupted it
 * finishes rather than resumes. A planning tick was none of those things, and six of the old
 * settlement pipeline's bugs lived on one.
 *
 * <p>There used to be a preview sweep here as well, expiring outlines on a clock. Outlines
 * belong to drafting tables now, and a block in the world does not need to be swept up.
 */
@EventBusSubscriber(modid = SyVillage.MODID)
public final class SyVillageEvents {

    private SyVillageEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Raise.tick();
    }

    /** Half a tower is worse than a whole one. Whatever is still going up, finish it. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        Raise.finishAll();
    }
}
