package com.syang.syvillage.net;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The one thing this mod sends over the wire.
 *
 * <p>An outline, to the one player looking at it. Nothing is sent to anybody else, nothing comes
 * back, and nothing is kept at either end beyond the few seconds the outline lives - which keeps
 * the mod's promise of holding no state even though a packet now exists.
 *
 * <p>Optional on purpose. A client without the mod simply never receives it, and the only thing
 * it loses is the preview; blueprints still refuse and still build, because both of those are
 * decided on the server.
 */
public final class SyVillageNetwork {

    private SyVillageNetwork() {}

    /** Listens on the mod bus, alongside the registers, the way the creative tabs do. */
    public static void register(IEventBus modBus) {
        modBus.addListener(SyVillageNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(PreviewOutline.TYPE, PreviewOutline.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    // Named inside the branch, not at the top of the file: a dedicated server
                    // must never load a class that imports the client.
                    if (FMLEnvironment.getDist().isClient()) {
                        com.syang.syvillage.client.PreviewGizmos.set(payload);
                    }
                }));
    }

    /** Show this player an outline, or take theirs away. */
    public static void send(ServerPlayer player, PreviewOutline outline) {
        PacketDistributor.sendToPlayer(player, outline);
    }

}
