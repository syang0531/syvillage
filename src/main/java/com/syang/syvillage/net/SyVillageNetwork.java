package com.syang.syvillage.net;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
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
    }
}
