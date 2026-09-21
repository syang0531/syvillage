package com.syang.syvillage.item;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A drawing of a building. It does nothing in your hand: it goes on an architect's table.
 *
 * <p>It used to be used on the ground directly, which put the whole interaction on a clock -
 * two clicks within a few seconds - and every complaint about the preview came from that. A
 * drawing pinned to a board in the world has no clock on it, so it can be walked round, left
 * standing while you go and clear the ground it needs, and switched off with a button.
 *
 * <p>What the village supplies is the labour and the materials, and what it charges is the
 * drawing itself, bought from an architect with emeralds. It does not charge stone, because
 * hauling the stone for a nine by nine house is an errand rather than a decision. Building
 * consumes the drawing and leaves the table standing, so a second building needs a second
 * drawing - and two buildings laid out at once need two tables.
 */
public final class Blueprint extends Item {

    /**
     * What this drawing is of.
     *
     * <p>For now one structure, hard-wired, because what is unproven is the board and not the
     * catalogue. When the catalogue opens this becomes a data component on the stack, so that
     * one item and one texture cover every building an architect sells - which is what
     * {@link #templateOf} exists to hide until then.
     */
    public static final Identifier TEMPLATE =
            Identifier.fromNamespaceAndPath("syvillage", "tower");

    /** Which building this particular drawing is of. */
    public static Identifier templateOf(ItemStack stack) {
        return TEMPLATE;
    }

    public Blueprint(Item.Properties properties) {
        super(properties);
    }
}
