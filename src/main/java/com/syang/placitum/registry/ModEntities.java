package com.syang.placitum.registry;

import com.syang.placitum.Placitum;
import com.syang.placitum.entity.MilitiaEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entity types Placitum adds. */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Placitum.MODID);

    /** Same size and tracking range as a villager, because it is one wearing different work. */
    public static final Supplier<EntityType<MilitiaEntity>> MILITIA = TYPES.register(
            "militia",
            key -> EntityType.Builder.of(MilitiaEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, key)));

    private ModEntities() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
