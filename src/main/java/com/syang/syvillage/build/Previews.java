package com.syang.syvillage.build;

import com.syang.syvillage.config.SyVillageConfig;
import com.syang.syvillage.net.PreviewOutline;
import com.syang.syvillage.net.SyVillageNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import org.jspecify.annotations.Nullable;

/**
 * The outline a player is looking at, and the only thing in this mod that is remembered at all.
 *
 * <p>Per player, in memory, gone at logout. Not saved, not shared, and consulted by nothing that
 * puts a block down except to ask "is this the same thing you were just shown".
 *
 * <p><b>It is also the whole of the mod's interface.</b> There is no screen: an outline drawn in
 * the game's own gizmo lines and one line of chat carry where the structure stands, how big it
 * is and what is in the way. If that is not legible then "one block out, knock it down and start
 * again" comes straight back, which is the complaint 0.3 exists to answer.
 *
 * <p>The drawing happens on the client ({@link com.syang.syvillage.client.PreviewGizmos}); this
 * works out the geometry and sends it once. Dust particles were tried first and could not draw a
 * line - they scatter with distance and a footprint of them reads as fog.
 */
public final class Previews {

    private Previews() {}

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

    /** Remember an outline and send it. */
    public static void show(ServerPlayer player, BlockPos clicked, Placement placement,
            Site.Survey survey) {
        SHOWN.put(player.getUUID(), new Shown(clicked, placement, survey,
                player.level().getGameTime() + SyVillageConfig.PREVIEW_SECONDS.get() * 20L));
        SyVillageNetwork.send(player, outline(placement, survey));
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
        if (SHOWN.remove(player.getUUID()) != null) {
            SyVillageNetwork.send(player, PreviewOutline.none());
        }
    }

    /** The turn the player last used, so a drawing keeps the facing they set on it. */
    public static Rotation turnOf(ServerPlayer player, Rotation ifNone) {
        Shown shown = SHOWN.get(player.getUUID());
        return shown == null ? ifNone : shown.placement().rotation();
    }

    /**
     * Drop the outlines that have run out, and tell their clients.
     *
     * <p>Nothing is redrawn here. The client holds the geometry and re-submits it each frame,
     * so the wire is quiet while somebody stands and looks at one. Bounded by the number of
     * players holding a blueprint, and it reads nothing from the world.
     */
    public static void tick(MinecraftServer server) {
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
                SyVillageNetwork.send(player, PreviewOutline.none());
            }
        }
    }

    /**
     * The geometry, and only the geometry.
     *
     * <p>Columns go over as offsets from the corner so that the client can find the border of
     * the occupied set without knowing what a template is. Everything else about the placement -
     * which building, which palette, which way round - stays on this side.
     */
    private static PreviewOutline outline(Placement placement, Site.Survey survey) {
        Template template = placement.template();
        int width = template.turnedWidth(placement.rotation());
        int depth = template.turnedDepth(placement.rotation());
        List<Integer> columns = new ArrayList<>(template.columns().size());
        for (int[] column : template.columns()) {
            int[] turned = template.turnInBox(column[0], column[1], placement.rotation());
            columns.add((turned[0] << 8) | turned[1]);
        }
        return new PreviewOutline(placement.origin(), width, template.sizeY(), depth,
                List.copyOf(columns), survey.blocked(), survey.buildable());
    }
}
