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
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
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
        applyVanillaState(level, villager, resident.vanillaState());

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
                .withVanillaState(writeVanillaState(level, villager));
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
     * Copies out everything vanilla owns about this villager.
     *
     * <p>Promote builds a brand new entity, so whatever is not captured here is destroyed on
     * every round trip. Trades were the obvious one; VillagerData is the one that bit us -
     * without it a farmer came back unemployed, and since the write-back re-reads the
     * profession, the settlement quietly lost a farmer every time the player left.
     *
     * <p>Encoding here and decoding in {@link #applyVanillaState} is the whole of the exception
     * to principle 1. Nothing else unpacks this tag, so it cannot drift out of agreement with
     * anything.
     */
    public static CompoundTag writeVanillaState(ServerLevel level, Villager villager) {
        RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        CompoundTag out = new CompoundTag();

        MerchantOffers offers = villager.getOffers();
        if (!offers.isEmpty()) {
            MerchantOffers.CODEC.encodeStart(ops, offers)
                    .resultOrPartial(error -> Placitum.LOGGER.warn("Could not store trades: {}", error))
                    .ifPresent(tag -> out.put("offers", tag));
        }
        VillagerData.CODEC.encodeStart(ops, villager.getVillagerData())
                .resultOrPartial(error -> Placitum.LOGGER.warn("Could not store villager data: {}", error))
                .ifPresent(tag -> out.put("villager_data", tag));
        out.putInt("villager_xp", villager.getVillagerXp());

        // The job site is what makes a profession stick. Without it vanilla's ResetProfession
        // fires the villager within seconds of promote - see applyVanillaState.
        villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).ifPresent(site ->
                GlobalPos.CODEC.encodeStart(ops, site)
                        .resultOrPartial(error -> Placitum.LOGGER.warn("Could not store job site: {}", error))
                        .ifPresent(tag -> out.put("job_site", tag)));
        return out;
    }

    /**
     * Puts the villager back the way vanilla had it.
     *
     * <p>Order matters. {@code setVillagerData} clears the trade list whenever the profession
     * changes, so restoring trades first and the profession second destroys the trades we just
     * restored. Profession goes first.
     *
     * <p>The job site matters just as much. Vanilla's ResetProfession behaviour fires any
     * villager that has no JOB_SITE memory, zero XP and level 1 - which is exactly what a
     * freshly built entity looks like. Restoring the profession alone buys a few seconds before
     * vanilla takes it away again, and since demote re-reads the profession, the settlement
     * would record the firing as fact and lose the job for good.
     */
    public static void applyVanillaState(ServerLevel level, Villager villager, CompoundTag tag) {
        if (tag.isEmpty()) {
            return;
        }
        RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);

        Tag data = tag.get("villager_data");
        if (data != null) {
            VillagerData.CODEC.parse(ops, data)
                    .resultOrPartial(error -> Placitum.LOGGER.warn("Could not restore villager data: {}", error))
                    .ifPresent(villager::setVillagerData);
        }
        villager.setVillagerXp(tag.getIntOr("villager_xp", 0));

        Tag site = tag.get("job_site");
        if (site != null) {
            GlobalPos.CODEC.parse(ops, site)
                    .resultOrPartial(error -> Placitum.LOGGER.warn("Could not restore job site: {}", error))
                    .ifPresent(pos -> villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, pos));
        }

        Tag offers = tag.get("offers");
        if (offers != null) {
            MerchantOffers.CODEC.parse(ops, offers)
                    .resultOrPartial(error -> Placitum.LOGGER.warn("Could not restore trades: {}", error))
                    .ifPresent(villager::setOffers);
        }
    }

    /**
     * Hands a villager back to vanilla, alive.
     *
     * <p>Emphatically not demote. Demote discards the entity because the record survives and
     * will rebuild it later. Unregister throws the record away, so discarding here would delete
     * the villager from the world outright - the mod would be destroying the very thing it
     * exists to protect, in the command whose entire purpose is to undo a mistake.
     */
    public static void release(ServerLevel level, SettlementManager manager, Resident resident) {
        Entity entity = findEntity(level, manager, resident);
        if (entity != null) {
            entity.removeData(ModAttachments.RESIDENT_ID);
            if (entity instanceof Villager villager) {
                villager.setCustomName(null);
            }
            Placitum.LOGGER.debug("Released {} back to vanilla", resident.lineage().fullName());
        }
        manager.unbind(resident.id());
    }

    public static @Nullable Entity findEntity(ServerLevel level, SettlementManager manager, Resident resident) {
        UUID entityId = manager.entityOf(resident.id());
        return entityId == null ? null : level.getEntity(entityId);
    }
}
