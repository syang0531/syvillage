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
 * outline and says what is wrong with it; the second click, on the same block inside a few
 * seconds, builds. Sneaking turns it a quarter and never builds. That step is what makes the
 * difference between this and "one block out, knock it down and start again".
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
        // Where a block would have gone: against the face that was clicked.
        BlockPos at = context.getClickedPos().relative(context.getClickedFace());
        boolean sneaking = context.isSecondaryUseActive();

        Placement confirmed = Previews.confirms(player, at, sneaking);
        if (confirmed != null) {
            int blocks = Raise.begin(level, confirmed);
            Previews.forget(player);
            context.getItemInHand().consume(1, player);
            level.gameEvent(player, GameEvent.BLOCK_PLACE, at);
            player.sendSystemMessage(Component
                    .translatable("syvillage.blueprint.raised",
                            confirmed.template().name(), blocks)
                    .withStyle(ChatFormatting.GREEN));
            return InteractionResult.SUCCESS;
        }

        Template template = Template.of(TEMPLATE);
        Rotation rotation = sneaking
                ? Previews.turnOf(player, Rotation.NONE).getRotated(Rotation.CLOCKWISE_90)
                : Previews.turnOf(player, Rotation.NONE);
        Placement placement = new Placement(TEMPLATE,
                Placement.corner(at, player.getYRot(), template.turnedWidth(rotation),
                        template.turnedDepth(rotation)),
                rotation, Craft.of(level.getBiome(at)), at.getY());

        Site.Survey survey = Site.read(level, placement);
        Previews.show(player, at, placement, survey);
        player.sendSystemMessage(describe(template, survey));
        return InteractionResult.SUCCESS;
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
        if (survey.gap() > 0) {
            return Component.translatable("syvillage.blueprint.floating", template.name(),
                            survey.gap())
                    .withStyle(ChatFormatting.YELLOW);
        }
        return Component.translatable("syvillage.blueprint.ready", template.name())
                .withStyle(ChatFormatting.GREEN);
    }
}
