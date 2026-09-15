package com.syang.placitum.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.syang.placitum.data.Assignment;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.data.SettlementId;
import com.syang.placitum.event.PlacitumEvents;
import com.syang.placitum.lifecycle.LifecycleManager;
import com.syang.placitum.settlement.Registration;
import com.syang.placitum.sim.SimParams;
import com.syang.placitum.sim.Simulation;
import com.syang.placitum.store.SettlementManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.BellBlock;

/**
 * Debug and management commands.
 *
 * <p>{@code info} is level 0: in multiplayer an ordinary player still needs to see how their
 * own village is doing. Everything that changes state stays at level 2.
 */
public final class PlacitumCommand {

    private PlacitumCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("placitum");

        root.then(Commands.literal("register")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> register(ctx.getSource())));

        root.then(Commands.literal("unregister")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> unregister(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        root.then(Commands.literal("list")
                .executes(ctx -> list(ctx.getSource())));

        root.then(Commands.literal("info")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> info(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        root.then(Commands.literal("tick")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                .executes(ctx -> tick(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        IntegerArgumentType.getInteger(ctx, "ticks"))))));

        root.then(Commands.literal("promote")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> promote(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        root.then(Commands.literal("demote")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> demote(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        root.then(Commands.literal("resident")
                .then(Commands.literal("list")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> residents(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"))))));

        dispatcher.register(root);
    }

    private static int register(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos bell = findNearestBell(level, BlockPos.containing(source.getPosition()));
        if (bell == null) {
            source.sendFailure(Component.literal("No bell within 32 blocks"));
            return 0;
        }
        Registration.Result result =
                Registration.register(level, SettlementManager.get(source.getServer()), bell);
        source.sendSuccess(() -> PlacitumEvents.describe(result), true);
        return result instanceof Registration.Result.Success ? 1 : 0;
    }

    private static int unregister(CommandSourceStack source, String rawId) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        ServerLevel level = source.getServer().getLevel(settlement.dimension());
        if (level != null && settlement.materializedCount() > 0) {
            // Hand the entities back before letting go, or those villagers are orphaned.
            settlement = LifecycleManager.demoteAll(level, manager, settlement);
            manager.put(settlement);
        }
        PlacitumEvents.lifecycle().forget(settlement.id());
        manager.unregister(settlement.id());
        String name = settlement.name();
        source.sendSuccess(() -> Component.literal("Unregistered " + name
                + " (its data file is kept, so re-registering restores it)"), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        List<SettlementId> entries = manager.listed();
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No registered settlements"), false);
            return 0;
        }
        for (SettlementId entry : entries) {
            Settlement settlement = manager.find(entry.id()).orElse(null);
            String suffix = settlement == null ? "?"
                    : settlement.scale() + ", pop " + settlement.population()
                            + ", " + settlement.materializedCount() + " materialized";
            source.sendSuccess(() -> Component.literal(shortId(entry.id()) + "  " + entry.name()
                    + "  [" + suffix + "]  " + entry.center().toShortString()), false);
        }
        return entries.size();
    }

    private static int info(CommandSourceStack source, String rawId) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        // Reading implies settling up first, or the numbers shown are stale by definition.
        long now = source.getServer().overworld().getGameTime();
        Settlement settled = Simulation.catchUp(source.getServer().overworld().getSeed(), settlement,
                SimParams.fromConfig(), now);
        manager.put(settled);

        source.sendSuccess(() -> Component.literal(settled.name() + " (" + settled.scale() + ")")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("  id " + settled.id()), false);
        source.sendSuccess(() -> Component.literal("  centre " + settled.center().toShortString()
                + " in " + settled.dimension().identifier()), false);
        boolean held = PlacitumEvents.lifecycle().isHeld(settled.id());
        source.sendSuccess(() -> Component.literal("  population " + settled.population()
                + " (" + settled.materializedCount() + " materialized, "
                + settled.residentCount() + " records)"
                + (held ? "  [held virtual by /placitum demote]" : "")), false);
        source.sendSuccess(() -> Component.literal("  jobs: " + countJob(settled, Assignment.FARMER)
                + " farmer, " + countJob(settled, Assignment.BUILDER) + " builder, "
                + countJob(settled, Assignment.WOODCUTTER) + " woodcutter, "
                + countJob(settled, Assignment.SMITH) + " smith, "
                + countJob(settled, Assignment.SCHOLAR) + " scholar, "
                + countJob(settled, Assignment.NONE) + " none"), false);
        source.sendSuccess(() -> Component.literal("  sim step " + settled.simStep()
                + ", last settled at tick " + settled.lastSimTick()
                + " (now " + now + ")"), false);
        source.sendSuccess(() -> Component.literal("  alert " + settled.alert()
                + ", plots " + settled.plots().size()
                + ", beds in plots " + settled.bedCount()
                + (settled.plots().isEmpty() ? "  (no plots until M3 builds houses)" : "")), false);
        source.sendSuccess(() -> Component.literal("  stock " + describeStock(settled)), false);
        return 1;
    }

    private static int tick(CommandSourceStack source, String rawId, int ticks) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        long seed = source.getServer().overworld().getSeed();
        long target = settlement.lastSimTick() + ticks;
        Settlement advanced = Simulation.catchUp(seed, settlement, SimParams.fromConfig(), target);
        manager.put(advanced);

        long steps = advanced.simStep() - settlement.simStep();
        source.sendSuccess(() -> Component.literal("Advanced " + ticks + " tick(s) = " + steps
                + " step(s). Stock: " + describeStock(advanced)), true);

        // "Nothing happened and I do not know why" is the exact experience this mod exists to
        // remove, so the debug command has to answer it rather than leave the player guessing.
        int materialized = advanced.materializedCount();
        if (materialized > 0) {
            source.sendSuccess(() -> Component.literal("  " + materialized + " of "
                    + advanced.residentCount() + " resident(s) are MATERIALIZED, so the simulation"
                    + " skipped them - they act as real entities instead."), false);
            source.sendSuccess(() -> Component.literal("  Run /placitum demote "
                    + shortId(advanced.id()) + " first to see the virtual formula run."), false);
        }
        int farmers = countJob(advanced, Assignment.FARMER);
        source.sendSuccess(() -> Component.literal("  " + farmers + " farmer(s), "
                + advanced.population() + " mouth(s) to feed"), false);
        return (int) steps;
    }

    private static int residents(CommandSourceStack source, String rawId) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        for (Resident r : settlement.residents()) {
            String line = r.lineage().fullName()
                    + "  " + r.assignment().job().getPath()
                    + "  " + r.stage()
                    + "  age " + r.ageDays()
                    + "  hp " + r.vitals().health()
                    + "  " + r.state()
                    + (r.zombified() ? "  ZOMBIFIED" : "");
            source.sendSuccess(() -> Component.literal("  " + line), false);
        }
        return settlement.residentCount();
    }

    private static int promote(CommandSourceStack source, String rawId) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        PlacitumEvents.lifecycle().beginPromotion(settlement.id());
        source.sendSuccess(() -> Component.literal("Promotion queued for " + settlement.name()
                + "; it runs within the tick budget"), true);
        return 1;
    }

    private static int demote(CommandSourceStack source, String rawId) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        ServerLevel level = source.getServer().getLevel(settlement.dimension());
        if (level == null) {
            source.sendFailure(Component.literal("That dimension is not loaded"));
            return 0;
        }
        PlacitumEvents.lifecycle().hold(settlement.id());
        Settlement demoted = LifecycleManager.demoteAll(level, manager, settlement);
        manager.put(demoted);
        source.sendSuccess(() -> Component.literal("Demoted " + demoted.name()
                + " and holding it virtual"), true);
        source.sendSuccess(() -> Component.literal("  It will not re-promote while you stand here."
                + " Run /placitum promote " + shortId(demoted.id()) + " to release it."), false);
        return 1;
    }

    /** Accepts a full UUID or any unambiguous prefix, because nobody types a UUID twice. */
    private static Optional<Settlement> resolve(SettlementManager manager, String rawId) {
        try {
            return manager.find(UUID.fromString(rawId));
        } catch (IllegalArgumentException ignored) {
            // fall through to prefix matching
        }
        Settlement match = null;
        for (SettlementId entry : manager.listed()) {
            if (entry.id().toString().startsWith(rawId.toLowerCase())
                    || entry.name().equalsIgnoreCase(rawId)) {
                if (match != null) {
                    return Optional.empty();   // ambiguous
                }
                match = manager.find(entry.id()).orElse(null);
            }
        }
        return Optional.ofNullable(match);
    }

    private static int countJob(Settlement settlement, net.minecraft.resources.Identifier job) {
        int n = 0;
        for (Resident r : settlement.residents()) {
            if (r.assignment().job().equals(job) && r.counts()) {
                n++;
            }
        }
        return n;
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static String describeStock(Settlement settlement) {
        if (settlement.stock().isEmpty()) {
            return "empty";
        }
        StringBuilder sb = new StringBuilder();
        for (var entry : settlement.stock().entrySet()) {
            Item item = entry.getKey();
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(com.syang.placitum.data.PlacitumCodecs.itemId(item).getPath())
                    .append(' ')
                    .append(entry.getValue());
        }
        return sb.toString();
    }

    private static BlockPos findNearestBell(ServerLevel level, BlockPos origin) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-32, -16, -32), origin.offset(32, 16, 32))) {
            if (!(level.getBlockState(pos).getBlock() instanceof BellBlock)) {
                continue;
            }
            double dist = pos.distSqr(origin);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos.immutable();
            }
        }
        return best;
    }
}
