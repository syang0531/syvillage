package com.syang.syvillage.event;

import com.syang.syvillage.SyVillage;
import com.syang.syvillage.build.Raise;
import com.syang.syvillage.net.SyVillageNetwork;
import com.syang.syvillage.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.BellBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The server-side hooks: a flourish, and the one question the world can be asked.
 *
 * <p>CLAUDE.md forbids a tick that walks the world looking for work, and the distinction is
 * worth keeping in view: the flourish is bounded by what a player started seconds ago, reads
 * nothing, saves nothing, and finishes rather than resumes when interrupted.
 *
 * <p>The survey is not a tick at all. Somebody asks, it answers.
 */
@EventBusSubscriber(modid = SyVillage.MODID)
public final class SyVillageEvents {

    private SyVillageEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Raise.tick();
    }

    /** Half a tower is worse than a whole one. Whatever is still going up, finish it. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        Raise.finishAll();
    }

    /**
     * Crouch and use a bell, or the guardian statue, to ask where it is still dark.
     *
     * <p>Two things to ask because they are the two things a village has that are about the
     * village rather than about a building. A plain right-click on a bell still rings it: the
     * gesture this mod adds is the one vanilla left spare.
     *
     * <p>Nothing is registered, claimed or remembered by asking. Move the bell, break it, hang
     * three - the answer is worked out where you stand at the moment you ask.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)
                || !player.isShiftKeyDown()) {
            return;
        }
        BlockPos pos = event.getPos();
        boolean asked = level.getBlockState(pos).getBlock() instanceof BellBlock
                || level.getBlockState(pos).is(ModBlocks.GUARDIAN_STATUE.get());
        if (!asked) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        SyVillageNetwork.survey(player, pos);
    }
}
