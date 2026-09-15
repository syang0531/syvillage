package com.syang.placitum.defense;

import com.syang.placitum.Placitum;
import com.syang.placitum.data.GearSet;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.entity.MilitiaEntity;
import com.syang.placitum.lifecycle.Lifecycle;
import com.syang.placitum.registry.ModAttachments;
import com.syang.placitum.registry.ModEntities;
import com.syang.placitum.store.SettlementManager;
import com.syang.placitum.store.SettlementMut;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * Calling villagers up, and standing them back down.
 *
 * <p>The resident's identity lives in the record, so arming someone is swapping which entity is
 * drawing them - not creating a new person. Name, profession, trades and history are untouched
 * because none of them were ever on the entity.
 *
 * <p>The swap goes through the same NBT round trip as promote and demote. That was learned the
 * hard way in M0: copying fields by hand loses whatever you forgot, and what gets forgotten is
 * never the thing you were thinking about. Here it matters more, because the two entities are
 * different types - anything hand-copied would have to be hand-copied twice, in both directions.
 */
public final class Conscription {

    private Conscription() {}

    /**
     * Arms as many eligible residents as the settlement has weapons for.
     *
     * @return residents updated with the gear they drew
     */
    public static List<Resident> muster(ServerLevel level, SettlementManager manager,
            Settlement settlement, SettlementMut stock) {
        int cap = DefenseRating.eligibleCount(settlement);
        List<Resident> updated = new ArrayList<>(settlement.residents());
        BlockPos muster = settlement.anchors().muster().orElse(settlement.center());
        int armed = 0;

        for (int i = 0; i < updated.size() && armed < cap; i++) {
            Resident resident = updated.get(i);
            if (!resident.militiaEligible() || !resident.counts() || resident.gear().armed()) {
                continue;
            }
            Optional<GearSet> drawn = Armoury.draw(stock);
            if (drawn.isEmpty()) {
                // Nothing left on the rack. The rest hide, and the player is told why - this is
                // the lever that makes donating three iron swords visibly change an outcome.
                break;
            }
            Resident armedResident = resident.withGear(drawn.get());
            if (resident.materialized()) {
                armedResident = swapToMilitia(level, manager, armedResident, muster);
            }
            updated.set(i, armedResident);
            armed++;
        }
        if (armed > 0) {
            Placitum.LOGGER.info("MUSTER: '{}' armed {} of {} eligible", settlement.name(), armed, cap);
        }
        return updated;
    }

    /** Puts everyone back to work and the weapons back on the rack. */
    public static List<Resident> standDown(ServerLevel level, SettlementManager manager,
            Settlement settlement, SettlementMut stock) {
        List<Resident> updated = new ArrayList<>(settlement.residents());
        int disarmed = 0;

        for (int i = 0; i < updated.size(); i++) {
            Resident resident = updated.get(i);
            if (!resident.gear().armed()) {
                continue;
            }
            Armoury.returnGear(stock, resident.gear());
            Resident unarmed = resident.withGear(GearSet.EMPTY);
            if (resident.materialized()) {
                unarmed = swapToVillager(level, manager, unarmed);
            }
            updated.set(i, unarmed);
            disarmed++;
        }
        if (disarmed > 0) {
            Placitum.LOGGER.info("STAND DOWN: '{}' returned {} weapon(s)", settlement.name(), disarmed);
        }
        return updated;
    }

    private static Resident swapToMilitia(ServerLevel level, SettlementManager manager,
            Resident resident, BlockPos muster) {
        Entity old = Lifecycle.findEntity(level, manager, resident);
        if (!(old instanceof Villager villager) || old instanceof MilitiaEntity) {
            return resident;
        }
        CompoundTag snapshot = Lifecycle.writeVanillaState(villager);
        MilitiaEntity militia = ModEntities.MILITIA.get().create(level, EntitySpawnReason.LOAD);
        if (militia == null) {
            return resident;
        }
        Lifecycle.restoreInto(level, militia, snapshot);
        militia.snapTo(villager.getX(), villager.getY(), villager.getZ(),
                villager.getYRot(), villager.getXRot());
        militia.setData(ModAttachments.RESIDENT_ID, resident.id());
        militia.setCustomName(villager.getCustomName());
        militia.setHealth(villager.getHealth());
        resident.gear().weapon().ifPresent(weapon ->
                militia.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon)));

        replace(level, manager, resident, villager, militia);
        return resident.withVanillaState(snapshot);
    }

    private static Resident swapToVillager(ServerLevel level, SettlementManager manager,
            Resident resident) {
        Entity old = Lifecycle.findEntity(level, manager, resident);
        if (!(old instanceof MilitiaEntity militia)) {
            return resident;
        }
        // Snapshot the militia, not the stale record: it has been fighting, and its health and
        // whatever else changed while armed has to survive the trip back.
        CompoundTag snapshot = Lifecycle.writeVanillaState(militia);
        Villager villager = net.minecraft.world.entity.EntityTypes.VILLAGER
                .create(level, EntitySpawnReason.LOAD);
        if (villager == null) {
            return resident;
        }
        Lifecycle.restoreInto(level, villager, snapshot);
        villager.snapTo(militia.getX(), militia.getY(), militia.getZ(),
                militia.getYRot(), militia.getXRot());
        villager.setData(ModAttachments.RESIDENT_ID, resident.id());
        villager.setCustomName(militia.getCustomName());
        villager.setHealth(militia.getHealth());
        villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);

        replace(level, manager, resident, militia, villager);
        return resident.withVanillaState(snapshot);
    }

    /**
     * Adds the new body before discarding the old one.
     *
     * <p>Order matters: discard first and there is a tick where the settlement has a resident
     * with no entity, which every head count and every write-back would read as truth.
     */
    private static void replace(ServerLevel level, SettlementManager manager, Resident resident,
            Entity old, Entity fresh) {
        Lifecycle.addSilently(level, fresh);
        manager.unbind(resident.id());
        old.discard();
        manager.bind(resident.id(), fresh.getUUID());
        Placitum.LOGGER.debug("Swapped {} to {}", resident.lineage().fullName(),
                fresh.getType().getDescriptionId());
    }
}
