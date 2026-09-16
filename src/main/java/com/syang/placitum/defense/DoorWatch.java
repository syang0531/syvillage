package com.syang.placitum.defense;

import com.syang.placitum.Placitum;
import com.syang.placitum.build.CottagePlan;
import com.syang.placitum.data.AlertState;
import com.syang.placitum.data.Plot;
import com.syang.placitum.data.PlotKind;
import com.syang.placitum.data.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Shuts the settlement's own doors at night, and whenever the horn has sounded.
 *
 * <p>Vanilla villagers do close doors behind them - InteractWithDoor remembers what they opened
 * and shuts it again. It is just not reliable: an interrupted path, two villagers through the
 * same doorway, or a hurried run home at dusk all leave one standing open, and an open door is
 * an invitation for the rest of the night.
 *
 * <p>docs/defense.md asks for exactly this and the roadmap deferred it from M1 to here.
 *
 * <p>Only doors this settlement built. A player's own front door is not ours to operate, and the
 * doors of the vanilla houses it was adopted with stay on vanilla's rules - we know where our own
 * are without searching for them, which is what keeps this from being a scan of the whole claim.
 */
public final class DoorWatch {

    /** Roughly every five seconds. Doors do not need to be checked at thirty hertz. */
    private static final int INTERVAL_TICKS = 100;

    private DoorWatch() {}

    public static void closeUp(ServerLevel level, Settlement settlement) {
        if (level.getGameTime() % INTERVAL_TICKS != 0) {
            return;
        }
        boolean shouldBeShut = Curfew.curfewActive(level)
                || settlement.defense().alert() != AlertState.PEACE;
        if (!shouldBeShut) {
            return;
        }
        for (Plot plot : settlement.plots().values()) {
            if (plot.kind() == PlotKind.HOUSE) {
                shut(level, settlement, plot);
            }
        }
    }

    /**
     * Finds this house's door and closes it.
     *
     * <p>The door's height is not stored - the floor was decided from ground that has since been
     * built on - so a short column around the surface is searched for it. Eight reads a house,
     * twice a minute, only after dark.
     */
    private static void shut(ServerLevel level, Settlement settlement, Plot plot) {
        BlockPos northWest = settlement.grid().blockAt(plot.anchor());
        Direction facing = CottagePlan.doorFacing(plot.rotation());
        BlockPos column = CottagePlan.doorPosition(northWest, facing);
        if (!level.isLoaded(column)) {
            return;
        }
        int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types
                .MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());

        for (int y = surface + 1; y >= surface - CottagePlan.HEIGHT - 2; y--) {
            BlockPos at = new BlockPos(column.getX(), y, column.getZ());
            BlockState state = level.getBlockState(at);
            if (!(state.getBlock() instanceof DoorBlock)
                    || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER
                    || !state.getValue(DoorBlock.OPEN)) {
                continue;
            }
            // Both halves, and without neighbour updates - the same reason everything else this
            // mod places avoids them: a door half asked to check on its partner mid-change
            // removes itself.
            level.setBlock(at, state.setValue(DoorBlock.OPEN, false), Block.UPDATE_CLIENTS
                    | Block.UPDATE_KNOWN_SHAPE);
            BlockPos upper = at.above();
            BlockState top = level.getBlockState(upper);
            if (top.getBlock() instanceof DoorBlock) {
                level.setBlock(upper, top.setValue(DoorBlock.OPEN, false),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            level.playSound(null, at, net.minecraft.sounds.SoundEvents.WOODEN_DOOR_CLOSE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
            Placitum.LOGGER.debug("Shut a door at {} in '{}'", at.toShortString(),
                    settlement.name());
            return;
        }
    }
}
