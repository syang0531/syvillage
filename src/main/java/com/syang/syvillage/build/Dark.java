package com.syang.syvillage.build;

import com.syang.syvillage.config.SyVillageConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.LightLayer;

/**
 * Where a monster can still stand up in this village.
 *
 * <p>This is the thing the mod was started for. Mobs kill villagers at night and the answer is
 * light - but the mod used to lay the light itself, on a grid, at five times the density the
 * job needed and nowhere the player chose. What a player cannot do is <b>see block light</b>.
 * They can put a lantern anywhere; they cannot tell which corner of their own village is still
 * dark, or whether they have finished. That is the asymmetry worth closing, and closing it is
 * information rather than automation.
 *
 * <p>Spawning turns on block light being zero. One lantern is fifteen and reaches fourteen
 * blocks, so this usually comes back with far fewer places than people expect - which is the
 * point. {@code minLightLevel} asks for a little more than zero, because a torch a creeper
 * takes out should not turn a safe village into a dark one overnight.
 *
 * <p>Bounded by <b>the village vanilla already knows about</b>: beds, job sites and meeting
 * points. We do not declare how far a village reaches, we ask. Where there is no village yet -
 * somebody's first bell on empty ground - a plain radius stands in, because a place with
 * nothing in it still has ground a mob can stand on.
 *
 * <p><b>Not filtered by whether anybody could walk there.</b> That was the first version and it
 * was the wrong question asked well: {@code Reach} answers "could a villager get here", which is
 * what a road needs to know. A mob on a ledge above the village does not walk in, it drops in,
 * and a torch on the far bank of a stream is still a torch worth placing. Light is about where
 * something can stand up, not about where somebody could have come from.
 */
public final class Dark {

    private Dark() {}

    /**
     * What the survey found.
     *
     * @param marks          where to draw, capped at {@link #REPORTED}
     * @param count          how many there actually are, which is the number that is reported
     * @param lights         where a light would do the most good, most first
     * @param walked         how many columns were examined, so "none" is told from "nowhere"
     * @param aroundVillage  whether vanilla knew of a village here to bound the answer with
     */
    public record Survey(List<BlockPos> marks, List<BlockPos> lights, int count, int walked,
            boolean aroundVillage) {

        public boolean safe() {
            return count == 0;
        }
    }

    /** Enough marks to see the shape of the problem without filling the sky with them. */
    public static final int REPORTED = 512;

    /**
     * Every place within reach of here that a monster could spawn tonight.
     *
     * @param centre what the walk starts from - a bell, or a statue
     */
    public static Survey read(ServerLevel level, BlockPos centre) {
        int minLight = SyVillageConfig.MIN_LIGHT_LEVEL.get();
        Set<Long> columns = villageColumns(level, centre);
        boolean aroundVillage = !columns.isEmpty();
        if (!aroundVillage) {
            columns = square(centre, SyVillageConfig.DARK_SURVEY_RADIUS.get());
        }

        List<BlockPos> dark = new ArrayList<>();
        int count = 0;
        int walked = 0;
        for (long column : columns) {
            int x = keyX(column);
            int z = keyZ(column);
            if (!level.hasChunkAt(new BlockPos(x, level.getMinY(), z))) {
                continue;   // nobody has loaded it, so nothing is spawning there either
            }
            walked++;
            BlockPos on = new BlockPos(x, Terrain.groundAt(level, x, z) + 1, z);
            if (level.getBrightness(LightLayer.BLOCK, on) >= minLight) {
                continue;
            }
            // Vanilla's own question, asked of vanilla's own block: a mob needs somewhere its
            // feet will hold. Slabs, leaves, farmland and water all answer this for us.
            if (!level.getBlockState(on.below()).isValidSpawn(level, on.below(),
                    EntityTypes.ZOMBIE)) {
                continue;
            }
            count++;
            if (dark.size() < REPORTED) {
                dark.add(on);   // beyond this they are counted but not drawn
            }
        }
        return new Survey(List.copyOf(dark), lightsFor(dark), count, walked, aroundVillage);
    }

