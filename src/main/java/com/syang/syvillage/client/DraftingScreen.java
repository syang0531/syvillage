package com.syang.syvillage.client;

import com.syang.syvillage.block.DraftingMenu;
import com.syang.syvillage.block.DraftingTableEntity;
import com.syang.syvillage.config.SyVillageClientConfig;
import com.syang.syvillage.net.DraftingCommand;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
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
 * came of trying to arrange it without one.
 *
 * <p>With a slot, because a Take button is a slot that tells you nothing. A furnace shows what
 * is burning; a board shows which drawing is pinned to it, and a silhouette alone does not tell
 * one house from another.
 *
 * <p>Everything except the slot - the turn, the offset, whether the outline is up, whether it
 * can be built - arrives as block entity data, already synced to everybody near the table. The
 * menu carries only the position the buttons have to name.
 */
public class DraftingScreen extends AbstractContainerScreen<DraftingMenu> {

    private static final Identifier PANEL =
            Identifier.fromNamespaceAndPath("syvillage", "textures/gui/drafting_table.png");

    private static final int COLUMN = 54;
    private static final int OFFSET_Y = 44;

    private final List<AbstractWidget> needsDrawing = new ArrayList<>();

    private @Nullable EditBox x;
    private @Nullable EditBox y;
    private @Nullable EditBox z;
    private @Nullable Button turn;
    private @Nullable Button show;
    private @Nullable Button build;

    public DraftingScreen(DraftingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 240);
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
        needsDrawing.clear();
        DraftingTableEntity board = board();
        BlockPos offset = board == null ? DraftingTableEntity.DEFAULT_OFFSET : board.offset();

        x = axis(0, offset.getX());
        y = axis(1, offset.getY());
        z = axis(2, offset.getZ());

        turn = gated(Button.builder(Component.empty(), b -> send(DraftingCommand.TURN))
                .bounds(leftPos + 8, topPos + 84, 78, 20).build());
        show = gated(Button.builder(Component.empty(), b -> send(DraftingCommand.SHOW))
                .bounds(leftPos + 90, topPos + 84, 78, 20).build());
        build = addRenderableWidget(Button.builder(
                        Component.translatable("syvillage.drafting.build"),
                        b -> {
                            send(DraftingCommand.BUILD);
                            onClose();
                        })
                .bounds(leftPos + 8, topPos + 106, 160, 20).build());

        addRenderableWidget(opacity(leftPos + 8, topPos + 128, 78,
                "syvillage.drafting.mass_opacity", SyVillageClientConfig.MASS_OPACITY));
        addRenderableWidget(opacity(leftPos + 90, topPos + 128, 78,
                "syvillage.drafting.mark_opacity", SyVillageClientConfig.MARK_OPACITY));
        refreshLabels();
    }

    /**
     * One axis: a box to type into, and a minus and a plus under it.
     *
     * <p>Both, rather than one or the other. Typing is how you say "thirty blocks that way";
     * clicking is how you nudge a wall off a tree without taking your eyes off the outline, and
     * neither does the other's job well.
     */
    private EditBox axis(int index, int value) {
        int left = leftPos + 8 + index * COLUMN;
        EditBox box = new EditBox(font, left, topPos + OFFSET_Y, 50, 18, Component.empty());
        box.setMaxLength(4);
        box.setValue(Integer.toString(value));
        // Nonsense is caught where it lands rather than kept out of the box: read() shrugs at a
        // lone minus sign and the table clamps anything wild, so typing "-" on the way to "-12"
        // is not a fight.
        box.setResponder(text -> sendOffset());
        addWidget(box);
        needsDrawing.add(box);

        gated(Button.builder(Component.literal("-"), b -> nudge(index, -1))
                .bounds(left, topPos + OFFSET_Y + 20, 24, 18).build());
        gated(Button.builder(Component.literal("+"), b -> nudge(index, 1))
                .bounds(left + 26, topPos + OFFSET_Y + 20, 24, 18).build());
        return box;
    }

    /** A widget that means nothing with an empty board, and says so by being grey. */
    private <T extends AbstractWidget> T gated(T widget) {
        needsDrawing.add(widget);
        return addRenderableWidget(widget);
    }

    private AbstractSliderButton opacity(int left, int top, int width, String key,
            net.neoforged.neoforge.common.ModConfigSpec.IntValue setting) {
        return new AbstractSliderButton(left, top, width, 18,
                Component.empty(), setting.get() / 255.0) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                setMessage(Component.translatable(key, Math.round(value * 100)));
            }

            @Override
            protected void applyValue() {
                // Straight into the client's own config, which is where a display preference
                // belongs: it outlives this table, this world and this session.
                setting.set((int) Math.round(value * 255));
            }
        };
    }

    private void nudge(int index, int by) {
        EditBox box = switch (index) {
            case 0 -> x;
            case 1 -> y;
            default -> z;
        };
        if (box != null) {
            box.setValue(Integer.toString(read(box) + by));   // the responder sends it
        }
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
        boolean pinned = !board.drawing().isEmpty();
        turn.setMessage(Component.translatable("syvillage.drafting.turn",
                Component.translatable("syvillage.facing." + facing(board.rotation()))));
        show.setMessage(Component.translatable(board.showing()
                ? "syvillage.drafting.showing" : "syvillage.drafting.hidden"));
        for (AbstractWidget widget : needsDrawing) {
            widget.active = pinned;
        }
        build.active = pinned && board.outline().buildable();
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
        // Added with addWidget, which focuses them but does not draw them.
        for (EditBox box : new EditBox[] {x, y, z}) {
            if (box != null) {
                box.extractRenderState(graphics, mouseX, mouseY, partial);
            }
        }
        DraftingTableEntity board = board();
        Component held = board == null || board.drawing().isEmpty()
                ? Component.translatable("syvillage.drafting.empty")
                : board.drawing().getHoverName();
        graphics.text(font, held, leftPos + 30, topPos + 23, 0xFF404040);
        graphics.text(font, Component.translatable("syvillage.drafting.offset"),
                leftPos + 8, topPos + 34, 0xFF707070);
        if (board != null && !board.drawing().isEmpty() && !board.outline().buildable()) {
            graphics.text(font, Component.translatable("syvillage.drafting.in_the_way",
                    board.outline().blocked().length / 3), leftPos + 8, topPos + 149,
                    0xFFAA3322);
        }
    }

    /**
     * The title only.
     *
     * <p>The inventory's own label is dropped: it sits exactly where the message about what is
     * in the way goes, and of the two it is the one nobody has ever needed to be told.
     */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, 0xFF404040);
    }

    /** The world carries on behind it: the point of the board is to look at what it draws. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
