package com.syang.placitum;

import com.syang.placitum.config.PlacitumConfig;
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
 * <p>Placitum gives a registered vanilla village streets, street lights, houses and fields, and
 * leaves everything a villager does to vanilla. The design lives in {@code CLAUDE.md} and
 * {@code docs/design.md}; its first invariant governs everything here, including what is absent
 * from this file: we do not simulate villagers, so there is nothing to attach to one.
 */
@Mod(Placitum.MODID)
public class Placitum {

    public static final String MODID = "placitum";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    /** Default value of the resident-id attachment: "this entity is not ours". */
    public static final UUID NIL_UUID = new UUID(0L, 0L);

    public Placitum(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, PlacitumConfig.SPEC);
    }
}
