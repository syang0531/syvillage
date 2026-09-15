package com.syang.placitum.registry;

import com.syang.placitum.Placitum;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.UUIDUtil;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The only thing Placitum ever attaches to an entity: which resident it is a view of.
 *
 * <p>State does not live here. An entity carries an identity and nothing else, so there is
 * never a question of which copy is authoritative.
 */
public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Placitum.MODID);

    /** Serialized so an entity saved inside a chunk can be rebound after a restart. */
    public static final Supplier<AttachmentType<UUID>> RESIDENT_ID = TYPES.register(
            "resident_id",
            () -> AttachmentType.builder(() -> Placitum.NIL_UUID)
                    .serialize(UUIDUtil.CODEC.fieldOf("resident_id"))
                    .build());

    private ModAttachments() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
