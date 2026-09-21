package com.syang.syvillage.registry;

import com.syang.syvillage.SyVillage;
import com.syang.syvillage.item.Blueprint;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's own tab, because by now it has enough in it to need one.
 *
 * <p>It used to hang its handful of things off vanilla's tabs, which was right while there were
 * five of them. There are a hundred and sixty-eight drawings now, and putting those in
 * <em>Building Blocks</em> would bury somebody else's tab under this mod.
 *
 * <p>Order is deliberate: the two tables and the statue first, because they are what a player
 * has to place before a drawing means anything, then the drawings behind them.
 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SyVillage.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "syvillage", () -> CreativeModeTab.builder()
                    .title(Component.translatable("syvillage.title"))
                    .icon(() -> new ItemStack(ModBlocks.LORDS_TABLE.get()))
                    .withTabsBefore(ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                            Identifier.withDefaultNamespace("spawn_eggs")))
                    .displayItems((parameters, output) -> {
                        output.accept(ModBlocks.VILLAGE_HEAD_TABLE.get());
                        output.accept(ModBlocks.LORDS_TABLE.get());
                        output.accept(ModBlocks.GUARDIAN_STATUE.get());
                        output.accept(ModItems.FREEMANS_CHARTER.get());
                        output.accept(ModItems.LORDS_SEAL.get());
                        output.accept(ModItems.GOLEM_HEART.get());
                        Blueprint.everything().forEach(output::accept);
                    })
                    .build());

    private ModCreativeTabs() {}

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
