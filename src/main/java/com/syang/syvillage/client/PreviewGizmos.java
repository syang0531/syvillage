package com.syang.syvillage.client;

import com.syang.syvillage.SyVillage;
import com.syang.syvillage.block.DraftingBoards;
import com.syang.syvillage.block.DraftingTableEntity;
import com.syang.syvillage.build.Outline;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Drawing the outline, with the lines the game draws a structure block's box with.
 *
 * <p>This was dust particles first, and dust cannot draw a line: it scatters with distance, its
 * width cannot be set, and a footprint's worth of it reads as fog over the very ground the
 * player is being asked to judge. That was tried in a real world, which is the condition
 * CLAUDE.md put on opening a client package at all.
 *
 * <p>Three shapes, and each answers a different question.
 *
 * <ul>
 *   <li><b>The white box</b> - how much room the drawing needs.
 *   <li><b>The border</b>, green or red, round the columns that get blocks - where it sits, and
 *       whether it can.
 *   <li><b>The massing</b>, one translucent bar per column from its lowest block to its highest -
 *       <em>what</em> is going to be there. A border on the ground tells somebody who has seen
 *       the building where it goes; it tells somebody who has not seen it nothing at all, and
 *       that was the complaint. One bar per column is two hundred and twenty-one shapes rather
 *       than the twelve hundred a block-by-block ghost would be, and the silhouette is the part
 *       that carries the answer.
 * </ul>
 */
@EventBusSubscriber(modid = SyVillage.MODID, value = Dist.CLIENT)
public final class PreviewGizmos {

    private PreviewGizmos() {}

    /** White, and the same whether the site is good or bad: the box is geometry, not a verdict. */
    private static final int BOX = 0xFFE8EDF2;
    private static final int READY = 0xFF4CC26A;
    private static final int BLOCKED = 0xFFD9483B;
    /** The massing. Alpha low enough to see the ground and the building behind it through. */
    private static final int MASS_READY = 0x3348C46A;
    private static final int MASS_BLOCKED = 0x33D9483B;

    private static final float BOX_WIDTH = 2.0f;
    private static final float BORDER_WIDTH = 3.5f;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null || DraftingBoards.loaded().isEmpty()) {
            return;
        }
        for (DraftingTableEntity board : DraftingBoards.loaded()) {
            Outline outline = board.outline();
            if (!outline.empty()) {
                draw(outline);
            }
        }
    }

    private static void draw(Outline outline) {
        BlockPos corner = outline.corner();
        Gizmos.cuboid(new AABB(corner.getX(), corner.getY(), corner.getZ(),
                        corner.getX() + outline.width(), corner.getY() + outline.height(),
                        corner.getZ() + outline.depth()),
                GizmoStyle.stroke(BOX, BOX_WIDTH)).setAlwaysOnTop();

        boolean ok = outline.buildable();
        massAndBorder(outline, ok);

        int[] blocked = outline.blocked();
        for (int i = 0; i + 2 < blocked.length; i += 3) {
            Gizmos.cuboid(new AABB(new BlockPos(blocked[i], blocked[i + 1], blocked[i + 2])),
                    GizmoStyle.stroke(BLOCKED, BOX_WIDTH)).setAlwaysOnTop();
        }
    }

    /**
     * One pass over the columns: a translucent bar for each, and a line along every side whose
     * neighbour is empty.
     *
     * <p>The border is the edge of the occupied set, not of the box, which is what makes a
     * gatehouse read as a gatehouse rather than as a twenty-five by nine rectangle. Same
     * distinction as a footprint not being a bounding box.
     */
    private static void massAndBorder(Outline outline, boolean ok) {
        int width = outline.width();
        int depth = outline.depth();
        boolean[][] filled = new boolean[width][depth];
        int[][] spans = new int[width][depth];
        for (int i = 0; i < outline.columns().length; i++) {
            int packed = outline.columns()[i];
            int dx = packed >> 8;
            int dz = packed & 0xFF;
            filled[dx][dz] = true;
            spans[dx][dz] = outline.spans()[i];
        }
        int border = ok ? READY : BLOCKED;
        GizmoStyle mass = GizmoStyle.fill(ok ? MASS_READY : MASS_BLOCKED);
        double y = outline.corner().getY();

        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                if (!filled[dx][dz]) {
                    continue;
                }
                double x = outline.corner().getX() + dx;
                double z = outline.corner().getZ() + dz;
                int bottom = spans[dx][dz] >> 8;
                int top = spans[dx][dz] & 0xFF;
                Gizmos.cuboid(new AABB(x, y + bottom, z, x + 1, y + top + 1, z + 1), mass);

                if (!at(filled, dx, dz - 1)) {
                    line(x, y, z, x + 1, y, z, border);
                }
                if (!at(filled, dx, dz + 1)) {
                    line(x, y, z + 1, x + 1, y, z + 1, border);
                }
                if (!at(filled, dx - 1, dz)) {
                    line(x, y, z, x, y, z + 1, border);
                }
                if (!at(filled, dx + 1, dz)) {
                    line(x + 1, y, z, x + 1, y, z + 1, border);
                }
            }
        }
    }

    private static boolean at(boolean[][] filled, int dx, int dz) {
        return dx >= 0 && dz >= 0 && dx < filled.length && dz < filled[0].length && filled[dx][dz];
    }

    private static void line(double x0, double y0, double z0, double x1, double y1, double z1,
            int colour) {
        Gizmos.line(new Vec3(x0, y0 + 0.02, z0), new Vec3(x1, y1 + 0.02, z1), colour,
                BORDER_WIDTH).setAlwaysOnTop();
    }
}
