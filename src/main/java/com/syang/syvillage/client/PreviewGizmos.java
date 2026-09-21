package com.syang.syvillage.client;

import com.syang.syvillage.SyVillage;
import com.syang.syvillage.net.PreviewOutline;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Drawing the outline, with the lines the game draws a structure block's box with.
 *
 * <p>This was dust particles first, and dust cannot draw a line. It scatters with distance, it
 * cannot be made thin, and a footprint's worth of it reads as green fog over the very ground the
 * player is being asked to judge. That was tried in a real world before this was written, which
 * is the condition CLAUDE.md put on opening a client package at all.
 *
 * <p>26.2's gizmos do it properly: a cuboid with a stroke, a line with a width, drawn on top of
 * the world. Two shapes carry the whole answer - <b>the box</b> says how much room the drawing
 * needs, and <b>the border round the occupied columns</b> says what will actually stand there,
 * in green or red depending on whether it can.
 *
 * <p>Nothing here is persistent and nothing here is authoritative. The server decides what can
 * be built; this only says so out loud. A client without the mod sees no outline and can still
 * place a blueprint, because both clicks are handled on the other side.
 */
@EventBusSubscriber(modid = SyVillage.MODID, value = Dist.CLIENT)
public final class PreviewGizmos {

    private PreviewGizmos() {}

    /** White, and the same whether the site is good or bad: the box is geometry, not a verdict. */
    private static final int BOX = 0xFFE8EDF2;
    private static final int READY = 0xFF4CC26A;
    private static final int BLOCKED = 0xFFD9483B;

    private static final float BOX_WIDTH = 2.0f;
    private static final float BORDER_WIDTH = 3.5f;

    private static @Nullable PreviewOutline current;

    /** Called from the packet handler. An empty outline means the player is no longer shown one. */
    public static void set(PreviewOutline outline) {
        current = outline.empty() ? null : outline;
    }

    /**
     * Re-submit the shapes every client tick.
     *
     * <p>Gizmos are drained each tick, so an outline that should stay up has to be handed over
     * again. This is the flourish tick's client half: it reads nothing, holds one object, and
     * stops the moment the server says the outline is gone.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        PreviewOutline outline = current;
        if (outline == null) {
            return;
        }
        BlockPos origin = outline.origin();
        Gizmos.cuboid(new AABB(origin.getX(), origin.getY(), origin.getZ(),
                        origin.getX() + outline.width(), origin.getY() + outline.height(),
                        origin.getZ() + outline.depth()),
                GizmoStyle.stroke(BOX, BOX_WIDTH)).setAlwaysOnTop();

        border(outline, outline.buildable() ? READY : BLOCKED);

        for (BlockPos pos : outline.blocked()) {
            Gizmos.cuboid(new AABB(pos), GizmoStyle.stroke(BLOCKED, BOX_WIDTH)).setAlwaysOnTop();
        }
    }

    /**
     * A line round the edge of the occupied columns, at floor level.
     *
     * <p>The edge of the set, not of the box: a side is drawn where the column beside it is not
     * occupied. That makes a gatehouse read as a gatehouse rather than as a twenty-five by nine
     * rectangle, and it is the same distinction as a footprint not being a bounding box.
     */
    private static void border(PreviewOutline outline, int colour) {
        boolean[][] filled = new boolean[outline.width()][outline.depth()];
        for (int packed : outline.columns()) {
            filled[packed >> 8][packed & 0xFF] = true;
        }
        double y = outline.origin().getY() + 0.02;   // just clear of the floor, so it is not in it
        for (int dx = 0; dx < outline.width(); dx++) {
            for (int dz = 0; dz < outline.depth(); dz++) {
                if (!filled[dx][dz]) {
                    continue;
                }
                double x = outline.origin().getX() + dx;
                double z = outline.origin().getZ() + dz;
                if (!at(filled, dx, dz - 1)) {
                    line(x, y, z, x + 1, y, z, colour);
                }
                if (!at(filled, dx, dz + 1)) {
                    line(x, y, z + 1, x + 1, y, z + 1, colour);
                }
                if (!at(filled, dx - 1, dz)) {
                    line(x, y, z, x, y, z + 1, colour);
                }
                if (!at(filled, dx + 1, dz)) {
                    line(x + 1, y, z, x + 1, y, z + 1, colour);
                }
            }
        }
    }

    private static boolean at(boolean[][] filled, int dx, int dz) {
        return dx >= 0 && dz >= 0 && dx < filled.length && dz < filled[0].length && filled[dx][dz];
    }

    private static void line(double x0, double y0, double z0, double x1, double y1, double z1,
            int colour) {
        Gizmos.line(new Vec3(x0, y0, z0), new Vec3(x1, y1, z1), colour, BORDER_WIDTH)
                .setAlwaysOnTop();
    }
}
