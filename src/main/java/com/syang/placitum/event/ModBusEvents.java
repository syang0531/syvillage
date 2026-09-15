package com.syang.placitum.event;

import com.syang.placitum.entity.MilitiaEntity;
import com.syang.placitum.registry.ModEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Mod-bus registration.
 *
 * <p>Listeners are added by hand rather than by annotation: 26.2 dropped the {@code bus}
 * attribute from {@code @EventBusSubscriber}, so which bus a handler lands on is no longer
 * something the annotation can say.
 */
public final class ModBusEvents {

    private ModBusEvents() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(EntityAttributeCreationEvent.class, event ->
                event.put(ModEntities.MILITIA.get(), MilitiaEntity.createAttributes().build()));
    }
}
