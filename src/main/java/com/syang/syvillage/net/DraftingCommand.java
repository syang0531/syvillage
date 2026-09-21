package com.syang.syvillage.net;

import com.syang.syvillage.SyVillage;
import com.syang.syvillage.block.DraftingTableEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * A button on the drawing board, pressed.
 *
 * <p>The only thing that travels the other way. Everything the client knows about a table it
 * learns from the block entity's own sync, so this carries a position, which button, and the
 * three numbers the offset boxes hold - and nothing else. The server does the deciding, as it
 * does for everything else in this mod.
 *
 * @param action which button; {@link #MOVE} is the only one that reads the offset
 */
public record DraftingCommand(BlockPos table, int action, BlockPos offset)
        implements CustomPacketPayload {

    public static final int TURN = 0;
    public static final int MOVE = 1;
    public static final int SHOW = 2;
    public static final int BUILD = 3;

    public static final Type<DraftingCommand> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SyVillage.MODID, "drafting_command"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DraftingCommand> CODEC =
            StreamCodec.of((buf, it) -> {
                buf.writeBlockPos(it.table());
                buf.writeVarInt(it.action());
                buf.writeBlockPos(it.offset());
            }, buf -> new DraftingCommand(buf.readBlockPos(), buf.readVarInt(),
                    buf.readBlockPos()));

    /**
     * Do it, if the player is actually standing at that table.
     *
     * <p>Reach is checked rather than trusted. A packet is a thing anybody can send, and "the
     * client asked nicely" is not a reason to lay two thousand blocks in somebody else's town.
     */
    public static void apply(DraftingCommand command, ServerPlayer player) {
        if (!player.level().isLoaded(command.table())
                || player.distanceToSqr(command.table().getX() + 0.5,
                        command.table().getY() + 0.5, command.table().getZ() + 0.5) > 64.0
                || !(player.level().getBlockEntity(command.table())
                        instanceof DraftingTableEntity board)) {
            return;
        }
        switch (command.action()) {
            case TURN -> board.turn();
            case MOVE -> board.moveTo(command.offset());
            case SHOW -> board.toggleShowing();
            case BUILD -> board.build();
            default -> { }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
