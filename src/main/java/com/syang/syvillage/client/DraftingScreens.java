package com.syang.syvillage.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * The one line the block needs from the client.
 *
 * <p>Kept apart from the screen itself so that the block - which is common code - names a class
 * with no client imports of its own to reach past. A dedicated server never loads either of
 * them, because it never takes the branch that calls this.
 */
public final class DraftingScreens {

    private DraftingScreens() {}

    public static void open(BlockPos table) {
        Minecraft.getInstance().setScreenAndShow(new DraftingScreen(table));
    }
}
