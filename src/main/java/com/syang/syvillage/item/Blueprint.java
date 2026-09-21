package com.syang.syvillage.item;

import com.syang.syvillage.build.Placement;
import com.syang.syvillage.build.Previews;
import com.syang.syvillage.build.Raise;
import com.syang.syvillage.build.Site;
import com.syang.syvillage.build.Template;
import com.syang.syvillage.data.Craft;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * A drawing of a building. Used on a block it becomes the building.
 *
 * <p>Placed the way a block is placed, because that is what a player already knows: it goes
 * against the face that was clicked, it is refused when something is in the way, and it is not
 * refused for hanging over air. A bed is two blocks and follows those rules; this is two
 * thousand and follows the same ones.
 *
 * <p>The one thing a block does not have is the step in between. The first click shows the
 * outline and says what is wrong with it; the second click, on the same spot inside a few
 * seconds, builds. That step is what makes the difference between this and "one block out,
 * knock it down and start again".
 *
 * <p>What the village supplies is the labour and the materials, and what it charges is the
 * drawing - bought from an architect with emeralds. It does not charge stone, because hauling
 * the stone for a nine by nine house is an errand rather than a decision.
 */
public final class Blueprint extends Item {

    /**
     * What this drawing is of.
     *
     * <p>For now one structure, hard-wired, because what is unproven is the preview and not the
     * catalogue. When the catalogue opens this becomes a data component on the stack, so that
     * one item and one texture cover every building an architect sells.
     */
    public static final Identifier TEMPLATE =
            Identifier.fromNamespaceAndPath("syvillage", "tower");

    public Blueprint(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        Template template = Template.of(TEMPLATE);

        // Where a block would have gone: against the face that was clicked. The structure is
        // centred on that column rather than cornered at it, because a player aiming a
        // seventeen-wide tower is aiming at its middle.
        BlockPos at = context.getClickedPos().relative(context.getClickedFace());
        Rotation rotation = turn(player, context);
        BlockPos origin = new BlockPos(
                at.getX() - template.turnedWidth(rotation) / 2, at.getY(),
                at.getZ() - template.turnedDepth(rotation) / 2);
        Placement placement = new Placement(TEMPLATE, origin, rotation,
                Craft.of(level.getBiome(at)), at.getY());

        if (Previews.confirms(player, placement)) {
            int blocks = Raise.begin(level, placement);
            Previews.forget(player);
            context.getItemInHand().consume(1, player);
            level.gameEvent(player, GameEvent.BLOCK_PLACE, at);
            player.sendSystemMessage(Component
                    .translatable("syvillage.blueprint.raised", template.name(), blocks)
                    .withStyle(ChatFormatting.GREEN));
            return InteractionResult.SUCCESS;
        }

        Site.Survey survey = Previews.show(player, placement, Site.read(level, placement));
        player.sendSystemMessage(describe(template, survey));
        return InteractionResult.SUCCESS;
    }

    /** Sneaking turns it a quarter before looking. Otherwise it keeps the turn it had. */
    private static Rotation turn(ServerPlayer player, UseOnContext context) {
        Rotation held = Previews.turnOf(player, Rotation.NONE);
        return context.isSecondaryUseActive() ? held.getRotated(Rotation.CLOCKWISE_90) : held;
    }

    /**
     * One line, and it says the thing the player cannot see.
     *
     * <p>Principle twelve, at the only scale left: this used to be a tally of two hundred and
     * fifty lots and a reason for each. One drawing, one click, one sentence, and a coordinate
     * to walk to.
     */
    private static Component describe(Template template, Site.Survey survey) {
        if (!survey.buildable()) {
            BlockPos first = survey.first();
            return Component.translatable("syvillage.blueprint.blocked", survey.blockedCount(),
                            first.getX(), first.getY(), first.getZ())
                    .withStyle(ChatFormatting.RED);
        }
        if (survey.floating() > 0) {
            return Component.translatable("syvillage.blueprint.floating", template.name(),
                            survey.floating())
                    .withStyle(ChatFormatting.YELLOW);
        }
        return Component.translatable("syvillage.blueprint.ready", template.name())
                .withStyle(ChatFormatting.GREEN);
    }
}
