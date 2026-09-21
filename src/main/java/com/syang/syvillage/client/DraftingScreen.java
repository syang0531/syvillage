package com.syang.syvillage.client;

import com.syang.syvillage.block.DraftingMenu;
import com.syang.syvillage.block.DraftingTableEntity;
import com.syang.syvillage.net.DraftingCommand;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The drawing board.
 *
 * <p>A screen, which CLAUDE.md said this mod would not have. It has one now for a reason worth
 * writing down: the thing the player is arranging is not in their hand, it is a building laid
 * out across a valley, and "click the same block twice within thirty seconds" was the shape that
 * came of trying to arrange it without one. Three numbers and three buttons is both fewer
 * gestures and fewer rules.
 *
 * <p>With a slot, because a Take button is a slot that tells you nothing. A furnace shows what
 * is burning; a board shows which drawing is pinned to it, and a silhouette alone does not tell
 * one house from another.
 *
 * <p>Everything except the slot - the turn, the offset, whether the outline is up, whether it
 * can be built - arrives as block entity data, which is already synced to everybody near the
 * table. The menu carries only the position the buttons have to name.
 */
public class DraftingScreen extends AbstractContainerScreen<DraftingMenu> {

    private static final Identifier PANEL =
            Identifier.fromNamespaceAndPath("syvillage", "textures/gui/drafting_table.png");

    private @Nullable EditBox x;
    private @Nullable EditBox y;
    private @Nullable EditBox z;
    private @Nullable Button turn;
    private @Nullable Button show;
    private @Nullable Button build;

    public DraftingScreen(DraftingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 200);
        inventoryLabelY = imageHeight - 94;
    }

    private @Nullable DraftingTableEntity board() {
        return minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.table())
                        instanceof DraftingTableEntity found
                ? found : null;
    }

    @Override
    protected void init() {
        super.init();
        DraftingTableEntity board = board();
        BlockPos offset = board == null ? DraftingTableEntity.DEFAULT_OFFSET : board.offset();

        x = offsetBox(leftPos + 8, topPos + 44, offset.getX());
        y = offsetBox(leftPos + 62, topPos + 44, offset.getY());
        z = offsetBox(leftPos + 116, topPos + 44, offset.getZ());

        turn = addRenderableWidget(Button.builder(Component.empty(),
                        b -> send(DraftingCommand.TURN))
                .bounds(leftPos + 8, topPos + 68, 78, 20).build());
        show = addRenderableWidget(Button.builder(Component.empty(),
                        b -> send(DraftingCommand.SHOW))
                .bounds(leftPos + 90, topPos + 68, 78, 20).build());
        build = addRenderableWidget(Button.builder(
                        Component.translatable("syvillage.drafting.build"),
                        b -> {
                            send(DraftingCommand.BUILD);
                            onClose();
                        })
                .bounds(leftPos + 8, topPos + 92, 160, 20).build());
        refreshLabels();
    }

    /**
     * One offset box.
     *
     * <p>Sent on every keystroke rather than behind an apply button. A number typed into a box
     * that then has to be applied is two steps for one intention, and the outline in the world
     * is the confirmation: type twelve and watch the building walk twelve blocks.
     */
    private EditBox offsetBox(int left, int top, int value) {
        EditBox box = new EditBox(font, left, top, 52, 18, Component.empty());
        box.setMaxLength(4);
        box.setValue(Integer.toString(value));
        // Nonsense is caught where it lands rather than kept out of the box: read() shrugs at a
        // lone minus sign and the table clamps anything wild, so typing "-" on the way to "-12"
        // is not a fight.
        box.setResponder(text -> sendOffset());
        return addWidget(box);
    }

    private void sendOffset() {
        ClientPacketDistributor.sendToServer(new DraftingCommand(menu.table(),
                DraftingCommand.MOVE, new BlockPos(read(x), read(y), read(z))));
    }

    private static int read(@Nullable EditBox box) {
        if (box == null) {
            return 0;
        }
        try {
            return Integer.parseInt(box.getValue());
        } catch (NumberFormatException emptyOrJustAMinusSign) {
            return 0;
        }
    }

    private void send(int action) {
        ClientPacketDistributor.sendToServer(new DraftingCommand(menu.table(), action,
                BlockPos.ZERO));
    }

    /** The two buttons that say what they currently are, rather than what they would do. */
    private void refreshLabels() {
        DraftingTableEntity board = board();
        if (board == null || turn == null || show == null || build == null) {
            return;
        }
        turn.setMessage(Component.translatable("syvillage.drafting.turn",
                Component.translatable("syvillage.facing." + facing(board.rotation()))));
        show.setMessage(Component.translatable(board.showing()
                ? "syvillage.drafting.showing" : "syvillage.drafting.hidden"));
        build.active = !board.drawing().isEmpty() && board.outline().buildable();
    }

    private static String facing(Rotation rotation) {
        return switch (rotation) {
            case NONE -> "north";
            case CLOCKWISE_90 -> "east";
            case CLOCKWISE_180 -> "south";
            case COUNTERCLOCKWISE_90 -> "west";
        };
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshLabels();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partial) {
        super.extractBackground(graphics, mouseX, mouseY, partial);
        graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL, leftPos, topPos, 0.0F, 0.0F,
                imageWidth, imageHeight, 256, 256);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partial) {
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        // The boxes are added with addWidget, which focuses them but does not draw them.
        for (EditBox box : new EditBox[] {x, y, z}) {
            if (box != null) {
                box.extractRenderState(graphics, mouseX, mouseY, partial);
            }
        }
        graphics.text(font, Component.translatable("syvillage.drafting.offset"),
                leftPos + 8, topPos + 34, 0xFF404040);
        DraftingTableEntity board = board();
        if (board == null) {
            return;
        }
        Component held = board.drawing().isEmpty()
                ? Component.translatable("syvillage.drafting.empty")
                : board.drawing().getHoverName();
        graphics.text(font, held, leftPos + 30, topPos + 24, 0xFF404040);
        if (!board.drawing().isEmpty() && !board.outline().buildable()) {
            graphics.text(font, Component.translatable("syvillage.drafting.in_the_way",
                    board.outline().blocked().length / 3), leftPos + 8, topPos + 114,
                    0xFFAA3322);
        }
    }

    /** The world carries on behind it: the point of the board is to look at what it draws. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
