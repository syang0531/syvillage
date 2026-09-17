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

    /**
     * Whether we have already said that we cannot raise one.
     *
     * <p>Not saved, and deliberately so: it exists to keep one line per outage out of a line
     * every ten seconds, and the first tick after a reload may as well say it again.
     */
    private boolean complained;

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
                || level.getGameTime() % PlacitumConfig.GUARD_CHECK_TICKS.get() != 0) {
            return;
        }
        String vacancy = statue.vacancy(server);
        if (vacancy == null) {
            return;   // on duty
        }
        Optional<IronGolem> raised = SpawnUtil.trySpawnMob(EntityTypes.IRON_GOLEM,
                EntitySpawnReason.MOB_SUMMONED, server, pos, ATTEMPTS, RANGE_XZ, RANGE_Y,
                SpawnUtil.Strategy.LEGACY_IRON_GOLEM, false);
        if (raised.isEmpty()) {
            // Once per outage. A statue that has been walled in or paved over is a thing the
            // player has to be told about, because from the outside it looks exactly like a
            // statue that does not work - which is the whole of this project's hard-won lesson.
            if (!statue.complained) {
                statue.complained = true;
                Placitum.LOGGER.info("A guardian statue at {} has no golem and nowhere to put"
                        + " one: it needs open ground within {} blocks", pos, RANGE_XZ);
            }
            return;   // it comes round again
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
        statue.complained = false;
        statue.setChanged();
        // Info, not debug: this happens once per golem death, so it is not chatter. And it says
        // why, because "raised a golem" three times over is the same line whether the player
        // killed two or the statue lost track of them, and those need different fixes.
        Placitum.LOGGER.info("A guardian statue at {} raised a golem - {}", pos, vacancy);
    }

    /**
     * Why this statue owes the town a golem, or null if it does not.
     *
     * <p>A sentence rather than a boolean, and for once that is not decoration. Every one of
     * these means the same thing to the code and something different to whoever is reading the
     * log: a golem the player killed is the block working, and a golem that cannot be found at
     * all is the block losing track of one.
     */
    private @Nullable String vacancy(ServerLevel level) {
        if (golem == null) {
            return "it had none";
        }
        // Any dimension, because "somewhere else" and "gone" are different answers and the
        // statue owes this town a guard either way.
        Entity found = level.getEntityInAnyDimension(golem);
        if (found == null) {
            return "its golem could not be found";
        }
        if (!(found instanceof IronGolem guard)) {
            return "what it remembers is no longer a golem";
        }
        if (!guard.isAlive()) {
            return "its golem died";
        }
        if (guard.level() != level) {
            return "its golem left this world";
        }
        return null;
    }
}
