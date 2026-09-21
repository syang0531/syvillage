package com.syang.syvillage.build;

import com.syang.syvillage.config.SyVillageConfig;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import org.jspecify.annotations.Nullable;

/**
 * The outline a player is looking at, and the only thing in this mod that is remembered at all.
 *
 * <p>Per player, in memory, gone at logout. Not saved, not shared, and consulted by nothing that
 * puts a block down except to ask "is this the same thing you were just shown".
 *
 * <p><b>It is also the whole of the mod's interface.</b> There is no screen and no client code:
 * an outline in dust and one line of chat carry where the structure stands, how big it is, what
 * is in the way and what will hang over air. If that is not legible then "one block out, knock
 * it down and start again" comes straight back, which is the complaint 0.3 exists to answer.
 *
 * <p>Drawn the way a structure block draws: <b>a dashed wireframe round the whole box</b>, which
 * says how tall and how wide, and <b>a solid line round the columns that actually get blocks</b>,
 * which says what it will sit on. A field of dust over the whole footprint was the first attempt
 * and it read as fog - you could not see the ground you were being asked to judge.
 */
public final class Previews {

    private Previews() {}

    /** The box: what the drawing occupies. Geometry, so it does not change colour. */
    private static final ParticleOptions BOX = new DustParticleOptions(0xDCE3EA, 0.6f);
    /** The footprint, when it will go down. */
    private static final ParticleOptions READY = new DustParticleOptions(0x4CC26A, 1.0f);
    /** The footprint, and each block in the way, when it will not. */
    private static final ParticleOptions BLOCKED = new DustParticleOptions(0xD9483B, 1.0f);
    /** A corner that will hang over air. Allowed, and worth knowing. */
    private static final ParticleOptions EDGE = new DustParticleOptions(0xE0A83B, 1.0f);

    /** How often the outline is redrawn while it is alive. Dust lives about a second. */
    public static final int REDRAW_TICKS = 10;

    /**
     * What a player was last shown.
     *
     * <p>{@code clicked} is kept beside the placement on purpose. The placement's corner depends
     * on which way the player was facing, so recomputing it on the second click would move the
     * structure if they had turned even slightly between the two. The second click confirms
     * <em>what was drawn</em>, and the clicked block is what identifies it.
     */
    private record Shown(BlockPos clicked, Placement placement, Site.Survey survey, long expires) {}

    private static final Map<UUID, Shown> SHOWN = new HashMap<>();

    /** Remember an outline and draw it. */
    public static void show(ServerPlayer player, BlockPos clicked, Placement placement,
            Site.Survey survey) {
        SHOWN.put(player.getUUID(), new Shown(clicked, placement, survey,
                player.level().getGameTime() + SyVillageConfig.PREVIEW_SECONDS.get() * 20L));
        draw(player, placement, survey);
    }

    /**
     * The placement this click confirms, or null if this click is a fresh look instead.
     *
     * <p>Four things have to agree: the same drawing, the same block, still inside
     * {@code previewSeconds}, and an outline that was green. Sneaking never confirms - it is
     * the turn gesture, and a gesture that sometimes builds two thousand blocks instead is not
     * one anybody would trust.
     */
    public static @Nullable Placement confirms(ServerPlayer player, BlockPos clicked,
            boolean sneaking) {
        Shown shown = SHOWN.get(player.getUUID());
        if (sneaking || shown == null
                || !shown.clicked().equals(clicked)
                || !shown.survey().buildable()
                || player.level().getGameTime() > shown.expires()) {
            return null;
        }
        return shown.placement();
    }

    public static void forget(ServerPlayer player) {
        SHOWN.remove(player.getUUID());
    }

    /** The turn the player last used, so a drawing keeps the facing they set on it. */
    public static Rotation turnOf(ServerPlayer player, Rotation ifNone) {
        Shown shown = SHOWN.get(player.getUUID());
        return shown == null ? ifNone : shown.placement().rotation();
    }

