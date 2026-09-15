package com.syang.placitum.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.syang.placitum.data.Assignment;
import com.syang.placitum.data.AlertState;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.data.SettlementId;
import com.syang.placitum.defense.AlertMachine;
import com.syang.placitum.defense.Armoury;
import com.syang.placitum.defense.DefenseRating;
import com.syang.placitum.defense.RaidResolver;
import com.syang.placitum.event.PlacitumEvents;
import com.syang.placitum.lifecycle.LifecycleManager;
import com.syang.placitum.settlement.Registration;
import com.syang.placitum.sim.SimParams;
import com.syang.placitum.sim.Simulation;
import com.syang.placitum.store.SettlementManager;
import com.syang.placitum.store.SettlementMut;
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

        root.then(Commands.literal("alert")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("state", StringArgumentType.word())
                                .executes(ctx -> alert(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        StringArgumentType.getString(ctx, "state"))))));

        root.then(Commands.literal("simulate")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("raid")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("threat", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("trials", IntegerArgumentType.integer(1, 100000))
                                                .executes(ctx -> simulateRaid(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        IntegerArgumentType.getInteger(ctx, "threat"),
                                                        IntegerArgumentType.getInteger(ctx, "trials"))))))));

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
        if (level != null) {
            // Release, never demote. Demote discards the entity; here the record is what goes
            // away, so discarding would delete the villagers from the world.
            LifecycleManager.releaseAll(level, manager, settlement);
        }
        PlacitumEvents.lifecycle().forget(settlement.id());
        manager.unregister(settlement.id());
        String name = settlement.name();
        int freed = settlement.residentCount();
        source.sendSuccess(() -> Component.literal("Unregistered " + name + " - " + freed
                + " villager(s) handed back to vanilla, buildings untouched"), true);
        source.sendSuccess(() -> Component.literal(
                "  The old data file stays on disk but is no longer linked;"
                        + " registering this bell again starts a fresh settlement."), false);
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
        source.sendSuccess(() -> Component.literal("  defence rating " + DefenseRating.of(settled)
                + " (militia " + DefenseRating.eligibleCount(settled)
                + ", weapons " + Armoury.armableCount(settled)
                + ", gear tier " + Armoury.bestAvailableTier(settled)
                + ", watch points " + settled.anchors().watchPoints().size()
                + ", shelters " + settled.anchors().shelters().size() + ")"), false);
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

    private static int alert(CommandSourceStack source, String rawId, String rawState) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        AlertState target;
        try {
            target = AlertState.valueOf(rawState.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Alert states: peace, alert, combat, rout"));
            return 0;
        }
        ServerLevel level = source.getServer().getLevel(settlement.dimension());
        if (level == null) {
            source.sendFailure(Component.literal("That dimension is not loaded"));
            return 0;
        }
        Settlement next = AlertMachine.raise(settlement, target, level.getGameTime(), "set by command");
        manager.put(next);
        source.sendSuccess(() -> Component.literal(next.name() + " is now " + next.alert()), true);
        return 1;
    }

    /**
     * Rolls the raid formula many times and reports what it says.
     *
     * <p>The tuning tool docs/testing.md section 4 asks for. Nobody can derive these
     * coefficients; the procedure is to measure real fights and bend the formula to match, and
     * that is unbearable one raid at a time.
     */
    private static int simulateRaid(CommandSourceStack source, String rawId, int threat, int trials) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }

        int rating = DefenseRating.of(settlement);
        int repelled = 0;
        long casualties = 0;
        long seed = source.getServer().overworld().getSeed();

        for (int i = 0; i < trials; i++) {
            // A fresh copy each trial: the formula kills people and loots the stores, and a
            // thousand trials on one settlement would leave a ghost town.
            SettlementMut trial = SettlementMut.of(settlement);
            RaidResolver.Outcome outcome = RaidResolver.resolve(trial, threat,
                    Simulation.rngFor(seed, settlement.id(), i));
            if (outcome.repelled()) {
                repelled++;
            }
            casualties += outcome.casualties();
        }

        double survivalRate = 100.0 * repelled / trials;
        double avgDead = (double) casualties / trials;
        source.sendSuccess(() -> Component.literal(settlement.name() + ": threat " + threat
                + " vs rating " + rating).withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "  repelled %.1f%% of %d trial(s), %.2f dead on average",
                survivalRate, trials, avgDead)), false);
        source.sendSuccess(() -> Component.literal(
                "  militia " + DefenseRating.eligibleCount(settlement)
                        + ", gear tier " + Armoury.bestAvailableTier(settlement)
                        + ", weapons " + Armoury.armableCount(settlement)
                        + ", wall " + settlement.defense().wall().tier()
                        + ", watch points " + settlement.anchors().watchPoints().size()), false);
        return (int) survivalRate;
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
