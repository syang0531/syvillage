package com.syang.syvillage.build;

import com.syang.syvillage.data.Craft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;

/**
 * A structure, somewhere, turned some way. Everything an expansion needs and nothing else.
 *
 * <p>This replaces the old {@code BuildRecipe}, and the difference is the whole of 0.3. A recipe
 * carried a frozen ground profile and a list of clearance spans, because the settlement read the
 * world when it planned and had to remember what it had read until a queue got round to laying
 * the blocks. There is no queue and no planner: a player says where, and the blocks go down.
 *
 * <p>So there is nothing to freeze. {@link Raise#expand} never touches a level - it cannot, the
 * signature has no level in it - which is the strongest form principle four has ever taken here.
 * There is no codec either: this is never saved, and a record that is never saved has no
 * migration, no optional fields and no sixteen-field limit.
 *
 * @param origin   the north-west corner of the turned box, in the world
 * @param floor    the world y the template's layer 0 sits at - the face the player clicked
 */
public record Placement(Identifier templateId, BlockPos origin, Rotation rotation, Craft palette,
        int floor) {

    /**
     * The corner of a box that puts the clicked block nearest the player.
     *
     * <p>The clicked block used to be the middle, which is the obvious way to aim something and
     * the wrong way to stand beside it: a seventeen-wide tower centred on the block at your feet
     * closes over your head. A corner cannot, and which corner is decided by where the player is
     * looking - the box fills the quarter of the world in front of them.
     *
     * <p>What comes back is always the lowest x and z of the box, whichever quarter that was, so
     * everything downstream keeps treating the origin as the north-west corner.
     */
    public static BlockPos corner(BlockPos at, float yaw, int width, int depth) {
        double radians = Math.toRadians(yaw);
        double lookX = -Math.sin(radians);
        double lookZ = Math.cos(radians);
        return at.offset(lookX >= 0 ? 0 : -(width - 1), 0, lookZ >= 0 ? 0 : -(depth - 1));
    }

    /** The shape itself. Static content, read from a jar once and cached, so this stays pure. */
    public Template template() {
        return Template.of(templateId);
    }
}
