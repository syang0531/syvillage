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
 * The outline one player is being shown, on its way to that player's client.
 *
 * <p>The preview is worked out on the server - it is the server that knows what is in the way -
 * but a line has to be drawn by a client. So the geometry crosses, and nothing else does: no
 * template, no palette, no placement. The client is told which columns to draw a border round
 * and which blocks to ring in red, and it does not need to know what a blueprint is.
 *
 * <p>Sent once when the outline appears and once when it goes, rather than every few ticks. The
 * client redraws from what it holds, so the wire is quiet while somebody stands and looks.
 *
 * <p>An empty {@link #columns} means "nothing to draw" - that is how an outline is taken away.
 *
 * @param origin  the box's lowest corner; its y is the floor the structure sits on
 * @param columns occupied columns as {@code (dx << 8) | dz} from the origin, so a box may be up
 *                to 256 on a side - four times anything vanilla ever saved
 * @param blocked the positions something is already standing in, capped by the sender
 */
public record PreviewOutline(BlockPos origin, int width, int height, int depth,
        List<Integer> columns, List<BlockPos> blocked, boolean buildable)
        implements CustomPacketPayload {

    public static final Type<PreviewOutline> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SyVillage.MODID, "preview_outline"));

    /** Take the outline away. */
    public static PreviewOutline none() {
        return new PreviewOutline(BlockPos.ZERO, 0, 0, 0, List.of(), List.of(), false);
    }

    public boolean empty() {
        return columns.isEmpty();
    }

    /**
     * Written by hand rather than composed.
     *
     * <p>{@code StreamCodec.composite} runs out of arities before it runs out of fields here,
     * and a hand-written pair of lambdas is shorter than the shape it would take to fit.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, PreviewOutline> CODEC =
            StreamCodec.of(PreviewOutline::write, PreviewOutline::read);

    private static void write(RegistryFriendlyByteBuf buf, PreviewOutline outline) {
        buf.writeBlockPos(outline.origin());
        buf.writeVarInt(outline.width());
        buf.writeVarInt(outline.height());
        buf.writeVarInt(outline.depth());
        buf.writeVarInt(outline.columns().size());
        for (int column : outline.columns()) {
            buf.writeShort(column);
        }
        buf.writeVarInt(outline.blocked().size());
        for (BlockPos pos : outline.blocked()) {
            buf.writeBlockPos(pos);
        }
        buf.writeBoolean(outline.buildable());
    }

    private static PreviewOutline read(RegistryFriendlyByteBuf buf) {
        BlockPos origin = buf.readBlockPos();
        int width = buf.readVarInt();
        int height = buf.readVarInt();
        int depth = buf.readVarInt();
        int columnCount = buf.readVarInt();
        List<Integer> columns = new ArrayList<>(columnCount);
        for (int i = 0; i < columnCount; i++) {
            columns.add(buf.readShort() & 0xFFFF);
        }
        int blockedCount = buf.readVarInt();
        List<BlockPos> blocked = new ArrayList<>(blockedCount);
        for (int i = 0; i < blockedCount; i++) {
            blocked.add(buf.readBlockPos());
        }
        return new PreviewOutline(origin, width, height, depth, List.copyOf(columns),
                List.copyOf(blocked), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
