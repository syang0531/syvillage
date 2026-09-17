package com.syang.placitum.registry;

import com.google.common.collect.ImmutableSet;
import com.syang.placitum.Placitum;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The trades this mod adds, and the workstations villagers take them at.
 *
 * <p>Adding a profession is registry work and nothing else. Vanilla decides who takes the job,
 * walks them to it, and runs the trading; the brain reads the point-of-interest tag and does the
 * rest. There is no behaviour here to write, and that is the whole reason a profession was the
 * right shape for this: see CLAUDE.md principle 1.
 *
 * <p>What the profession <em>means</em> is decided elsewhere, in {@link
 * com.syang.placitum.build.Trades}. Our villagers do not act. The town does.
 */
public final class ModVillagers {

    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, Placitum.MODID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, Placitum.MODID);

    public static final ResourceKey<PoiType> VILLAGE_HEAD_POI = ResourceKey.create(
            Registries.POINT_OF_INTEREST_TYPE,
            Identifier.fromNamespaceAndPath(Placitum.MODID, "village_head"));

    public static final ResourceKey<VillagerProfession> VILLAGE_HEAD = ResourceKey.create(
            Registries.VILLAGER_PROFESSION,
            Identifier.fromNamespaceAndPath(Placitum.MODID, "village_head"));

    public static final ResourceKey<PoiType> LORD_POI = ResourceKey.create(
            Registries.POINT_OF_INTEREST_TYPE,
            Identifier.fromNamespaceAndPath(Placitum.MODID, "lord"));

    public static final ResourceKey<VillagerProfession> LORD = ResourceKey.create(
            Registries.VILLAGER_PROFESSION,
            Identifier.fromNamespaceAndPath(Placitum.MODID, "lord"));

    /**
     * One ticket, because the table seats one.
     *
     * <p>{@code maxTickets} is how many villagers may claim this block at once and every vanilla
     * job site uses one. A village with two heads would qualify for nothing twice.
     */
    private static final int SEATS = 1;

    /** Blocks a villager will walk to claim it from. Vanilla job sites use one. */
    private static final int WALKING_DISTANCE = 1;

    static {
        POI_TYPES.register("village_head", () -> new PoiType(
                    ImmutableSet.copyOf(ModBlocks.VILLAGE_HEAD_TABLE.get()
                            .getStateDefinition().getPossibleStates()),
                SEATS, WALKING_DISTANCE));
        PROFESSIONS.register("village_head", () -> new VillagerProfession(
                Component.translatable("entity.placitum.villager.village_head"),
                held -> held.is(VILLAGE_HEAD_POI),
                acquirable -> acquirable.is(VILLAGE_HEAD_POI),
                ImmutableSet.of(),
                ImmutableSet.of(),
                SoundEvents.VILLAGER_WORK_MASON,
                // Vanilla's mason trades, for now. A profession with nothing to sell opens an
                // empty screen, which reads as a broken mod rather than as a design decision;
                // the village head dealing in stone is at least the right subject. Trades of its
                // own are JSON in 26.2 - a trade_set registry - and are worth doing properly
                // once there is something only a village head should sell.
                Int2ObjectMap.ofEntries(
                        Int2ObjectMap.entry(1, TradeSets.MASON_LEVEL_1),
                        Int2ObjectMap.entry(2, TradeSets.MASON_LEVEL_2),
                        Int2ObjectMap.entry(3, TradeSets.MASON_LEVEL_3),
                        Int2ObjectMap.entry(4, TradeSets.MASON_LEVEL_4),
                        Int2ObjectMap.entry(5, TradeSets.MASON_LEVEL_5))));

        POI_TYPES.register("lord", () -> new PoiType(
                ImmutableSet.copyOf(ModBlocks.LORDS_TABLE.get()
                        .getStateDefinition().getPossibleStates()),
                SEATS, WALKING_DISTANCE));
        PROFESSIONS.register("lord", () -> new VillagerProfession(
                Component.translatable("entity.placitum.villager.lord"),
                held -> held.is(LORD_POI),
                acquirable -> acquirable.is(LORD_POI),
                ImmutableSet.of(),
                ImmutableSet.of(),
                SoundEvents.VILLAGER_WORK_MASON,
                // Borrowed, like the village head's, and marked as debt in docs/roadmap.md.
                Int2ObjectMap.ofEntries(
                        Int2ObjectMap.entry(1, TradeSets.MASON_LEVEL_1),
                        Int2ObjectMap.entry(2, TradeSets.MASON_LEVEL_2),
                        Int2ObjectMap.entry(3, TradeSets.MASON_LEVEL_3),
                        Int2ObjectMap.entry(4, TradeSets.MASON_LEVEL_4),
                        Int2ObjectMap.entry(5, TradeSets.MASON_LEVEL_5))));
    }

    private ModVillagers() {}

    /** Whether this is the job we added. Asked of a villager, not of a block. */
    public static boolean isVillageHead(Holder<VillagerProfession> profession) {
        return profession.is(VILLAGE_HEAD);
    }

    public static boolean isLord(Holder<VillagerProfession> profession) {
        return profession.is(LORD);
    }

    public static void register(IEventBus modBus) {
        POI_TYPES.register(modBus);
        PROFESSIONS.register(modBus);
    }
}
