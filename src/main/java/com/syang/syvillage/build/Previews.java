package com.syang.syvillage.build;

import com.syang.syvillage.config.SyVillageConfig;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;

/**
 * The outline a player is looking at, and nothing else in this mod that is remembered at all.
 *
 * <p>Per player, in memory, gone at logout. Not saved, not shared, not consulted by anything
 * that puts a block down except to ask "is this the same thing you were just shown".
 *
 * <p><b>It is also the whole of the mod's interface.</b> There is no screen and no client code:
 * an outline in dust particles and one line of chat carry where the structure will stand, how
 * high, what is in the way and what will hang over air. If that is not legible then "one block
 * out, knock it down and start again" comes straight back, which is the complaint 0.3 exists to
 * answer. So it is worth more care than it looks like it needs.
 *
 * <p>Confirmation is four things at once: the same template, the same spot, the same turn, and
 * inside {@code previewSeconds}. Any of them different and the click is a new preview rather
 * than two thousand blocks.
 */
public final class Previews {

    private Previews() {}

    /** Green: this is where it goes. */
    private static final ParticleOptions READY = new DustParticleOptions(0x4CC26A, 1.0f);
    /** Red: something is here. */
    private static final ParticleOptions BLOCKED = new DustParticleOptions(0xD9483B, 1.0f);
    /** Amber: this corner will hang over air, which is allowed and worth knowing. */
    private static final ParticleOptions EDGE = new DustParticleOptions(0xE0A83B, 1.0f);

    /** How often the outline is redrawn while it is alive. Dust lives about a second. */
    public static final int REDRAW_TICKS = 10;

    private record Shown(Placement placement, Site.Survey survey, long expires) {}

    private static final Map<UUID, Shown> SHOWN = new HashMap<>();

    /**
     * Remember an outline and draw it.
     *
     * @return the survey, so the caller can say in words what the colours say in dust
     */
    public static Site.Survey show(ServerPlayer player, Placement placement,
            Site.Survey survey) {
        SHOWN.put(player.getUUID(), new Shown(placement, survey,
                player.level().getGameTime() + SyVillageConfig.PREVIEW_SECONDS.get() * 20L));
        draw(player, placement, survey);
        return survey;
    }

    /**
     * Whether this click is the second one on an outline that is still good.
     *
     * <p>Compared on the placement itself rather than on the clicked block: the placement holds
     * the template, the corner, the turn and the floor, so four things are checked by one
     * equality and none of them can be forgotten.
     */
    public static boolean confirms(ServerPlayer player, Placement placement) {
        Shown shown = SHOWN.get(player.getUUID());
        return shown != null
                && shown.placement().equals(placement)
                && shown.survey().buildable()
                && player.level().getGameTime() <= shown.expires();
    }

    public static void forget(ServerPlayer player) {
        SHOWN.remove(player.getUUID());
    }

    /** The turn the player last used, so a second blueprint starts where the last one left off. */
    public static Rotation turnOf(ServerPlayer player, Rotation ifNone) {
        Shown shown = SHOWN.get(player.getUUID());
        return shown == null ? ifNone : shown.placement().rotation();
    }

    /**
     * Redraw every live outline, and drop the ones that have run out.
     *
     * <p>Bounded by the number of players holding a blueprint, and it reads nothing from the
     * world: the survey was taken when the outline was made. A stale outline is a feature -
     * the player who mined the block in the way sees it go green only after clicking again,
     * and clicking again is what they were going to do.
     */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (SHOWN.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Shown>> it = SHOWN.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Shown> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            if (player.level().getGameTime() > entry.getValue().expires()) {
                it.remove();
                continue;
            }
            if (player.level().getGameTime() % REDRAW_TICKS == 0) {
                draw(player, entry.getValue().placement(), entry.getValue().survey());
            }
        }
    }

    /**
     * The outline: the turned box's edge at floor level, plus whatever is in the way.
     *
     * <p>The edge rather than every column, because a filled rectangle of two hundred particles
     * is a green fog you cannot see the ground through. The blocked positions are drawn whole,
     * because those are the ones the player has to go and look at.
     */
    private static void draw(ServerPlayer player, Placement placement, Site.Survey survey) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Template template = placement.template();
        int w = template.turnedWidth(placement.rotation());
        int d = template.turnedDepth(placement.rotation());
        BlockPos corner = placement.origin();
        ParticleOptions edge = survey.buildable() ? READY : BLOCKED;

        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                if (x != 0 && x != w - 1 && z != 0 && z != d - 1) {
                    continue;
                }
                dust(level, player, edge, corner.getX() + x, placement.floor(),
                        corner.getZ() + z);
            }
        }
        for (BlockPos pos : survey.blocked()) {
            dust(level, player, BLOCKED, pos.getX(), pos.getY(), pos.getZ());
        }
        if (survey.floating() > 0 && survey.buildable()) {
            // One marker under each corner is enough to say "this end is over nothing".
            dust(level, player, EDGE, corner.getX(), placement.floor() - 1, corner.getZ());
            dust(level, player, EDGE, corner.getX() + w - 1, placement.floor() - 1,
                    corner.getZ() + d - 1);
        }
    }

    private static void dust(ServerLevel level, ServerPlayer player, ParticleOptions particle,
            int x, int y, int z) {
        level.sendParticles(player, particle, false, true,
                x + 0.5, y + 0.1, z + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
    }
}
