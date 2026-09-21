package com.syang.syvillage.block;

import com.syang.syvillage.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The architect's table: a workstation a villager claims, and a drawing board a player uses.
 *
 * <p>It was only the first of those, and that was the whole of its problem - a block whose only
 * job was to exist so somebody could take a job at it. Now a drawing goes on it and the building
 * it describes is laid out in the world beside it, to be turned and moved and looked at from
 * across the valley before anybody commits.
 *
 * <p><b>This replaced a timer.</b> The outline used to live for a few seconds after a click, and
 * every complaint about it came from that: thirty seconds is not long enough to walk round a
 * building, and it is nowhere near long enough to go and mine the tree that is in the way. An
 * outline that belongs to a block in the world lasts exactly as long as the block does, which is
 * a length the player chooses rather than one we guess.
 *
 * <p>Wanting two buildings laid out at once means building two tables. That is a limit made of a
 * thing rather than a number, which is the kind this mod prefers.
 */
public class DraftingTable extends FacingTable implements EntityBlock {

    public DraftingTable(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DraftingTableEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,
            BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return type == ModBlocks.DRAFTING_TABLE_ENTITY.get()
                ? (l, pos, s, entity) -> ((DraftingTableEntity) entity).serverTick()
                : null;
    }

    /**
     * Right-click to open the board; right-click holding a drawing to put it on the board first.
     *
     * <p>One gesture, and which of the two it is depends on what is in your hand - the same rule
     * a lectern and a jukebox use.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        return open(level, pos, player);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level,
            BlockPos pos, Player player, net.minecraft.world.InteractionHand hand,
            BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DraftingTableEntity table)) {
            return InteractionResult.PASS;
        }
        if (table.accepts(held)) {
            if (!level.isClientSide()) {
                table.put(held.split(1));
            }
            return open(level, pos, player);
        }
        return open(level, pos, player);
    }

    private static InteractionResult open(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof DraftingTableEntity)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            // The screen is the client's business and needs no menu: the table's state is already
            // synced as block entity data, so there is nothing to open a container for.
            com.syang.syvillage.client.DraftingScreens.open(pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** A broken table gives its drawing back rather than eating it. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level,
            BlockPos pos, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof DraftingTableEntity table) {
            table.dropDrawing(level, pos);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
