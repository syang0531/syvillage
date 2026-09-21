package com.syang.syvillage.block;

import com.syang.syvillage.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The one slot on the board, and the player's own pockets under it.
 *
 * <p>There was a Take button here instead, and it was wrong in the way a button usually is when
 * it stands in for a slot: it told you nothing. A furnace shows you what is burning; a board
 * should show you which drawing is pinned to it, because a silhouette alone is not enough to
 * tell one house from another.
 *
 * <p>The slot takes drawings and nothing else, one at a time. Everything else the screen shows -
 * the turn, the offset, whether the outline is up - rides the block entity's own sync, so this
 * menu carries no data of its own beyond the position the buttons need to name.
 */
public class DraftingMenu extends AbstractContainerMenu {

    /** Where the panel draws the drawing's slot, and the player's own. */
    public static final int SLOT_Y = 18;
    public static final int INVENTORY_Y = 158;

    private final Container board;
    private final BlockPos table;

    /** Client side: the position travels with the open packet, the contents with the slot. */
    public DraftingMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(id, inventory, new SimpleContainer(1), data.readBlockPos());
    }

    public DraftingMenu(int id, Inventory inventory, Container board, BlockPos table) {
        super(ModMenus.DRAFTING.get(), id);
        this.board = board;
        this.table = table;
        checkContainerSize(board, 1);

        // These two must match the panel texture. They did not, and the inventory's real
        // slots sat invisibly over the Build button and the sliders: hovering there lit a slot
        // highlight and clicking went to a slot nobody could see.
        addSlot(new Slot(board, 0, 8, DraftingMenu.SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return board.canPlaceItem(0, stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;   // one drawing, one building
            }
        });
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
    }

    public BlockPos table() {
        return table;
    }

    @Override
    public boolean stillValid(Player player) {
        return board.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) {
            return moved;
        }
        ItemStack stack = slot.getItem();
        moved = stack.copy();
        if (slotIndex == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return moved;
    }
}
