package com.syang.syvillage.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

/**
 * A workstation with a front.
 *
 * <p>Both tables are the same block in this respect and neither was, until they had a face worth
 * pointing: a cube with the same texture on all four sides has no wrong way round, so nothing
 * here was missing. Now the north face carries the front and the block has to be told which way
 * north is.
 *
 * <p>The profession registrations take <em>every</em> state of these blocks as their
 * point-of-interest, so a villager claims one whichever way it is turned. That is why adding a
 * property here needs nothing done to {@code ModVillagers}.
 */
public class FacingTable extends Block {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public FacingTable(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * Placed facing the player.
     *
     * <p>The opposite of where they are looking, because a table you have just put down should be
     * one you are standing at rather than behind.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,
                context.getHorizontalDirection().getOpposite());
    }

    // Structure blocks and the /clone command turn what they copy. Without these two a rotated
    // copy keeps its original facing, which is the one case where a block that looks right in
    // the world comes out wrong in a build somebody else pastes.

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
