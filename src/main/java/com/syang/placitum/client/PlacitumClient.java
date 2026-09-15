package com.syang.placitum.client;

import com.syang.placitum.Placitum;
import com.syang.placitum.registry.ModEntities;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-side registration.
 *
 * <p>A separate {@code @Mod} entry point rather than an annotated handler: 26.2 dropped the
 * {@code bus} attribute from {@code @EventBusSubscriber}, and a dist-scoped mod class keeps
 * client-only types from ever being loaded on a dedicated server.
 *
 * <p>The militia borrows the vanilla villager renderer outright. It is a Villager subclass, so
 * it renders with the same model, biome outfit and profession clothes - which is the point: the
 * player should see the same farmer they have been watching all week, now holding a sword, not
 * a new mob that appeared from nowhere.
 */
@Mod(value = Placitum.MODID, dist = Dist.CLIENT)
public final class PlacitumClient {

    public PlacitumClient(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event ->
                event.registerEntityRenderer(ModEntities.MILITIA.get(), VillagerRenderer::new));
    }
}
