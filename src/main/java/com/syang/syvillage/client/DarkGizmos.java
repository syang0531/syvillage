package com.syang.syvillage.client;

import com.syang.syvillage.SyVillage;
import com.syang.syvillage.config.SyVillageClientConfig;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The dark places, shown for as long as it takes to walk out and look at one.
 *
 * <p>Deliberately not permanent. A village standing in a permanent field of warnings is the
 * lamp grid again with the colours swapped - something to read a pattern off instead of ground
 * to look at. This is an answer to a question, so it fades, and asking again is one click.
 *
 * <p>Drawn at the block a mob would stand in rather than on the ground under it, because that
 * is what the survey measured and what a lantern has to reach.
 */
@EventBusSubscriber(modid = SyVillage.MODID, value = Dist.CLIENT)
public final class DarkGizmos {

    private DarkGizmos() {}

    /** The colour of a place nothing has lit. */
    private static final int DARK = 0x7A5CC8;

    /** How long the answer stays up. Long enough to walk to the far side of a village. */
    private static final int TICKS = 30 * 20;

    private static List<BlockPos> marks = List.of();
    private static int left;

    /** Called from the packet handler. A fresh answer replaces the last one. */
    public static void show(List<BlockPos> shown) {
        marks = shown;
        left = shown.isEmpty() ? 0 : TICKS;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (left <= 0 || Minecraft.getInstance().level == null) {
            return;
        }
        left--;
        int alpha = SyVillageClientConfig.MARK_OPACITY.get();
        GizmoStyle style = GizmoStyle.strokeAndFill(0xFF000000 | DARK, 1.5f,
                (Math.clamp(alpha, 0, 255) << 24) | DARK);
        for (BlockPos pos : marks) {
            Gizmos.cuboid(new AABB(pos), style).setAlwaysOnTop();
        }
    }
}
