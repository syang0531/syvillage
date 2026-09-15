package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;

/**
 * The truth about one villager. The entity is only a view of this.
 *
 * <p>There is no {@code entityId} field on purpose. Storing one would invert principle 1 and
 * make the data reference the entity; the binding lives in a runtime-only map on
 * SettlementManager instead.
 *
 * <p>{@code offers} is the single exception to principle 1, and it is deliberately a raw
 * {@link CompoundTag} rather than a typed MerchantOffers. The simulation never reads it; the
 * only code touching it copies it out on demote and back in on promote.
 *
 * <p>Keeping it untyped is also what makes it testable. Since 26.2, item data components bind
 * during datapack reload rather than registry bootstrap, so an ItemStack - and therefore a
 * MerchantOffer - cannot be constructed in a unit test at all. A field the round-trip test
 * cannot cover is a field that silently rots.
 */
public record Resident(
        UUID id,
        Lineage lineage,
        LifeStage stage,
        int ageDays,
        Assignment assignment,
        Vitals vitals,
        boolean militiaEligible,
        boolean zombified,
        GearSet gear,
        ResidentTask task,
        BlockPos coarsePos,
        ResidentState state,
        CompoundTag offers) {

    public static final Codec<Resident> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Resident::id),
            Lineage.CODEC.fieldOf("lineage").forGetter(Resident::lineage),
            LifeStage.CODEC.fieldOf("stage").forGetter(Resident::stage),
            Codec.INT.fieldOf("age_days").forGetter(Resident::ageDays),
            Assignment.CODEC.fieldOf("assignment").forGetter(Resident::assignment),
            Vitals.CODEC.fieldOf("vitals").forGetter(Resident::vitals),
            Codec.BOOL.fieldOf("militia_eligible").forGetter(Resident::militiaEligible),
            Codec.BOOL.fieldOf("zombified").forGetter(Resident::zombified),
            GearSet.CODEC.fieldOf("gear").forGetter(Resident::gear),
            ResidentTask.CODEC.fieldOf("task").forGetter(Resident::task),
            BlockPos.CODEC.fieldOf("coarse_pos").forGetter(Resident::coarsePos),
            ResidentState.CODEC.fieldOf("state").forGetter(Resident::state),
            CompoundTag.CODEC.fieldOf("offers").forGetter(Resident::offers)
    ).apply(i, Resident::new));

    public Resident withState(ResidentState newState) {
        return new Resident(id, lineage, stage, ageDays, assignment, vitals, militiaEligible,
                zombified, gear, task, coarsePos, newState, offers);
    }

    public Resident withVitals(Vitals newVitals) {
        return new Resident(id, lineage, stage, ageDays, assignment, newVitals,
                militiaEligible, zombified, gear, task, coarsePos, state, offers);
    }

    public Resident withAssignment(Assignment newAssignment) {
        return new Resident(id, lineage, stage, ageDays, newAssignment, vitals, militiaEligible,
                zombified, gear, task, coarsePos, state, offers);
    }

    public Resident withCoarsePos(BlockPos pos) {
        return new Resident(id, lineage, stage, ageDays, assignment, vitals, militiaEligible,
                zombified, gear, task, pos, state, offers);
    }

    public Resident withOffers(CompoundTag newOffers) {
        return new Resident(id, lineage, stage, ageDays, assignment, vitals, militiaEligible,
                zombified, gear, task, coarsePos, state, newOffers);
    }

    public boolean materialized() {
        return state == ResidentState.MATERIALIZED;
    }

    /** Counts towards population and carrying capacity. Zombified residents do not. */
    public boolean counts() {
        return !zombified;
    }
}
