package com.syang.placitum.event;

import com.syang.placitum.Placitum;
import com.syang.placitum.command.PlacitumCommand;
import com.syang.placitum.data.ChronicleEntry;
import com.syang.placitum.data.EntryType;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.ResidentState;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.data.SettlementId;
import com.syang.placitum.defense.DefenseTick;
import com.syang.placitum.lifecycle.Lifecycle;
import com.syang.placitum.lifecycle.LifecycleManager;
import com.syang.placitum.registry.ModAttachments;
import com.syang.placitum.settlement.Registration;
import com.syang.placitum.store.SettlementManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BellBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Every game-side hook Placitum installs. */
@EventBusSubscriber(modid = Placitum.MODID)
public final class PlacitumEvents {

    private static final LifecycleManager LIFECYCLE = new LifecycleManager();

    private PlacitumEvents() {}

    public static LifecycleManager lifecycle() {
        return LIFECYCLE;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        PlacitumCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (SettlementManager.peek() == null) {
            return;
        }
        LIFECYCLE.tick(server);
    }

    /**
     * Boot resync.
     *
     * <p>A crash can leave residents saved as MATERIALIZED whose entities either never
     * reached disk or did. Rather than guess, everyone starts VIRTUAL and entities rebind as
     * they load.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        SettlementManager manager = SettlementManager.get(event.getServer());
        LIFECYCLE.reset();
        int reset = manager.resetMaterializedState();
        if (reset > 0) {
            Placitum.LOGGER.info("Reset {} resident(s) to VIRTUAL after an unclean shutdown", reset);
        }
    }

    /** Last chance to copy entity state back before the world closes. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        SettlementManager manager = SettlementManager.get(server);
        for (Settlement settlement : manager.all()) {
            ServerLevel level = server.getLevel(settlement.dimension());
            if (level != null && settlement.materializedCount() > 0) {
                Placitum.LOGGER.info("SHUTDOWN: writing back '{}' before the world closes",
                        settlement.name());
                manager.put(LifecycleManager.demoteAll(level, manager, settlement));
            }
        }
        LIFECYCLE.reset();
        SettlementManager.clear();
    }

    /**
     * The entity's own way out - the only moment its state is still readable.
     *
     * <p>ChunkEvent.Unload was too late: by the time it fires the entities are already out of
     * the level's lookup, so the write-back found nothing and every resident in a teleported-away
     * chunk silently kept days-old health, position, trades and profession.
     *
     * <p>This fires per entity, while it is still valid, for unloads, teleports and removals
     * alike.
     */
    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        SettlementManager manager = SettlementManager.peek();
        if (manager == null) {
            return;
        }
        UUID residentId = manager.residentOf(villager.getUUID());
        if (residentId == null) {
            return;
        }
        for (Settlement settlement : manager.all()) {
            Resident resident = settlement.resident(residentId).orElse(null);
            if (resident == null || !resident.materialized()) {
                continue;
            }
            List<Resident> updated = new ArrayList<>();
            for (Resident r : settlement.residents()) {
                updated.add(r.id().equals(residentId)
                        ? Lifecycle.writeBack(level, r, villager).withState(ResidentState.VIRTUAL)
                        : r);
            }
            manager.unbind(residentId);
            manager.put(LifecycleManager.skipTimeSpentMaterialized(
                    SettlementManager.withResidents(settlement, updated), level.getGameTime()));
            Placitum.LOGGER.debug("Wrote back {} as its entity left the level",
                    resident.lineage().fullName());
            return;
        }
    }

    /**
     * Chunk unload, with no grace period.
     *
     * <p>Once the unload finishes the entity is unreachable and its health, task progress and
     * trades are gone with it. Demoting late is the same as not demoting.
     */
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        SettlementManager manager = SettlementManager.peek();
        if (manager == null) {
            return;
        }
        int chunkX = event.getChunk().getPos().x();
        int chunkZ = event.getChunk().getPos().z();
        for (SettlementId entry : manager.listed()) {
            if (!entry.dimension().equals(level.dimension())) {
                continue;
            }
            Settlement settlement = manager.find(entry.id()).orElse(null);
            if (settlement == null || settlement.materializedCount() == 0) {
                continue;
            }
            Settlement next = LIFECYCLE.demoteInChunk(level, manager, settlement, chunkX, chunkZ);
            if (next != settlement) {
                manager.put(next);
            }
        }
    }

    /**
     * Rebinds an entity that carries a resident id.
     *
     * <p>Three outcomes, and the third matters most: an entity whose resident is gone is
     * released as an ordinary villager rather than killed. To the player, a villager
     * vanishing for no reason is exactly the bug this mod exists to fix.
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof Villager villager)) {
            return;
        }
        if (com.syang.placitum.lifecycle.Lifecycle.isSelfSpawn()) {
            return;   // promote is adding this one and will bind it itself
        }
        UUID residentId = villager.getData(ModAttachments.RESIDENT_ID);
        if (Placitum.NIL_UUID.equals(residentId)) {
            return;
        }
        SettlementManager manager = SettlementManager.peek();
        if (manager == null) {
            return;
        }

        for (Settlement settlement : manager.all()) {
            Resident resident = settlement.resident(residentId).orElse(null);
            if (resident == null) {
                continue;
            }
            if (resident.materialized() && manager.isBound(residentId)
                    && !villager.getUUID().equals(manager.entityOf(residentId))) {
                Placitum.LOGGER.warn("Duplicate entity for resident {}; discarding the newcomer", residentId);
                event.setCanceled(true);
                return;
            }
            manager.bind(residentId, villager.getUUID());
            Placitum.LOGGER.debug("Rebound {} to entity {}", resident.lineage().fullName(),
                    villager.getUUID());
            if (!resident.materialized()) {
                // Settle the absence before this resident has a body, or the modules will skip
                // it as materialized while consumption still counts it.
                Settlement settled = LifecycleManager.settleBeforeMaterializing(level, settlement);
                List<Resident> updated = new ArrayList<>();
                for (Resident r : settled.residents()) {
                    updated.add(r.id().equals(residentId) ? r.withState(ResidentState.MATERIALIZED) : r);
                }
                manager.put(SettlementManager.withResidents(settled, updated));
            }
            return;
        }

        villager.removeData(ModAttachments.RESIDENT_ID);
        Placitum.LOGGER.info("Released orphaned villager {} back to vanilla", villager.getUUID());
    }

    /**
     * A resident died where we could see it.
     *
     * <p>Recorded with a cause, always. The original complaint was never that villagers died -
     * it was never learning why, and a death that leaves no trace is the bug this whole mod is
     * arguing against.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        SettlementManager manager = SettlementManager.peek();
        if (manager == null) {
            return;
        }
        UUID residentId = manager.residentOf(villager.getUUID());
        if (residentId == null) {
            return;
        }
        for (Settlement settlement : manager.all()) {
            Resident resident = settlement.resident(residentId).orElse(null);
            if (resident == null) {
                continue;
            }
            String cause = event.getSource().getLocalizedDeathMessage(villager).getString();
            List<Resident> survivors = new ArrayList<>();
            for (Resident r : settlement.residents()) {
                if (!r.id().equals(residentId)) {
                    survivors.add(r);
                }
            }
            int gameDay = (int) (level.getGameTime() / 24000L);
            Settlement next = settlement.withResidents(survivors)
                    .withDefense(settlement.defense().withCasualty(gameDay, 1));
            next = next.withChronicle(next.chronicle().with(new ChronicleEntry(
                    level.getGameTime(), EntryType.DEATH, resident.lineage().fullName(), cause)));

            manager.unbind(residentId);
            manager.put(next);
            Placitum.LOGGER.info("DEATH in '{}': {} - {}", settlement.name(),
                    resident.lineage().fullName(), cause);
            return;
        }
    }

    /** Right-click the bell to register. One interaction, and nothing else in the world changes. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!player.isShiftKeyDown()) {
            // Plain right-click still rings the bell, and now that is the alarm: for a
            // registered settlement, ringing turns the militia out. The interaction a player
            // already reaches for does the thing they already meant by it.
            raiseAlarmAt(level, event.getPos(), player);
            return;
        }
        BlockPos pos = event.getPos();
        if (!(level.getBlockState(pos).getBlock() instanceof BellBlock)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        SettlementManager manager = SettlementManager.get(level.getServer());
        Registration.Result result = Registration.register(level, manager, pos);
        player.sendSystemMessage(describe(result));
    }

    /** Ringing a registered settlement's bell calls it to arms. */
    private static void raiseAlarmAt(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!(level.getBlockState(pos).getBlock() instanceof BellBlock)) {
            return;
        }
        SettlementManager manager = SettlementManager.peek();
        if (manager == null) {
            return;
        }
        for (Settlement settlement : manager.all()) {
            if (!settlement.dimension().equals(level.dimension())
                    || !settlement.center().equals(pos)) {
                continue;
            }
            Settlement raised = DefenseTick.soundAlarm(settlement, level, manager);
            manager.put(raised);
            int armed = (int) raised.residents().stream().filter(r -> r.gear().armed()).count();
            player.sendSystemMessage(armed > 0
                    ? Component.literal(raised.name() + " stands to arms - " + armed + " mustered")
                            .withStyle(ChatFormatting.GOLD)
                    : Component.literal(raised.name() + " has nothing to fight with; everyone is hiding")
                            .withStyle(ChatFormatting.RED));
            return;
        }
    }

    public static Component describe(Registration.Result result) {
        return switch (result) {
            case Registration.Result.Success success -> Component
                    .literal("Registered " + success.settlement().name() + " - "
                            + success.settlement().residentCount() + " resident(s)")
                    .withStyle(ChatFormatting.GREEN);
            case Registration.Result.AlreadyRegistered already -> Component
                    .literal("This bell already belongs to " + already.name())
                    .withStyle(ChatFormatting.YELLOW);
            case Registration.Result.Overlaps overlaps -> Component
                    .literal("Too close to " + overlaps.otherName() + " - " + overlaps.distance()
                            + " blocks away, " + overlaps.required() + " required")
                    .withStyle(ChatFormatting.RED);
            case Registration.Result.NotEnoughBeds beds -> Component
                    .literal("Not enough beds: " + beds.found() + " of " + beds.required())
                    .withStyle(ChatFormatting.RED);
            case Registration.Result.NotEnoughVillagers villagers -> Component
                    .literal("Not enough villagers: " + villagers.found() + " of " + villagers.required())
                    .withStyle(ChatFormatting.RED);
        };
    }
}