    /**
     * Where to put lights so that none of this is dark any more.
     *
     * <p><b>Counting dark columns was the wrong unit.</b> Two thousand of them is true, and it
     * is also useless: nobody lights two thousand places, and nought never arrives. One lantern
     * covers eleven blocks in every direction, so two thousand columns is five or six lanterns -
     * and <em>that</em> is a number somebody can act on and finish.
     *
     * <p>This is the same mistake as "220 of 221 columns hang over air", which was also correct
     * and also said nothing. A true number nobody can act on does not satisfy principle nine.
     *
     * <p>It suggests; it does not place. Where the light actually goes, and whether it is a
     * lantern or a campfire or a torch under the eaves, stays the player's - which is the whole
     * difference between this and the lamp grid that 0.2 laid without asking.
     *
     * <p>Greedy over a coarse grid rather than exact: the best cover of a few thousand points
     * is not worth a server pause, and one light too many is not a wrong answer.
     */
    private static List<BlockPos> lightsFor(List<BlockPos> dark) {
        int reach = Math.max(1, SyVillageConfig.LIGHT_SOURCE_LEVEL.get()
                - SyVillageConfig.MIN_LIGHT_LEVEL.get());
        Map<Long, List<BlockPos>> cells = new LinkedHashMap<>();
        for (BlockPos pos : dark) {
            cells.computeIfAbsent(key(Math.floorDiv(pos.getX(), reach),
                    Math.floorDiv(pos.getZ(), reach)), c -> new ArrayList<>()).add(pos);
        }
        Set<BlockPos> left = new LinkedHashSet<>(dark);
        List<BlockPos> lights = new ArrayList<>();
        while (!left.isEmpty() && lights.size() < MOST_LIGHTS) {
            BlockPos best = null;
            int bestCovered = 0;
            for (List<BlockPos> cell : cells.values()) {
                BlockPos at = middle(cell);
                int covered = 0;
                for (BlockPos pos : left) {
                    if (covers(at, pos, reach)) {
                        covered++;
                    }
                }
                if (covered > bestCovered) {
                    bestCovered = covered;
                    best = at;
                }
            }
            if (best == null) {
                break;   // nothing left that a light in any of these cells would reach
            }
            lights.add(best);
            BlockPos chosen = best;
            left.removeIf(pos -> covers(chosen, pos, reach));
        }
        return List.copyOf(lights);
    }

    /**
     * Whether a light here would put some brightness there.
     *
     * <p>Taxicab, because that is how light spreads: one step in any of six directions costs
     * one. Through walls it costs more, so this is optimistic - and being optimistic about one
     * light only ever means the next survey asks for one more, which is a cheap way to be wrong.
     */
    private static boolean covers(BlockPos light, BlockPos dark, int reach) {
        return Math.abs(light.getX() - dark.getX()) + Math.abs(light.getY() - dark.getY())
                + Math.abs(light.getZ() - dark.getZ()) <= reach;
    }

    /** The column of a cell nearest its own middle, so the suggestion is somewhere real. */
    private static BlockPos middle(List<BlockPos> cell) {
        long x = 0;
        long y = 0;
        long z = 0;
        for (BlockPos pos : cell) {
            x += pos.getX();
            y += pos.getY();
            z += pos.getZ();
        }
        BlockPos centre = new BlockPos((int) (x / cell.size()), (int) (y / cell.size()),
                (int) (z / cell.size()));
        BlockPos nearest = cell.getFirst();
        for (BlockPos pos : cell) {
            if (pos.distSqr(centre) < nearest.distSqr(centre)) {
                nearest = pos;
            }
        }
        return nearest;
    }

    /** Enough advice to act on. Past this the answer is "light some of it and ask again". */
    private static final int MOST_LIGHTS = 24;

    /** Everything within {@code radius} of here, for a place that is not a village yet. */
    private static Set<Long> square(BlockPos centre, int radius) {
        Set<Long> out = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                out.add(key(centre.getX() + dx, centre.getZ() + dz));
            }
        }
        return out;
    }

    // A column as one number, so a set of them is cheap. Was Reach's, which this no longer uses.

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int keyX(long key) {
        return (int) (key >> 32);
    }

    private static int keyZ(long key) {
        return (int) key;
    }

    /**
     * The columns near something the village is made of.
     *
     * <p>Empty when there is no village here at all, which the caller reads as "use everything
     * the walk reached". A bell on empty ground is somebody founding a place, and they want the
     * same answer about the ground they are standing on.
     */
    private static Set<Long> villageColumns(ServerLevel level, BlockPos centre) {
        int radius = SyVillageConfig.DARK_SURVEY_RADIUS.get();
        int around = SyVillageConfig.DARK_AROUND_POI.get();
        PoiManager pois = level.getPoiManager();
        Set<Long> out = new HashSet<>();
        pois.getInSquare(type -> type.is(PoiTypeTags.VILLAGE), centre, radius,
                PoiManager.Occupancy.ANY).forEach(record -> {
                    BlockPos at = record.getPos();
                    for (int dx = -around; dx <= around; dx++) {
                        for (int dz = -around; dz <= around; dz++) {
                            out.add(key(at.getX() + dx, at.getZ() + dz));
                        }
                    }
                });
        return out;
    }
}
