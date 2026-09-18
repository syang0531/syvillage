package com.syang.placitum;

import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.registry.ModBlocks;
import com.syang.placitum.registry.ModCreativeTabs;
import com.syang.placitum.registry.ModItems;
import com.syang.placitum.registry.ModVillagers;
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

    public Placitum(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, PlacitumConfig.SPEC);
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModVillagers.register(modBus);
        ModCreativeTabs.register(modBus);
    }
}
