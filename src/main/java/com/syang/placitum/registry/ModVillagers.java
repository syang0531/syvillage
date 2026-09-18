package com.syang.placitum.registry;

import com.google.common.collect.ImmutableSet;
import com.syang.placitum.Placitum;
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
                // Data, in data/placitum/trade_set. The one thing only a village head sells
                // is the lord's seal, at the second level: the first is there to be levelled
                // through, and the ones after so that a master has something new to say.
                trades("village_head")));

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
                // Likewise. The golem's heart is at the second level.
                trades("lord")));
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
                    Identifier.fromNamespaceAndPath(Placitum.MODID,
                            profession + "/level_" + level)));
        }
        return out;
    }

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
