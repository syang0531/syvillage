package com.syang.syvillage.registry;

import com.syang.syvillage.SyVillage;
import com.syang.syvillage.data.Drawing;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** What a blueprint carries: which building it is of. */
public final class ModComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, SyVillage.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Drawing>> DRAWING =
            COMPONENTS.register("drawing", () -> DataComponentType.<Drawing>builder()
                    .persistent(Drawing.CODEC)
                    .networkSynchronized(Drawing.STREAM_CODEC)
                    .build());

    private ModComponents() {}

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
