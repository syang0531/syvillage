package com.syang.placitum.settlement;

import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.Assignment;
import com.syang.placitum.data.DefenseState;
import com.syang.placitum.data.GearSet;
import com.syang.placitum.data.LifeStage;
import com.syang.placitum.data.Lineage;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.ResidentState;
import com.syang.placitum.data.ResidentTask;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.data.SettlementId;
import com.syang.placitum.data.SimClock;
import com.syang.placitum.data.Vitals;
import com.syang.placitum.registry.ModAttachments;
import com.syang.placitum.Placitum;
import com.syang.placitum.store.SettlementManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

/**
 * Turning a vanilla village into a settlement.
 *
 * <p>Registration is one right-click on the bell, and nothing else in the world changes.
 * Unregistered villages never touch a line of this mod's code, which keeps old saves safe and
 * puts the performance ceiling in the player's hands.
 */
public final class Registration {

    /** Why a registration attempt was refused, so the player gets a real answer. */
    public sealed interface Result {
        record Success(Settlement settlement) implements Result {}
        record Overlaps(String otherName, int distance, int required) implements Result {}
        record NotEnoughBeds(int found, int required) implements Result {}
        record NotEnoughVillagers(int found, int required) implements Result {}
        record AlreadyRegistered(String name) implements Result {}
    }

    private Registration() {}

    public static Result register(ServerLevel level, SettlementManager manager, BlockPos bellPos) {
        int claimChunks = PlacitumConfig.CLAIM_RADIUS_CHUNKS.get();
        int claimBlocks = claimChunks * 16;

        for (SettlementId existing : manager.listed()) {
            if (!existing.dimension().equals(level.dimension())) {
                continue;
            }
            int distance = (int) Math.sqrt(existing.center().distSqr(bellPos));
            int required = PlacitumConfig.MIN_SETTLEMENT_DISTANCE.get();
            if (distance < required) {
                if (existing.center().equals(bellPos)) {
                    return new Result.AlreadyRegistered(existing.name());
                }
                return new Result.Overlaps(existing.name(), distance, required);
            }
        }

        int beds = (int) countBeds(level, bellPos, claimBlocks);
        int minBeds = PlacitumConfig.REGISTER_MIN_BEDS.get();
        if (beds < minBeds) {
            return new Result.NotEnoughBeds(beds, minBeds);
        }

        List<Villager> villagers = nearbyVillagers(level, bellPos, claimBlocks);
        int minResidents = PlacitumConfig.REGISTER_MIN_RESIDENTS.get();
        if (villagers.size() < minResidents) {
            return new Result.NotEnoughVillagers(villagers.size(), minResidents);
        }

        UUID id = UUID.randomUUID();
        RandomSource rng = RandomSource.create(id.getMostSignificantBits());
        SettlementId identity = new SettlementId(id, NameGenerator.settlementName(rng),
                level.dimension(), bellPos, claimChunks);

        List<Resident> residents = adopt(level, villagers, manager, rng);
        Settlement settlement = Settlement.founding(identity, residents,
                SimClock.startingAt(level.getGameTime()), PlacitumConfig.SAFETY_WINDOW_DAYS.get());

        manager.put(settlement);
        return new Result.Success(settlement);
    }

    /**
     * Converts the villagers already living here into residents.
     *
     * <p>The entities are kept, not replaced: they keep their trades, their professions and
     * their gossip. All that changes is that they now have a record behind them - and a name.
     */
    private static List<Resident> adopt(ServerLevel level, List<Villager> villagers,
            SettlementManager manager, RandomSource rng) {
        Set<String> takenNames = new HashSet<>();
        Map<String, Integer> professions = new TreeMap<>();
        List<Resident> residents = new ArrayList<>();
        for (Villager villager : villagers) {
            UUID residentId = UUID.randomUUID();
            Lineage lineage = NameGenerator.founder(rng, takenNames);
            Identifier job = ProfessionMap.of(villager);
            professions.merge(ProfessionMap.vanillaName(villager), 1, Integer::sum);

            // First generation has no parents. Their history starts here, and pretending
            // otherwise would put births in the chronicle that nobody witnessed.
            Resident resident = new Resident(
                    residentId,
                    lineage,
                    villager.isBaby() ? LifeStage.CHILD : LifeStage.ADULT,
                    villager.isBaby() ? 3 : 25,
                    new Assignment(job, Optional.empty(), Optional.empty()),
                    new Vitals((int) Math.ceil(villager.getHealth()), 50, 50),
                    ProfessionMap.militiaEligible(job),
                    false,
                    GearSet.EMPTY,
                    ResidentTask.IDLE,
                    villager.blockPosition(),
                    ResidentState.MATERIALIZED,
                    com.syang.placitum.lifecycle.Lifecycle.writeVanillaState(villager));

            villager.setData(ModAttachments.RESIDENT_ID, residentId);
            manager.bind(residentId, villager.getUUID());
            residents.add(resident);
        }
        // What vanilla actually reported, not what we mapped it to. A village of freshly
        // generated villagers is mostly unemployed, and without this line that is
        // indistinguishable from a broken mapping.
        Placitum.LOGGER.info("Adopted {} villager(s); vanilla professions: {}",
                residents.size(), professions);
        return residents;
    }

    public static long countBeds(ServerLevel level, BlockPos center, int radius) {
        PoiManager poi = level.getPoiManager();
        return poi.getCountInRange(holder -> holder.is(homeKey()), center, radius, PoiManager.Occupancy.ANY);
    }

    private static ResourceKey<PoiType> homeKey() {
        return PoiTypes.HOME;
    }

    /**
     * The one place an area entity scan is allowed.
     *
     * <p>It runs once, when the player registers a village, with the chunks already loaded
     * because the player is standing in them. The banned pattern is a recurring claim-wide
     * scan for threat detection; that uses watch points instead - see docs/defense.md.
     */
    public static List<Villager> nearbyVillagers(ServerLevel level, BlockPos center, int radius) {
        AABB box = new AABB(center).inflate(radius);
        List<Villager> found = new ArrayList<>(level.getEntitiesOfClass(Villager.class, box,
                v -> v.isAlive() && v.getData(ModAttachments.RESIDENT_ID).equals(com.syang.placitum.Placitum.NIL_UUID)));
        // Stable order so two registrations of the same village produce the same records.
        found.sort((a, b) -> a.getUUID().compareTo(b.getUUID()));
        return found;
    }
}