    /**
     * Redraw every live outline, and drop the ones that have run out.
     *
     * <p>Bounded by the number of players holding a blueprint, and it reads nothing from the
     * world: the survey was taken when the outline was made. A stale outline is honest - the
     * player who has just mined the block in the way sees it go green only after clicking
     * again, and clicking again is what they were going to do.
     */
    public static void tick(MinecraftServer server) {
        if (SHOWN.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Shown>> it = SHOWN.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Shown> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.level().getGameTime() > entry.getValue().expires()) {
                it.remove();
                continue;
            }
            if (player.level().getGameTime() % REDRAW_TICKS == 0) {
                draw(player, entry.getValue().placement(), entry.getValue().survey());
            }
        }
    }

    // ---- drawing

    private static void draw(ServerPlayer player, Placement placement, Site.Survey survey) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Template template = placement.template();
        int w = template.turnedWidth(placement.rotation());
        int d = template.turnedDepth(placement.rotation());
        BlockPos corner = placement.origin();
        int floor = placement.floor();

        wireframe(level, player, corner, w, template.sizeY(), d);
        footprint(level, player, template, placement, w, d,
                survey.buildable() ? READY : BLOCKED);

        for (BlockPos pos : survey.blocked()) {
            dust(level, player, BLOCKED, pos.getX(), pos.getY(), pos.getZ());
        }
        if (survey.floating() > 0 && survey.buildable()) {
            dust(level, player, EDGE, corner.getX(), floor - 1, corner.getZ());
            dust(level, player, EDGE, corner.getX() + w - 1, floor - 1, corner.getZ() + d - 1);
        }
    }

    /**
     * The twelve edges of the box, dashed.
     *
     * <p>Dashed rather than solid so that it reads as the extent of the drawing and not as
     * something that will be built. The solid line below it is the one that will.
     */
    private static void wireframe(ServerLevel level, ServerPlayer player, BlockPos corner,
            int w, int h, int d) {
        int x0 = corner.getX();
        int z0 = corner.getZ();
        int y0 = corner.getY();
        for (int i = 0; i < w; i++) {
            if (dash(i)) {
                for (int y : new int[] {y0, y0 + h - 1}) {
                    dust(level, player, BOX, x0 + i, y, z0);
                    dust(level, player, BOX, x0 + i, y, z0 + d - 1);
                }
            }
        }
        for (int i = 0; i < d; i++) {
            if (dash(i)) {
                for (int y : new int[] {y0, y0 + h - 1}) {
                    dust(level, player, BOX, x0, y, z0 + i);
                    dust(level, player, BOX, x0 + w - 1, y, z0 + i);
                }
            }
        }
        for (int i = 0; i < h; i++) {
            if (dash(i)) {
                dust(level, player, BOX, x0, y0 + i, z0);
                dust(level, player, BOX, x0 + w - 1, y0 + i, z0);
                dust(level, player, BOX, x0, y0 + i, z0 + d - 1);
                dust(level, player, BOX, x0 + w - 1, y0 + i, z0 + d - 1);
            }
        }
    }

    /** Two blocks on, one off. Short enough to read as a line at seventeen blocks. */
    private static boolean dash(int i) {
        return i % 3 != 2;
    }

    /**
     * A solid line round the columns that get blocks - the shape of what will stand here.
     *
     * <p>The edge of the occupied set, not of the box: a column is on it when one of its four
     * neighbours is not occupied. That is what makes a gatehouse read as a gatehouse rather
     * than as a twenty-five by nine rectangle, and it is the same distinction as a footprint
     * not being a bounding box.
     */
    private static void footprint(ServerLevel level, ServerPlayer player, Template template,
            Placement placement, int w, int d, ParticleOptions colour) {
        boolean[][] filled = new boolean[w][d];
        for (int[] column : template.columns()) {
            int[] turned = template.turnInBox(column[0], column[1], placement.rotation());
            filled[turned[0]][turned[1]] = true;
        }
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                if (!filled[x][z] || !onEdge(filled, x, z, w, d)) {
                    continue;
                }
                dust(level, player, colour, placement.origin().getX() + x, placement.floor(),
                        placement.origin().getZ() + z);
            }
        }
    }

    private static boolean onEdge(boolean[][] filled, int x, int z, int w, int d) {
        for (int[] step : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int nx = x + step[0];
            int nz = z + step[1];
            if (nx < 0 || nz < 0 || nx >= w || nz >= d || !filled[nx][nz]) {
                return true;
            }
        }
        return false;
    }

    private static void dust(ServerLevel level, ServerPlayer player, ParticleOptions particle,
            int x, int y, int z) {
        level.sendParticles(player, particle, false, true,
                x + 0.5, y + 0.1, z + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
    }
}
