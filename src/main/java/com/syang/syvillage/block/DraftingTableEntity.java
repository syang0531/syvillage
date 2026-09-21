package com.syang.syvillage.block;

import com.syang.syvillage.build.Outline;
import com.syang.syvillage.build.Placement;
import com.syang.syvillage.build.Raise;
import com.syang.syvillage.build.Site;
import com.syang.syvillage.config.SyVillageConfig;
import com.syang.syvillage.data.Craft;
import com.syang.syvillage.item.Blueprint;
import com.syang.syvillage.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A drawing on a board, and where it says the building goes.
 *
 * <p>All of this used to be a few seconds of memory on the server keyed by player, and every
 * complaint the preview collected came from that. Thirty seconds is not long enough to walk
 * round a building and look at it from the other side, and it is nowhere near long enough to go
 * and mine the tree that is in the way. <b>An outline that belongs to a block lasts as long as
 * the block does</b>, which is a length the player chooses rather than one we guess.
 *
 * <p>It does not break principle three. There is no {@code SavedData} and no manager: this is a
 * block in the world, saved by vanilla along with every other block entity and gone when
 * somebody breaks it. The guardian statue's memory of its golem is the same kind of thing.
 *
 * <p>Two buildings laid out at once means two tables. That is a limit made of a thing rather
 * than of a number, which is the kind this mod prefers.
 */
public class DraftingTableEntity extends BlockEntity implements Container, MenuProvider {

    /** Where the drawing sits when nobody has moved it: one step diagonally off the table. */
    public static final BlockPos DEFAULT_OFFSET = new BlockPos(1, 0, 1);

    private ItemStack drawing = ItemStack.EMPTY;
    private Rotation rotation = Rotation.NONE;
    private BlockPos offset = DEFAULT_OFFSET;
    private boolean showing = true;

    /** What the client draws. Worked out here; empty when there is nothing to show. */
    private Outline outline = Outline.none();

