package com.syang.syvillage.client;

import com.syang.syvillage.block.DraftingTableEntity;
import com.syang.syvillage.net.DraftingCommand;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The drawing board.
 *
 * <p>A screen, which CLAUDE.md said this mod would not have. It has one now for a reason worth
 * writing down: the thing the player is arranging is not in their hand, it is a building laid
 * out across a valley, and "click the same block twice within thirty seconds" was the shape that
 * came of trying to arrange it without one. Four buttons and three numbers is both fewer
 * gestures and fewer rules.
 *
 * <p>Shaped after the structure block's, because that is the screen a player who wants to place
 * a building has already met. No slot and no container: the drawing, the turn, the offset and
 * the outline all arrive as block entity data, so there is nothing here to synchronise.
 */
public class DraftingScreen extends Screen {

    private static final int WIDTH = 210;
    private static final int LINE = 22;

    private final BlockPos table;

    private @Nullable EditBox x;
    private @Nullable EditBox y;
    private @Nullable EditBox z;
    private @Nullable Button turn;
    private @Nullable Button show;
    private @Nullable Button build;

    public DraftingScreen(BlockPos table) {
        super(Component.translatable("syvillage.drafting.title"));
        this.table = table;
    }

    private @Nullable DraftingTableEntity board() {
        return minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(table) instanceof DraftingTableEntity found
                ? found : null;
    }

    @Override
    protected void init() {
        DraftingTableEntity board = board();
        if (board == null) {
            onClose();
            return;
        }
        int left = (width - WIDTH) / 2;
        int top = height / 2 - 60;

        x = offsetBox(left, top + LINE, board.offset().getX());
        y = offsetBox(left + 70, top + LINE, board.offset().getY());
        z = offsetBox(left + 140, top + LINE, board.offset().getZ());

        turn = addRenderableWidget(Button.builder(Component.empty(),
                        b -> send(DraftingCommand.TURN))
                .bounds(left, top + LINE * 2 + 6, 100, 20).build());
        show = addRenderableWidget(Button.builder(Component.empty(),
                        b -> send(DraftingCommand.SHOW))
                .bounds(left + 105, top + LINE * 2 + 6, 105, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("syvillage.drafting.take"),
                        b -> send(DraftingCommand.TAKE))
                .bounds(left, top + LINE * 3 + 6, 100, 20).build());
        build = addRenderableWidget(Button.builder(
                        Component.translatable("syvillage.drafting.build"),
                        b -> {
                            send(DraftingCommand.BUILD);
                            onClose();
                        })
                .bounds(left + 105, top + LINE * 3 + 6, 105, 20).build());
        refreshLabels();
    }

    /**
     * One offset box.
     *
     * <p>Sent on every edit rather than behind an apply button. A number typed into a box that
     * then has to be applied is two steps for one intention, and the outline is the
     * confirmation: type twelve and watch the building walk twelve blocks east.
     */
    private EditBox offsetBox(int left, int top, int value) {
        EditBox box = new EditBox(font, left, top, 60, 20, Component.empty());
        box.setMaxLength(4);
        box.setValue(Integer.toString(value));
        // Nonsense is caught where it lands rather than kept out of the box: read() shrugs at a
        // lone minus sign and the table clamps anything wild, so typing "-" on the way to "-12"
        // does not have to be fought.
        box.setResponder(text -> sendOffset());
        return addWidget(box);
    }

    private void sendOffset() {
        ClientPacketDistributor.sendToServer(new DraftingCommand(table, DraftingCommand.MOVE,
                new BlockPos(read(x), read(y), read(z))));
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
        ClientPacketDistributor.sendToServer(new DraftingCommand(table, action, BlockPos.ZERO));
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
    public void tick() {
        refreshLabels();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partial) {
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        int left = (width - WIDTH) / 2;
        int top = height / 2 - 60;
        graphics.text(font, title, left, top - 24, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("syvillage.drafting.offset"),
                left, top + 8, 0xFFA0A0A0);
        // The boxes are added with addWidget, which focuses them but does not draw them.
        for (EditBox box : new EditBox[] {x, y, z}) {
            if (box != null) {
                box.extractRenderState(graphics, mouseX, mouseY, partial);
            }
        }
        DraftingTableEntity board = board();
        if (board == null) {
            return;
        }
        Component held = board.drawing().isEmpty()
                ? Component.translatable("syvillage.drafting.empty")
                : board.drawing().getHoverName();
        graphics.text(font, held, left, top - 10, 0xFFC8C8C8);
        if (!board.drawing().isEmpty() && !board.outline().buildable()) {
            graphics.text(font, Component.translatable("syvillage.drafting.in_the_way",
                    board.outline().blocked().length / 3), left, top + LINE * 4 + 10,
                    0xFFD9483B);
        }
    }

    /** The world carries on behind it: the point of the board is to look at what it draws. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
