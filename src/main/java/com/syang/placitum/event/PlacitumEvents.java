package com.syang.placitum.event;

import com.syang.placitum.Placitum;
import com.syang.placitum.build.SettlementTick;
import com.syang.placitum.command.PlacitumCommand;
import com.syang.placitum.command.SettlementReport;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.SettlementId;
import com.syang.placitum.settlement.Registration;
import com.syang.placitum.store.SettlementManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.BellBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The server-side hooks, which are now almost nothing.
 *
 * <p>There used to be handlers here for villager death, breeding, conversion, arrival and
 * profession changes, because the mod kept its own copy of every villager. It does not any more,
 * so vanilla is left to run its own villagers and this is a tick loop and a command registration.
 */
@EventBusSubscriber(modid = Placitum.MODID)
public final class PlacitumEvents {

    private PlacitumEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        PlacitumCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    /**
     * Builds, for every settlement whose ground is loaded.
     *
     * <p>Loaded is the whole condition. There is no virtual half to fall back on, so a village
     * nobody visits does not grow - which is the trade that removes the second code path and
     * the six bugs that lived on it.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        SettlementManager manager = SettlementManager.peek();
        if (manager == null) {
            return;
        }
        for (SettlementId entry : manager.listed()) {
            ServerLevel level = event.getServer().getLevel(entry.dimension());
            if (level == null || !level.isLoaded(entry.center())) {
                continue;
            }
            Settlement settlement = manager.find(entry.id()).orElse(null);
            if (settlement == null) {
                continue;
            }
            Settlement after = SettlementTick.run(level, settlement);
            if (after != settlement) {
                manager.put(after);
            }
        }
    }

    /**
     * Shift-right-click a bell to register the village around it, or to ask about it.
     *
     * <p>The one interaction this mod adds to the world. Plain right-click is left alone: it
     * rings the bell, the way it always did, and nothing in this version has anything to say
     * about a bell being rung.
     *
     * <p>Shift-clicking a bell inside a town is not a failed second registration but the obvious
     * other question - "what is this place doing" - and answering it there saves a player having
     * to learn an id to run a command with. Any bell in the claim will do, so a bell hung for
     * decoration is a noticeboard rather than a refusal.
     *
     * <p>The bell it was registered at is not special afterwards, and can be broken: the town
     * plan is arithmetic on a position, not on a block. What cannot move is that position, since
     * every road, lot, lamp and rampart is measured from it.
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
        if (!(level.getBlockState(pos).getBlock() instanceof BellBlock)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        SettlementManager manager = SettlementManager.get(level.getServer());

        // Any bell inside a town's claim, not only the one it was registered at. Bells are
        // decoration as much as anything, and a player who hangs one in their market square got
        // "Too close to Achenstead - 40 blocks away, 96 required" - which is true, reads as a
        // failure, and is not what they asked. What they asked is "what is this place".
        Settlement here = null;
        int claim = PlacitumConfig.CLAIM_RADIUS_CHUNKS.get() * 16;
        for (Settlement existing : manager.all()) {
            if (existing.dimension().equals(level.dimension())
                    && existing.center().distSqr(pos) <= (long) claim * claim) {
                here = existing;
                break;
            }
        }
        if (here != null) {
            SettlementReport.of(here, level).forEach(player::sendSystemMessage);
            return;
        }
        player.sendSystemMessage(describe(Registration.register(level, manager, pos)));
    }

    private static Component describe(Registration.Result result) {
        return switch (result) {
            case Registration.Result.Success success -> Component
                    .translatable("placitum.bell.registered", success.settlement().name())
                    .withStyle(ChatFormatting.GREEN);
            case Registration.Result.AlreadyRegistered already -> Component
                    .translatable("placitum.bell.already_registered", already.name())
                    .withStyle(ChatFormatting.YELLOW);
            case Registration.Result.Overlaps overlaps -> Component
                    .translatable("placitum.bell.too_close", overlaps.otherName(),
                            overlaps.distance(), overlaps.required())
                    .withStyle(ChatFormatting.RED);
        };
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        SettlementManager.get(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SettlementManager.clear();
    }
}