    public DraftingTableEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DRAFTING_TABLE_ENTITY.get(), pos, state);
    }

    // ---- what the board holds

    public ItemStack drawing() {
        return drawing;
    }

    /**
     * One slot, and it takes drawings only.
     *
     * <p>A slot rather than a button, because a button that says "take" tells the player nothing
     * about what is on the board. A furnace shows what is burning; a silhouette on its own does
     * not tell one house from another.
     */
    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return drawing.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == 0 ? drawing : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack taken = slot == 0 ? drawing.split(count) : ItemStack.EMPTY;
        if (!taken.isEmpty()) {
            refresh();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack taken = getItem(slot);
        drawing = ItemStack.EMPTY;
        refresh();
        return taken;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == 0) {
            drawing = stack;
            refresh();
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 && stack.getItem() instanceof Blueprint;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void setChanged() {
        super.setChanged();
    }

    @Override
    public void clearContent() {
        drawing = ItemStack.EMPTY;
        refresh();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("syvillage.drafting.title");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new DraftingMenu(id, inventory, this, worldPosition);
    }

    /** A broken table hands its drawing back rather than eating it. */
    public void dropDrawing(Level level, BlockPos pos) {
        if (!drawing.isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), drawing);
            drawing = ItemStack.EMPTY;
        }
    }

    // ---- what the board says

    public Rotation rotation() {
        return rotation;
    }

    public BlockPos offset() {
        return offset;
    }

    public boolean showing() {
        return showing;
    }

    public Outline outline() {
        return outline;
    }

    public void turn() {
        rotation = rotation.getRotated(Rotation.CLOCKWISE_90);
        refresh();
    }

    /**
     * Move the drawing, in blocks from this table.
     *
     * <p>Free coordinates rather than four corners to cycle through. Four was the first idea and
     * it is a special case of this one: somebody who wants the house across the stream can say
     * so, and somebody who wants it beside the table types one and one. Bounded, because an
     * offset of ten thousand is a survey of chunks nobody has loaded and a refusal nobody can
     * read.
     */
    public void moveTo(BlockPos wanted) {
        int limit = SyVillageConfig.MAX_DRAWING_OFFSET.get();
        offset = new BlockPos(
                Math.clamp(wanted.getX(), -limit, limit),
                Math.clamp(wanted.getY(), -limit, limit),
                Math.clamp(wanted.getZ(), -limit, limit));
        refresh();
    }

    public void toggleShowing() {
        showing = !showing;
        refresh();
    }

    /** Where the drawing puts the structure's lowest, most north-westerly corner. */
    public BlockPos corner() {
        return getBlockPos().offset(offset);
    }

    public @Nullable Placement placement() {
        if (drawing.isEmpty() || level == null) {
            return null;
        }
        BlockPos corner = corner();
        return new Placement(Blueprint.templateOf(drawing), corner, rotation,
                Craft.of(level.getBiome(corner)), corner.getY());
    }

    // ---- building

    /** @return how many blocks are going down, or -1 if this table cannot build */
    public int build() {
        Placement placement = placement();
        if (placement == null || !(level instanceof ServerLevel server)) {
            return -1;
        }
        if (!Site.read(server, placement).buildable()) {
            return -1;
        }
        int blocks = Raise.begin(server, placement);
        drawing = ItemStack.EMPTY;   // the drawing is what the building costs
        refresh();
        return blocks;
    }

    // ---- keeping the outline honest

    /**
     * Re-read the ground, now and then, for a board somebody is standing at.
     *
     * <p>This exists for one gesture: the player sees a red block inside the outline, goes and
     * mines it, and comes back. Without it they would have to poke the table to see it turn
     * green, and a step that teaches nothing is a step worth removing.
     *
     * <p><b>It is not the planning loop CLAUDE.md forbids.</b> It runs only for tables a player
     * built, only while the outline is switched on, only while somebody is near enough to be
     * looking at it, and it decides nothing - it re-reads an answer that is already on screen.
     * The banned loop walked a whole claim looking for work nobody had asked for.
     */
    public void serverTick() {
        if (!showing || drawing.isEmpty() || level == null
                || level.getGameTime() % SyVillageConfig.DRAWING_REFRESH_TICKS.get() != 0) {
            return;
        }
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        double watch = SyVillageConfig.DRAWING_WATCH_RANGE.get();
        if (server.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5, watch, false) == null) {
            return;
        }
        refresh();
    }

    /** Work the outline out again, and tell anybody watching only if it actually changed. */
    public void refresh() {
        setChanged();
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        Placement placement = placement();
        Outline fresh = showing && placement != null
                ? Outline.of(placement, Site.read(server, placement))
                : Outline.none();
        if (!fresh.equals(outline)) {
            outline = fresh;
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    // ---- saving and syncing
    //
    // One path for both: getUpdateTag is saveCustomOnly, so what saveAdditional writes is what
    // the client gets. The outline goes in it too. On disk that is a reading of the ground that
    // may be a tick out of date by the time the world loads, and it is replaced by the first
    // refresh - which costs a tick of a shape nobody is looking at yet.

    private static final String DRAWING = "drawing";
    private static final String ROTATION = "rotation";
    private static final String OFFSET = "offset";
    private static final String SHOWING = "showing";

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        drawing = input.read(DRAWING, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        rotation = Rotation.values()[Math.clamp(input.getIntOr(ROTATION, 0), 0, 3)];
        offset = input.read(OFFSET, BlockPos.CODEC).orElse(DEFAULT_OFFSET);
        showing = input.getBooleanOr(SHOWING, true);
        outline = Outline.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!drawing.isEmpty()) {
            output.store(DRAWING, ItemStack.CODEC, drawing);
        }
        output.putInt(ROTATION, rotation.ordinal());
        output.store(OFFSET, BlockPos.CODEC, offset);
        output.putBoolean(SHOWING, showing);
        outline.store(output);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    // The client draws every loaded board once a tick, and this is how it finds them without
    // searching. Both halves call these, and only the client side keeps the list.

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide()) {
            DraftingBoards.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        DraftingBoards.remove(this);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
