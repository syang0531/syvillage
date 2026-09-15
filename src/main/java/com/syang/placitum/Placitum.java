package com.syang.placitum;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point.
 *
 * <p>Placitum turns a registered vanilla village into a settlement that grows and defends
 * itself. The design is documented in {@code CLAUDE.md} and {@code docs/}; the invariants
 * there are not negotiable. In particular: the {@code Resident} record is the source of
 * truth and the entity is only a view of it.
 */
@Mod(Placitum.MODID)
public class Placitum {

    public static final String MODID = "placitum";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public Placitum(IEventBus modBus, ModContainer container) {
        // Registries are attached here as they are introduced (M0: none yet).
        LOGGER.info("Placitum loaded");
    }
}
