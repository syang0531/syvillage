package com.syang.placitum.lifecycle;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.Assignment;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.ResidentState;
import com.syang.placitum.data.Vitals;
import com.syang.placitum.registry.ModAttachments;
import com.syang.placitum.settlement.ProfessionMap;
import com.syang.placitum.store.SettlementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import java.util.UUID;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

/**
 * Turns a resident record into an entity and back.
 *
 * <p>Data flows one way at a time: record to entity on promote, entity to record on demote.
 * There is deliberately no continuous synchronisation - that is how the two copies start
 * disagreeing.
 */
public final class Lifecycle {

    private Lifecycle() {}

    /**
     * Spawns the entity view of a resident.
     *
     * @return the updated resident, or the original if the position is not entity-ticking yet
     */
    public static Resident promote(ServerLevel level, SettlementManager manager, Resident resident) {
        UUID bound = manager.entityOf(resident.id());
        if (bound != null && level.getEntity(bound) != null) {
            // This resident already has a body. An entity carrying its id loaded from its own
            // chunk while the promotion was still in flight, so spawning now would leave two
            // identical villagers standing next to each other with one record between them.
            Placitum.LOGGER.debug("{} already has entity {}; not spawning a second",
                    resident.lineage().fullName(), bound);
            return resident.withState(ResidentState.MATERIALIZED);
        }

        BlockPos pos = resident.coarsePos();
        if (!level.isPositionEntityTicking(pos)) {
            return resident;   // held back; the promotion task retries next pass
        }
        Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.LOAD);
        if (villager == null) {
            Placitum.LOGGER.warn("Could not create a villager for resident {}", resident.id());
            return resident;
        }

        villager.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        villager.setData(ModAttachments.RESIDENT_ID, resident.id());
        villager.setCustomName(Component.literal(resident.lineage().fullName()));
        villager.setCustomNameVisible(false);
        villager.setHealth(Math.max(1, resident.vitals().health()));
        readOffers(level, resident.offers()).ifPresent(villager::setOffers);

        level.addFreshEntity(villager);
        manager.bind(resident.id(), villager.getUUID());
        Placitum.LOGGER.debug("Promoted {} ({}) at {}", resident.lineage().fullName(),
                resident.id(), pos.toShortString());
        return resident.withState(ResidentState.MATERIALIZED);
    }

    /**
     * Copies the entity's state back into the record and discards the entity.
     *
     * <p>Every path that removes an entity has to come through here. A chunk unloading, the
     * server stopping and the player walking away all lose the same data otherwise.
     */
    public static Resident demote(ServerLevel level, SettlementManager manager, Resident resident) {
        Entity entity = findEntity(level, manager, resident);
        Resident out = resident;
        if (entity instanceof Villager villager) {
            out = writeBack(level, out, villager);
        }
        if (entity != null) {
            entity.discard();
        } else {
            Placitum.LOGGER.debug("Demoting {} with no entity bound; nothing to write back",
                    resident.id());
        }
        manager.unbind(resident.id());
        return out.withState(ResidentState.VIRTUAL);
    }

    public static Resident writeBack(ServerLevel level, Resident resident, Villager villager) {
        Vitals vitals = resident.vitals().withHealth((int) Math.ceil(villager.getHealth()));
        return resident
                .withVitals(vitals)
                .withCoarsePos(villager.blockPosition())
                .withAssignment(refreshJob(resident, villager))
                .withOffers(writeOffers(level, villager.getOffers()));
    }

    /**
     * Re-reads the villager's profession.
     *
     * <p>Vanilla villagers acquire professions by claiming a job site, which can happen long
     * after registration - a freshly generated village is mostly unemployed. Freezing the job
     * at adoption would leave those residents unemployed forever, and a settlement with no
     * farmers can never produce anything.
     *
     * <p>This is allowed precisely because it happens here. Entity state flows into the record
     * on demote and nowhere else.
     */
    private static Assignment refreshJob(Resident resident, Villager villager) {
        Identifier job = ProfessionMap.of(villager);
        if (job.equals(resident.assignment().job())) {
            return resident.assignment();
        }
        Placitum.LOGGER.debug("{} is now a {} (vanilla: {})", resident.lineage().fullName(),
                job.getPath(), ProfessionMap.vanillaName(villager));
        return resident.assignment().withJob(job);
    }

    /**
     * Trade lists cross the entity boundary as an opaque tag.
     *
     * <p>Encoding here, once, is the whole of the exception to principle 1. Nothing downstream
     * unpacks it, which is why it can never drift out of agreement with anything.
     */
    public static CompoundTag writeOffers(ServerLevel level, MerchantOffers offers) {
        if (offers.isEmpty()) {
            return new CompoundTag();
        }
        RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        CompoundTag out = new CompoundTag();
        MerchantOffers.CODEC.encodeStart(ops, offers)
                .resultOrPartial(error -> Placitum.LOGGER.warn("Could not store trades: {}", error))
                .ifPresent(tag -> out.put("offers", tag));
        return out;
    }

    public static java.util.Optional<MerchantOffers> readOffers(ServerLevel level, CompoundTag tag) {
        Tag stored = tag.get("offers");
        if (stored == null) {
            return java.util.Optional.empty();
        }
        RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        return MerchantOffers.CODEC.parse(ops, stored)
                .resultOrPartial(error -> Placitum.LOGGER.warn("Could not restore trades: {}", error));
    }

    public static @Nullable Entity findEntity(ServerLevel level, SettlementManager manager, Resident resident) {
        UUID entityId = manager.entityOf(resident.id());
        return entityId == null ? null : level.getEntity(entityId);
    }
}
