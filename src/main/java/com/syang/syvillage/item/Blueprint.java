package com.syang.syvillage.item;

import com.syang.syvillage.data.Catalogue;
import com.syang.syvillage.data.Drawing;
import com.syang.syvillage.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/**
 * A drawing of a building. It does nothing in your hand: it goes on an architect's table.
 *
 * <p>It used to be used on the ground directly, which put the whole interaction on a clock - two
 * clicks within a few seconds - and every complaint about the preview came from that. A drawing
 * pinned to a board in the world has no clock on it, so it can be walked round, left standing
 * while you clear the ground it needs, and switched off with a button.
 *
 * <p><b>One item covers every building.</b> Which one is a {@link Drawing} on the stack rather
 * than a registry entry of its own: a hundred and sixty-eight items would be a hundred and
 * sixty-eight models, a creative menu nobody could search, and no way at all for a data pack to
 * add a house. The tooltip reads the component, because the side drawing a tooltip cannot read
 * a template - a client has no structure manager.
 *
 * <p>What the village supplies is the labour and the materials, and what it charges is the
 * drawing itself, bought from an architect with emeralds. It does not charge stone, because
 * hauling the stone for a nine by nine house is an errand rather than a decision. Building
 * consumes the drawing and leaves the table standing, so a second building needs a second
 * drawing - and two laid out at once need two tables.
 */
public final class Blueprint extends Item {

    /** What a blank one is of, so a stack from a command or an older world still means something. */
    public static final Identifier FALLBACK =
            Identifier.fromNamespaceAndPath("syvillage", "tower");

    public Blueprint(Item.Properties properties) {
        super(properties);
    }

    /** One drawing, as an item. */
    public static ItemStack of(Drawing drawing) {
        ItemStack stack = new ItemStack(
                com.syang.syvillage.registry.ModItems.BLUEPRINT.get());
        stack.set(ModComponents.DRAWING.get(), drawing);
        return stack;
    }

    public static @Nullable Drawing drawingOf(ItemStack stack) {
        return stack.get(ModComponents.DRAWING.get());
    }

    /** Which building this particular drawing is of. */
    public static Identifier templateOf(ItemStack stack) {
        Drawing drawing = drawingOf(stack);
        return drawing == null ? FALLBACK : drawing.template();
    }

    @Override
    public Component getName(ItemStack stack) {
        Drawing drawing = drawingOf(stack);
        return drawing == null ? super.getName(stack)
                : Component.translatable("item.syvillage.blueprint.of", drawing.name());
    }

    /**
     * What is on the paper.
     *
     * <p>Size first, because that is what decides whether it fits where you are standing. Then
     * the two things that decide whether a village grows around it: beds are where people come
     * from, and a job block is where a trade comes from. Both are vanilla's numbers, counted off
     * Mojang's own building.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> lines, TooltipFlag flag) {
        Drawing drawing = drawingOf(stack);
        if (drawing == null) {
            return;
        }
        lines.accept(Component.translatable("item.syvillage.blueprint.extent", drawing.extent())
                .withStyle(ChatFormatting.GRAY));
        if (drawing.beds() > 0) {
            lines.accept(Component.translatable("item.syvillage.blueprint.beds", drawing.beds())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (drawing.workstation()) {
            lines.accept(Component.translatable("item.syvillage.blueprint.workstation")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (flag.isAdvanced()) {
            lines.accept(Component.literal(drawing.template().toString())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Everything an architect could ever draw, for the creative menu. */
    public static java.util.List<ItemStack> everything() {
        return Catalogue.all().stream().map(Blueprint::of).toList();
    }
}
