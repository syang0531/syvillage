package com.syang.syvillage.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * A villager, on paper. Used on a block it becomes one, the way a spawn egg does.
 *
 * <p>Sold by the village head, and what it is for is the second village: a bell rung in an empty
 * place registers a settlement that lights its streets and then waits, because nobody is there
 * to take the head's job. A charter carried from the first village is the family that moves.
 *
 * <p>It is a vanilla villager in every respect - {@link Villager#finalizeSpawn} gives it the
 * look of the biome it is set down in, as an egg does - and it is priced and rationed like a
 * rare thing, because population is meant to be waited for, not bought. This is a family now
 * and then, not a factory.
 */
public final class FreemansCharter extends Item {

    public FreemansCharter(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        // Where the egg puts it: in the clicked block if there is room, else on the clicked
        // face - with the same nudge down off a block's top so it lands on it, not above it.
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        BlockPos at = state.getCollisionShape(level, pos).isEmpty()
                ? pos : pos.relative(context.getClickedFace());
        boolean movedUp = !at.equals(pos) && context.getClickedFace() == Direction.UP;
        ItemStack stack = context.getItemInHand();
        Villager villager = EntityTypes.VILLAGER.spawn(serverLevel, stack, context.getPlayer(), at,
                EntitySpawnReason.SPAWN_ITEM_USE, true, movedUp);
        if (villager == null) {
            return InteractionResult.FAIL;
        }
        stack.consume(1, context.getPlayer());
        serverLevel.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, at);
        return InteractionResult.SUCCESS;
    }
}
