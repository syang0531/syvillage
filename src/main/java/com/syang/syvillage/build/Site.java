package com.syang.syvillage.build;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Whether a structure will go down here, and if not, exactly where it will not.
 *
 * <p>One rule, and it is the game's own rule for placing a block: <b>every position the
 * structure would occupy has to be free.</b> A bed refuses when something is in the way of
 * either half; this refuses when something is in the way of any of two thousand. Nothing is
 * cut, nothing is filled, nothing is felled - a hillside in the footprint is a refusal, and a
 * tree in the footprint is a refusal, and both are the player's to clear if they want this
 * spot. They can see which blocks, so a refusal is information rather than a chore.
 *
 * <p>Support is not asked about, for the same reason the bed does not ask: a bed placed at a
 * cliff edge hangs its head over the drop, and a house may too. What a structure stands on is
 * the player's business. {@link Survey#gap} says how far the worst of it hangs, and does not
 * refuse.
 *
 * <p>It counted the columns with nothing under them first, and in a real world that came back
 * as two hundred and twenty out of two hundred and twenty-one every single time. The count was
 * correct and it said nothing: the floor is one flat plane and the ground is not, so nearly
 * every column has air under it somewhere. One depth is a number a player can act on.
 *
 * <p><b>Occupied means the column's span, not the box.</b> From the lowest block the template
 * puts in that column to the highest, including the air between them: a house whose walls clear
 * a mound but whose living room is full of it is not placeable. Columns the template puts
 * nothing in - the roadway under a gate arch - are nobody's business, which is the same
 * distinction as a footprint not being a bounding box.
 */
public final class Site {

    private Site() {}

    /**
     * What a placement would do here.
     *
     * @param blocked the occupied positions, in template order, capped at {@link #REPORTED}
     * @param gap     the deepest run of air between the structure and the ground under it
     */
    public record Survey(Placement placement, List<BlockPos> blocked, int blockedCount,
            int gap) {

        public boolean buildable() {
            return blockedCount == 0;
        }

        /** The first thing in the way, for a message that names somewhere to go and look. */
        public BlockPos first() {
            return blocked.isEmpty() ? null : blocked.getFirst();
        }
    }

    /** How many blocked positions are kept for marking. Enough to see the shape of the problem. */
    public static final int REPORTED = 256;

    public static Survey read(ServerLevel level, Placement placement) {
        Template template = placement.template();
        List<BlockPos> blocked = new ArrayList<>();
        int blockedCount = 0;
        int gap = 0;

        for (int[] column : template.columns()) {
            BlockPos at = template.columnAt(placement.origin(), placement.rotation(),
                    column[0], column[1]);
            int bottom = template.bottomOf(column[0], column[1]);
            int top = template.topOf(column[0], column[1]);

            for (int y = bottom; y <= top; y++) {
                BlockPos pos = new BlockPos(at.getX(), placement.floor() + y, at.getZ());
                // Unloaded counts as in the way. We cannot see it, and guessing is how a house
                // ends up through somebody's roof.
                boolean free = level.hasChunkAt(pos)
                        && level.getBlockState(pos).canBeReplaced();
                if (!free) {
                    blockedCount++;
                    if (blocked.size() < REPORTED) {
                        blocked.add(pos);
                    }
                }
            }
            // How far this column's lowest block stands above the terrain under it. Read down
            // through our own masonry, so a tower set on a wall is standing on something rather
            // than hanging over it (principle eight).
            if (level.hasChunkAt(at)) {
                int ground = Terrain.footingAt(level, at.getX(), at.getZ(),
                        placement.palette().wall());
                gap = Math.max(gap, placement.floor() + bottom - 1 - ground);
            }
        }
        return new Survey(placement, List.copyOf(blocked), blockedCount, gap);
    }
}
