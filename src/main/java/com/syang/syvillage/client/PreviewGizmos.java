package com.syang.syvillage.client;

import com.syang.syvillage.SyVillage;
import com.syang.syvillage.block.DraftingBoards;
import com.syang.syvillage.block.DraftingTableEntity;
import com.syang.syvillage.build.Outline;
import com.syang.syvillage.config.SyVillageClientConfig;
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
 * <p>Four shapes, and each answers a different question.
 *
 * <ul>
 *   <li><b>The white box</b> - how much room the drawing needs.
 *   <li><b>The border</b>, green or red, round the columns that get blocks - where it sits, and
 *       whether it can go there.
 *   <li><b>The massing</b>, one translucent bar per column from its lowest block to its highest -
 *       <em>what</em> is going to be there. A border on the ground tells somebody who has seen
 *       this building where it goes and tells somebody who has not seen it nothing at all, which
 *       was the complaint. 221 bars rather than the 1209 boxes a block-by-block ghost would be;
 *       the silhouette is the part that carries the answer.
 *   <li><b>The marks</b> - a red cube where something already stands in the way, an amber one
 *       where there is a hole under the floor.
 * </ul>
 *
 * <p><b>Colour goes on the marks, not on the massing.</b> Washing the whole silhouette red
 * filled the screen with it and from inside the footprint it was a wall. The handful of blocks
 * that carry a problem are each somewhere to walk to; the two thousand that are merely going to
 * be there do not each need to shout. How far you see through either is
 * {@link SyVillageClientConfig}, which the board's own sliders write.
 */
@EventBusSubscriber(modid = SyVillage.MODID, value = Dist.CLIENT)
public final class PreviewGizmos {

    private PreviewGizmos() {}

    /** White, and the same whether the site is good or bad: the box is geometry, not a verdict. */
    private static final int BOX = 0xE8EDF2;
    private static final int READY = 0x4CC26A;
    private static final int BLOCKED = 0xD9483B;
    private static final int UNSUPPORTED = 0xE0A83B;

    private static final float BOX_WIDTH = 2.0f;
    private static final float BORDER_WIDTH = 3.5f;

    /** Leaving a world: nothing loaded there is loaded any more. */
    @SubscribeEvent
    public static void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            DraftingBoards.clear();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null || DraftingBoards.loaded().isEmpty()) {
            return;
        }
        for (DraftingTableEntity board : DraftingBoards.loaded()) {
            // A broken table's block entity is removed, and a removed one draws nothing. Asked
            // here as well as on removal because an outline that outlives its table is the one
            // failure a player cannot clear by any means at all.
            if (board.isRemoved()) {
                continue;
            }
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
                GizmoStyle.stroke(opaque(BOX), BOX_WIDTH)).setAlwaysOnTop();

        massAndBorder(outline);

        int marks = SyVillageClientConfig.MARK_OPACITY.get();
        marks(outline.blocked(), BLOCKED, marks);
        marks(outline.unsupported(), UNSUPPORTED, marks);
    }

    /**
     * The blocks that have something to say, one cube each.
     *
     * <p>Red is something standing where the building goes; amber is a hole under its floor.
     * Both are a handful of blocks the player can walk to, which is why they are filled and the
     * massing is not.
     */
    private static void marks(int[] positions, int colour, int alpha) {
        // Outline and fill on the same slider. An outline that stayed solid while the fill
        // faded meant the slider never actually turned anything off.
        GizmoStyle style = GizmoStyle.strokeAndFill(tint(colour, alpha), BOX_WIDTH,
                tint(colour, alpha / 2));
        for (int i = 0; i + 2 < positions.length; i += 3) {
            Gizmos.cuboid(new AABB(new BlockPos(positions[i], positions[i + 1],
                    positions[i + 2])), style).setAlwaysOnTop();
        }
    }

    /**
     * One pass over the columns: a translucent bar for each, and a line along every side whose
     * neighbour is empty.
     *
     * <p>The massing is the building, so it wears the building's colour whether the ground suits
     * it or not. Only the border carries the verdict, because the border answers "can this go
     * here" and the silhouette answers "what is this".
     *
     * <p>The border is the edge of the occupied set, not of the box, which is what makes a
     * gatehouse read as a gatehouse rather than as a twenty-five by nine rectangle. Same
     * distinction as a footprint not being a bounding box.
     */
    private static void massAndBorder(Outline outline) {
        int width = outline.width();
        int depth = outline.depth();
        boolean[][] filled = new boolean[width][depth];
        int[][] spans = new int[width][depth];
        for (int i = 0; i < outline.columns().length; i++) {
            int packed = outline.columns()[i];
            filled[packed >> 8][packed & 0xFF] = true;
            spans[packed >> 8][packed & 0xFF] = outline.spans()[i];
        }
        int border = opaque(outline.buildable() ? READY : BLOCKED);
        int alpha = SyVillageClientConfig.MASS_OPACITY.get();
        GizmoStyle mass = GizmoStyle.fill(tint(READY, alpha));
        double y = outline.corner().getY();

        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                if (!filled[dx][dz]) {
                    continue;
                }
                double x = outline.corner().getX() + dx;
                double z = outline.corner().getZ() + dz;
                if (alpha > 0) {
                    int bottom = spans[dx][dz] >> 8;
                    int top = spans[dx][dz] & 0xFF;
                    Gizmos.cuboid(new AABB(x, y + bottom, z, x + 1, y + top + 1, z + 1), mass);
                }
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

    private static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }

    private static int tint(int rgb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | rgb;
    }
}
