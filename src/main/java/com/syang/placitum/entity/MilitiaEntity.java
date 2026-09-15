package com.syang.placitum.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * An armed villager.
 *
 * <p>Vanilla villagers cannot fight. Their PANIC activity is wired to flee on HURT_BY and they
 * have no attack damage attribute at all, so making one fight means replacing its brain - and
 * docs/defense.md rules out Mixin for that, because brain registries move every version.
 *
 * <p>Since the {@code Resident} record is the truth and the entity only a view, the cheaper
 * answer is to swap the view. This subclasses {@link Villager} rather than building a mob from
 * scratch for two practical reasons: it renders with the vanilla villager model, professions
 * and clothes included, so a mustered farmer still looks like that farmer; and the swap keeps
 * every villager field intact by construction.
 *
 * <p>The brain is switched off by overriding {@link #customServerAiStep}, which is where
 * Villager ticks it. Goals drive this one instead. That is subclassing, not Mixin - no vanilla
 * registry is touched and nothing breaks when Mojang reshuffles behaviours.
 */
public class MilitiaEntity extends Villager {

    public MilitiaEntity(EntityType<? extends Villager> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Villager.createAttributes()
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.55);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(2, new MoveTowardsTargetGoal(this, 0.9, 32.0F));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.5));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true));
    }

    /**
     * Deliberately empty.
     *
     * <p>Villager spends this method ticking its brain, running its schedule and looking for
     * work. A mustered militia does none of that - it fights and then stands down. Leaving the
     * brain running would have it wandering off to its composter mid-raid.
     */
    @Override
    protected void customServerAiStep(ServerLevel level) {
        // no brain
    }

    /** Not open for business while under arms. */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;   // demote owns removal, not despawning
    }
}
