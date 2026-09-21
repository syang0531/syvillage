package com.syang.syvillage.net;

import com.syang.syvillage.build.Dark;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The one thing this mod sends over the wire.
 *
 * <p>One payload, and it goes <em>to</em> the server: a button on a drawing board, pressed. What
 * the client knows about a table it learns from the block entity's own sync, which vanilla was
 * already doing, so there is nothing to send the other way.
 *
 * <p>Optional on purpose. A client without the mod cannot open the board and cannot press
 * anything, and that is all it loses - a table still holds its drawing, and anything already
 * building still builds, because the server decides both.
 */
public final class SyVillageNetwork {

    private SyVillageNetwork() {}

    /** Listens on the mod bus, alongside the registers, the way the creative tabs do. */
    public static void register(IEventBus modBus) {
        modBus.addListener(SyVillageNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(DraftingCommand.TYPE, DraftingCommand.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        DraftingCommand.apply(payload, player);
                    }
                }));
        registrar.playToClient(DarkMarks.TYPE, DarkMarks.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    // Named inside the branch, not at the top of the file: a dedicated server
                    // must never load a class that imports the client.
                    if (FMLEnvironment.getDist().isClient()) {
                        com.syang.syvillage.client.DarkGizmos.show(payload.marks(),
                                payload.lights());
                    }
                }));
    }

    /**
     * Answer somebody who asked where it is still dark.
     *
     * <p>The count goes in chat because that is the part that matters - nought means finished,
     * and nothing else in the game will ever tell you that. The places go as marks because a
     * number alone is not somewhere you can walk to.
     */
    public static void survey(ServerPlayer player, BlockPos centre) {
        Dark.Survey survey = Dark.read((ServerLevel) player.level(), centre);
        PacketDistributor.sendToPlayer(player,
                new DarkMarks(survey.marks(), survey.lights()));
        player.sendSystemMessage(describe(survey));
    }

    private static Component describe(Dark.Survey survey) {
        if (survey.walked() == 0) {
            return Component.translatable("syvillage.dark.nowhere")
                    .withStyle(ChatFormatting.GRAY);
        }
        if (survey.safe()) {
            return Component.translatable("syvillage.dark.none", survey.walked())
                    .withStyle(ChatFormatting.GREEN);
        }
        // The number of lights leads, because it is the one somebody can finish. The dark
        // columns are the scale of it, and on their own they read as hopeless: two thousand
        // places is nobody's evening, and it is five lanterns.
        return Component.translatable(survey.aroundVillage()
                        ? "syvillage.dark.found" : "syvillage.dark.found_here",
                        survey.lights().size(), survey.count())
                .withStyle(ChatFormatting.LIGHT_PURPLE);
    }
}
