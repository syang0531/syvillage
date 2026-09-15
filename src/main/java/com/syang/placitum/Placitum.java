package com.syang.placitum;

import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.registry.ModAttachments;
import com.syang.placitum.event.ModBusEvents;
import com.syang.placitum.registry.ModEntities;
import java.util.UUID;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point.
 *
 * <p>Placitum turns a registered vanilla village into a settlement that grows and defends
 * itself. The design lives in {@code CLAUDE.md} and {@code docs/}; its invariants are not
 * negotiable. The first one governs everything here: the {@code Resident} record is the truth
 * and the entity is only a view of it.
 */
@Mod(Placitum.MODID)
public class Placitum {

    public static final String MODID = "placitum";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    /** Default value of the resident-id attachment: "this entity is not ours". */
    public static final UUID NIL_UUID = new UUID(0L, 0L);

    public Placitum(IEventBus modBus, ModContainer container) {
        ModAttachments.register(modBus);
        ModEntities.register(modBus);
        ModBusEvents.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, PlacitumConfig.SPEC);
    }
}
