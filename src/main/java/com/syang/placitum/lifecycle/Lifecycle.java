package com.syang.placitum.lifecycle;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.Assignment;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.ResidentState;
import com.syang.placitum.data.Vitals;
import com.syang.placitum.registry.ModAttachments;
import com.syang.placitum.settlement.ProfessionMap;
import com.syang.placitum.store.SettlementManager;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Turns a resident record into an entity and back.
 *
 * <p>Data flows one way at a time: record to entity on promote, entity to record on demote.
 * There is deliberately no continuous synchronisation - that is how the two copies start
 * disagreeing.
 *
 * <p>The entity half of the trip uses vanilla's own serialization rather than copying fields
 * by hand. That decision was made the expensive way. Hand-copying started with health and
 * trades, then needed VillagerData, then the job site and XP, and each round of testing found
 * another thing vanilla owned that we had dropped - a villager whose profession vanished, then
 * whose trades vanished, then who was fired by vanilla seconds after arriving because the job
 * site memory was missing. A vanilla villager is not a handful of fields; it is a brain full of
 * memories, POI claims, gossip and inventory, and reproducing it by hand is re-implementing
 * chunk loading badly. Chunk unload and reload is exactly this operation, so we do what it does.
 */
public final class Lifecycle {

    /**
     * Set while this class is spawning a villager itself.
     *
     * <p>{@code addFreshEntity} fires EntityJoinLevelEvent synchronously, so our own spawn
     * lands in our own rebind handler: it scans every settlement and writes one back, per
     * villager, for work that promote is already doing. It also logged a "Rebound" line right
     * before every "Promoted" line, which reads exactly like a duplicate spawn - it cost an
     * hour of chasing one that was never there.
     */
    private static boolean spawningOurOwn;

    private Lifecycle() {}

    /** True when the entity now joining is one promote is in the middle of adding. */
    public static boolean isSelfSpawn() {
        return spawningOurOwn;
    }

    /**
     * Spawns the entity view of a resident.
     *
     * @return the updated resident, or the original if the position is not entity-ticking yet
     */
    public static Resident promote(ServerLevel level, SettlementManager manager, Resident resident) {
        UUID bound = manager.entityOf(resident.id());
        if (bound != null && level.getEntity(bound) != null) {
            // Already has a body, from an entity that loaded with its chunk while the promotion
            // was in flight. A second spawn would leave two villagers sharing one record.
            return resident.withState(ResidentState.MATERIALIZED);
        }

        BlockPos pos = resident.coarsePos();
        if (!level.isPositionEntityTicking(pos)) {
            return resident;   // held back; the promotion task retries next pass
        }

        Villager villager = restore(level, resident);
        if (villager == null) {
            return resident;
        }

        // Our fields win over the restored copy: the record is the source of truth for anything
        // the simulation owns, and the snapshot may be days stale in those places.
        villager.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, villager.getYRot(), 0.0F);
        villager.setData(ModAttachments.RESIDENT_ID, resident.id());
        villager.setCustomName(Component.literal(resident.lineage().fullName()));
        villager.setCustomNameVisible(false);
        villager.setHealth(Math.max(1, resident.vitals().health()));

        addSilently(level, villager);
        manager.bind(resident.id(), villager.getUUID());
        Placitum.LOGGER.debug("Promoted {} ({}) at {}", resident.lineage().fullName(),
                resident.id(), pos.toShortString());
        return resident.withState(ResidentState.MATERIALIZED);
    }

    /**
     * Rebuilds the villager from its saved NBT, or makes a fresh one if there is none.
     *
     * <p>A blank villager is only correct for a resident who has never been materialized. Every
     * other path has a snapshot, and using a blank one there is what silently destroys
     * professions, trades and job sites.
     */
    private static @Nullable Villager restore(ServerLevel level, Resident resident) {
        Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.LOAD);
        if (villager == null) {
            Placitum.LOGGER.warn("Could not create a villager for resident {}", resident.id());
            return null;
        }
        restoreInto(level, villager, resident.vanillaState());
        return villager;
    }

    /**
     * Pours a saved villager into an entity that already exists.
     *
     * <p>Shared with conscription, where the target is a MilitiaEntity rather than a Villager.
     * Both are Villagers as far as the NBT is concerned, which is exactly why the militia
     * subclasses one - the swap carries everything without a field list to keep in step.
     */
    public static void restoreInto(ServerLevel level, Villager target, CompoundTag saved) {
        if (saved.isEmpty()) {
            return;
        }
        try (ProblemReporter.ScopedCollector reporter =
                     new ProblemReporter.ScopedCollector(Placitum.LOGGER)) {
            target.load(TagValueInput.create(reporter, level.registryAccess(), saved));
        } catch (Exception e) {
            Placitum.LOGGER.warn("Could not restore saved villager state; using a blank one", e);
        }
    }

    /** Adds an entity without our own join handler treating it as an arrival to rebind. */
    public static void addSilently(ServerLevel level, Entity entity) {
        spawningOurOwn = true;
        try {
            level.addFreshEntity(entity);
        } finally {
            spawningOurOwn = false;
        }
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
            releasePois(level, villager);
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
                .withVanillaState(writeVanillaState(villager));
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
     * The villager exactly as vanilla would write it to a chunk.
     *
     * <p>This is the single exception to principle 1, and it is opaque on purpose: the
     * simulation never opens it. Storing everything is what makes it safe - there is no list of
     * fields to keep in sync and therefore no field to forget.
     */
    public static CompoundTag writeVanillaState(Villager villager) {
        try (ProblemReporter.ScopedCollector reporter =
                     new ProblemReporter.ScopedCollector(Placitum.LOGGER)) {
            TagValueOutput out = TagValueOutput.createWithContext(reporter, villager.registryAccess());
            villager.saveWithoutId(out);
            return out.buildResult();
        } catch (Exception e) {
            Placitum.LOGGER.warn("Could not snapshot villager for later restore", e);
            return new CompoundTag();
        }
    }

    /**
     * Gives up the villager's claims on its bed, workstation and meeting point.
     *
     * <p>Vanilla only does this when a villager dies. Our entities leave constantly - every
     * demote, every chunk unload, every conscription swap - and discard() releases nothing, so
     * each round trip left a bed claimed by an entity that no longer exists.
     *
     * <p>After a day of testing every bed in the village was held by a ghost and all six
     * residents reported having nowhere to sleep. Left alone it ratchets one way: villages
     * become permanently bedless, and since M2 derives carrying capacity from beds, no child
     * would ever be born in a village the player had visited twice.
     *
     * <p>The memories are kept. On promote the restored brain still remembers the bed, and with
     * the claim actually free it can take it back.
     */
    public static void releasePois(ServerLevel level, Villager villager) {
        releasePoi(level, villager, MemoryModuleType.HOME);
        releasePoi(level, villager, MemoryModuleType.JOB_SITE);
        releasePoi(level, villager, MemoryModuleType.MEETING_POINT);
    }

    private static void releasePoi(ServerLevel level, Villager villager,
            MemoryModuleType<GlobalPos> memory) {
        villager.getBrain().getMemory(memory).ifPresent(pos -> {
            if (pos.dimension().equals(level.dimension())) {
                level.getPoiManager().release(pos.pos());
            }
        });
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
