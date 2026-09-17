package com.syang.placitum.block;

import com.syang.placitum.Placitum;
import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.registry.ModBlocks;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.SpawnUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The statue's one memory: which golem is its golem.
 *
 * <p>A name rather than a count, and that is the whole design. Vanilla holds the number of golems
 * down through the <em>villagers'</em> memory - a villager who can see one stops wanting one - and
 * none of that applies to a golem we summon ourselves. Counting the golems nearby instead would
 * raise a second the moment the first wandered off, and a third after that, until the town was
 * made of iron.
 *
 * <p>So the statue remembers the one it made and asks after it by name. Gone, or dead, or carried
 * into another world: then, and only then, does it make another.
 */
public class GuardianBlockEntity extends BlockEntity {

    private static final String KEY = "golem";

    /** How far out a golem may be set down, and how far up or down that search may look. */
    private static final int RANGE_XZ = 6;
    private static final int RANGE_Y = 4;
    private static final int ATTEMPTS = 10;

    private @Nullable UUID golem;

    public GuardianBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.GUARDIAN.get(), pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // Optional, and absent on a statue that has never managed to raise one.
        golem = input.read(KEY, UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable(KEY, UUIDUtil.CODEC, golem);
    }

    /**
     * Looks for its golem now and then, and raises one if it has none.
     *
     * <p>On an interval, because the answer changes about as often as a golem dies, and because a
     * statue that searched the world sixty times a second is a statue nobody could afford to put
     * down. Nothing here is ever right or wrong by a few seconds.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
            GuardianBlockEntity statue) {
        if (!(level instanceof ServerLevel server)
                || level.getGameTime() % PlacitumConfig.GUARD_CHECK_TICKS.get() != 0
                || statue.onDuty(server)) {
            return;
        }
        Optional<IronGolem> raised = SpawnUtil.trySpawnMob(EntityTypes.IRON_GOLEM,
                EntitySpawnReason.MOB_SUMMONED, server, pos, ATTEMPTS, RANGE_XZ, RANGE_Y,
                SpawnUtil.Strategy.LEGACY_IRON_GOLEM, false);
        if (raised.isEmpty()) {
            return;   // nowhere to stand one up; it comes round again
        }
        IronGolem guard = raised.get();
        // Persistent on purpose. A guard that despawns when the player walks away is a guard for
        // exactly the times nothing was going to happen anyway.
        guard.setPersistenceRequired();
        // And player-made, which in vanilla means one thing only: it will not attack a player.
        // A golem the player paid four iron blocks for should not be the thing that kills them
        // over a villager caught by a stray arrow.
        guard.setPlayerCreated(true);
        statue.golem = guard.getUUID();
        statue.setChanged();
        Placitum.LOGGER.debug("A guardian statue at {} raised a golem", pos);
    }

    /** Whether the golem it remembers is still alive, still a golem, and still in this world. */
    private boolean onDuty(ServerLevel level) {
        if (golem == null) {
            return false;
        }
        // Any dimension, because "somewhere else" and "gone" are different answers and the statue
        // owes a guard to this town either way.
        Entity found = level.getEntityInAnyDimension(golem);
        return found instanceof IronGolem guard && guard.isAlive() && guard.level() == level;
    }
}
