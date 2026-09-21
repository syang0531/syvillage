package com.syang.syvillage.net;

import com.syang.syvillage.SyVillage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The places a monster can still stand up in, on their way to the one player who asked.
 *
 * <p>Unlike a drawing's outline, this belongs to nobody and nothing: it is the answer to a
 * question somebody asked once, so it goes to them, is drawn for a few seconds and forgotten.
 * Nothing holds it at either end - which is why it is a packet and not a block entity.
 *
 * <p>That is also the right shape for what it is. An outline that stayed up would be the grid
 * again: a village permanently painted in warnings, and the player back to reading a pattern
 * instead of the ground.
 *
 * @param marks  where a monster could stand up
 * @param lights where a light would cover the most of them - advice, not placement
 */
public record DarkMarks(List<BlockPos> marks, List<BlockPos> lights)
        implements CustomPacketPayload {

    public static final Type<DarkMarks> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SyVillage.MODID, "dark_marks"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DarkMarks> CODEC =
            StreamCodec.of((buf, it) -> {
                write(buf, it.marks());
                write(buf, it.lights());
            }, buf -> new DarkMarks(read(buf), read(buf)));

    private static void write(RegistryFriendlyByteBuf buf, List<BlockPos> positions) {
        buf.writeVarInt(positions.size());
        for (BlockPos pos : positions) {
            buf.writeBlockPos(pos);
        }
    }

    private static List<BlockPos> read(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<BlockPos> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(buf.readBlockPos());
        }
        return List.copyOf(out);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
