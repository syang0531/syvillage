package com.syang.syvillage.registry;

import com.google.common.collect.ImmutableSet;
import com.syang.syvillage.SyVillage;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;
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
 * com.syang.syvillage.build.Trades}. Our villagers do not act. The town does.
 */
public final class ModVillagers {

    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, SyVillage.MODID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, SyVillage.MODID);

    public static final ResourceKey<PoiType> VILLAGE_HEAD_POI = ResourceKey.create(
            Registries.POINT_OF_INTEREST_TYPE,
            Identifier.fromNamespaceAndPath(SyVillage.MODID, "village_head"));

    public static final ResourceKey<VillagerProfession> VILLAGE_HEAD = ResourceKey.create(
            Registries.VILLAGER_PROFESSION,
            Identifier.fromNamespaceAndPath(SyVillage.MODID, "village_head"));

    public static final ResourceKey<PoiType> ARCHITECT_POI = ResourceKey.create(
            Registries.POINT_OF_INTEREST_TYPE,
            Identifier.fromNamespaceAndPath(SyVillage.MODID, "architect"));

    public static final ResourceKey<VillagerProfession> ARCHITECT = ResourceKey.create(
            Registries.VILLAGER_PROFESSION,
            Identifier.fromNamespaceAndPath(SyVillage.MODID, "architect"));

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
                Component.translatable("entity.syvillage.villager.village_head"),
                held -> held.is(VILLAGE_HEAD_POI),
                acquirable -> acquirable.is(VILLAGE_HEAD_POI),
                ImmutableSet.of(),
                ImmutableSet.of(),
                SoundEvents.VILLAGER_WORK_MASON,
                // Data, in data/syvillage/trade_set. The village head deals in people and
                // places: a map to the next village, a bell to found one with, and a charter
                // that is a family willing to move.
                trades("village_head")));

        POI_TYPES.register("architect", () -> new PoiType(
                ImmutableSet.copyOf(ModBlocks.ARCHITECTS_TABLE.get()
                        .getStateDefinition().getPossibleStates()),
                SEATS, WALKING_DISTANCE));
        PROFESSIONS.register("architect", () -> new VillagerProfession(
                Component.translatable("entity.syvillage.villager.architect"),
                held -> held.is(ARCHITECT_POI),
                acquirable -> acquirable.is(ARCHITECT_POI),
                ImmutableSet.of(),
                ImmutableSet.of(),
                SoundEvents.VILLAGER_WORK_MASON,
                // Likewise, and this is where the mod's progression actually lives: which
                // buildings an architect will draw for you is their trade level, which is
                // vanilla's own ratchet rather than anything we count.
                trades("architect")));
    }

    private ModVillagers() {}

    /**
     * A profession's five levels of trades, as keys into the trade_set registry.
     *
     * <p>All five, even where the upper ones are one filler trade each: the lookup is a plain
     * map get, and a villager who levels into a missing entry is not something to find out
     * about in a crash report.
     */
    private static Int2ObjectMap<ResourceKey<TradeSet>> trades(String profession) {
        Int2ObjectMap<ResourceKey<TradeSet>> out = new Int2ObjectOpenHashMap<>();
        for (int level = 1; level <= 5; level++) {
            out.put(level, ResourceKey.create(Registries.TRADE_SET,
                    Identifier.fromNamespaceAndPath(SyVillage.MODID,
                            profession + "/level_" + level)));
        }
        return out;
    }

    /** Whether this is the job we added. Asked of a villager, not of a block. */
    public static boolean isVillageHead(Holder<VillagerProfession> profession) {
        return profession.is(VILLAGE_HEAD);
    }

    public static boolean isArchitect(Holder<VillagerProfession> profession) {
        return profession.is(ARCHITECT);
    }

    public static void register(IEventBus modBus) {
        POI_TYPES.register(modBus);
        PROFESSIONS.register(modBus);
    }
}
