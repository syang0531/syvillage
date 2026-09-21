package com.syang.syvillage.build;

import com.syang.syvillage.config.SyVillageConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

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
 * somebody's first bell on empty ground - the walk's own radius stands in, because a place with
 * nothing in it still has ground a mob can stand on.
 */
public final class Dark {

    private Dark() {}

    /**
     * What the survey found.
     *
     * @param marks          where to draw, capped at {@link #REPORTED}
     * @param count          how many there actually are, which is the number that is reported
     * @param walked         how much ground was looked at, so "none" can be told from "nowhere"
     * @param aroundVillage  whether vanilla knew of a village here to bound the answer with
     */
    public record Survey(List<BlockPos> marks, int count, int walked, boolean aroundVillage) {

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
        int radius = SyVillageConfig.DARK_SURVEY_RADIUS.get();
        int minLight = SyVillageConfig.MIN_LIGHT_LEVEL.get();
        Reach reach = Reach.from(level, centre, radius);
        Set<Long> village = villageColumns(level, centre, radius);

        List<BlockPos> dark = new ArrayList<>();
        int count = 0;
        int walked = 0;
        for (long column : reach.columns()) {
            if (!village.isEmpty() && !village.contains(column)) {
                continue;
            }
            walked++;
            int x = Reach.keyX(column);
            int z = Reach.keyZ(column);
            BlockPos on = new BlockPos(x, Terrain.groundAt(level, x, z) + 1, z);
            if (level.getBrightness(LightLayer.BLOCK, on) >= minLight) {
                continue;
            }
            // Vanilla's own question, asked of vanilla's own block: a mob needs somewhere its
            // feet will hold. Slabs, leaves and farmland answer this for us.
            BlockState floor = level.getBlockState(on.below());
            if (!floor.isValidSpawn(level, on.below(), net.minecraft.world.entity.EntityTypes.ZOMBIE)) {
                continue;
            }
            count++;
            if (dark.size() < REPORTED) {
                dark.add(on);   // beyond this they are counted but not drawn
            }
        }
        return new Survey(List.copyOf(dark), count, walked, !village.isEmpty());
    }

    /**
     * The columns near something the village is made of.
     *
     * <p>Empty when there is no village here at all, which the caller reads as "use everything
     * the walk reached". A bell on empty ground is somebody founding a place, and they want the
     * same answer about the ground they are standing on.
     */
    private static Set<Long> villageColumns(ServerLevel level, BlockPos centre, int radius) {
        int around = SyVillageConfig.DARK_AROUND_POI.get();
        PoiManager pois = level.getPoiManager();
        Set<Long> out = new HashSet<>();
        pois.getInSquare(type -> type.is(PoiTypeTags.VILLAGE), centre, radius,
                PoiManager.Occupancy.ANY).forEach(record -> {
                    BlockPos at = record.getPos();
                    for (int dx = -around; dx <= around; dx++) {
                        for (int dz = -around; dz <= around; dz++) {
                            out.add(Reach.key(at.getX() + dx, at.getZ() + dz));
                        }
                    }
                });
        return out;
    }
}
