package com.syang.placitum.settlement;

import com.syang.placitum.Placitum;
import com.syang.placitum.build.GridSurvey;
import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.data.Craft;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.data.SettlementId;
import com.syang.placitum.store.SettlementManager;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * Turning a bell into a settlement.
 *
 * <p>Naming the place and reading the ground, and that is all. Villagers are not counted,
 * adopted, aged, employed or bound to anything - they were, and that machinery is what made the
 * mod impossible to balance and invisible to play. See docs/why-the-reset.md.
 *
 * <p>There are no minimum beds or villagers to check for either. A bell and some ground is
 * enough to start building; whether anybody lives there is vanilla's business.
 */
public final class Registration {

    /** What a registration attempt did. */
    public sealed interface Result {
        record Success(Settlement settlement) implements Result {}

        record Overlaps(String otherName, int distance, int required) implements Result {}

        record AlreadyRegistered(String name) implements Result {}
    }

    private Registration() {}

    public static Result register(ServerLevel level, SettlementManager manager, BlockPos bellPos) {
        for (SettlementId existing : manager.listed()) {
            if (!existing.dimension().equals(level.dimension())) {
                continue;
            }
            int distance = (int) Math.sqrt(existing.center().distSqr(bellPos));
            int required = PlacitumConfig.MIN_SETTLEMENT_DISTANCE.get();
            if (distance < required) {
                if (existing.center().equals(bellPos)) {
                    return new Result.AlreadyRegistered(existing.name());
                }
                return new Result.Overlaps(existing.name(), distance, required);
            }
        }

        UUID id = UUID.randomUUID();
        RandomSource rng = RandomSource.create(id.getMostSignificantBits());
        SettlementId identity = new SettlementId(id, NameGenerator.settlementName(rng),
                level.dimension(), bellPos, PlacitumConfig.CLAIM_RADIUS_CHUNKS.get());

        // The palette is the biome's, read once here and never again. A town does not change
        // what it is made of; it changes what it is allowed to build.
        Craft palette = Craft.of(level.getBiome(bellPos));
        Settlement settlement = Settlement.founding(identity, palette);
        // Surveyed now rather than on the next tick: the player is standing here, so the chunks
        // are loaded and this is the cheapest moment it will ever be.
        settlement = settlement.withGrid(GridSurvey.run(level, settlement).grid());
        manager.put(settlement);

        Placitum.LOGGER.info("Registered '{}' at {}, built in the {} style", identity.name(),
                bellPos.toShortString(), palette.getSerializedName());
        return new Result.Success(settlement);
    }
}
