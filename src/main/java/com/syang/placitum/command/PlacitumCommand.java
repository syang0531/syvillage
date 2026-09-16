package com.syang.placitum.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.syang.placitum.build.BuildPlanner;
import com.syang.placitum.build.GridMap;
import com.syang.placitum.data.BuildJob;
import com.syang.placitum.data.CellPos;
import com.syang.placitum.data.CellState;
import com.syang.placitum.build.GridSurvey;
import com.syang.placitum.data.Assignment;
import com.syang.placitum.data.AlertState;
import com.syang.placitum.data.ChronicleEntry;
import com.syang.placitum.data.EntryType;
import com.syang.placitum.data.Resident;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.data.SimClock;
import com.syang.placitum.data.SettlementId;
import com.syang.placitum.defense.AlertMachine;
import com.syang.placitum.defense.Armoury;
import com.syang.placitum.defense.DefenseRating;
import com.syang.placitum.defense.RaidResolver;
import com.syang.placitum.event.PlacitumEvents;
import com.syang.placitum.lifecycle.LifecycleManager;
import com.syang.placitum.population.Capacity;
import com.syang.placitum.population.Demographics;
import com.syang.placitum.settlement.PoiRepair;
import com.syang.placitum.settlement.Registration;
import com.syang.placitum.config.PlacitumConfig;
import com.syang.placitum.sim.SimParams;
import com.syang.placitum.sim.Simulation;
import com.syang.placitum.store.SettlementManager;
import com.syang.placitum.store.SettlementMut;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
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

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext buildContext) {
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

        root.then(Commands.literal("stock")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                .then(Commands.argument("count", IntegerArgumentType.integer(-4096, 4096))
                                        .executes(ctx -> stock(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                ItemArgument.getItem(ctx, "item").item().value(),
                                                IntegerArgumentType.getInteger(ctx, "count")))))));

        root.then(Commands.literal("debug")
                .then(Commands.literal("house")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> debugHouse(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("rehouse")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> debugRehouse(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("rewall")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> debugRewall(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("growth")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> debugGrowth(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"))))));

        root.then(Commands.literal("plot")
                .then(Commands.literal("show")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> plotShow(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("survey")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> plotSurvey(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("block")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("gx", IntegerArgumentType.integer(-64, 64))
                                        .then(Commands.argument("gz", IntegerArgumentType.integer(-64, 64))
                                                .executes(ctx -> plotMark(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        IntegerArgumentType.getInteger(ctx, "gx"),
                                                        IntegerArgumentType.getInteger(ctx, "gz"),
                                                        true))))))
                .then(Commands.literal("unblock")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("gx", IntegerArgumentType.integer(-64, 64))
                                        .then(Commands.argument("gz", IntegerArgumentType.integer(-64, 64))
                                                .executes(ctx -> plotMark(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        IntegerArgumentType.getInteger(ctx, "gx"),
                                                        IntegerArgumentType.getInteger(ctx, "gz"),
                                                        false)))))));

        root.then(Commands.literal("build")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> buildStatus(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        root.then(Commands.literal("verify")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> verify(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        root.then(Commands.literal("chronicle")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> chronicle(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id"), 10))
                        .then(Commands.argument("lines", IntegerArgumentType.integer(1, 200))
                                .executes(ctx -> chronicle(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        IntegerArgumentType.getInteger(ctx, "lines"))))));

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
                SimParams.fromConfig(source.getServer().overworld()), now);
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
                + " (now " + now + ")"
                + (settled.lastSimTick() > now ? "  [clock ahead of the world; dormant]" : "")), false);
        source.sendSuccess(() -> Component.literal("  defence rating " + DefenseRating.of(settled)
                + " (militia " + DefenseRating.eligibleCount(settled)
                + ", weapons " + Armoury.armableCount(settled)
                + ", gear tier " + Armoury.bestAvailableTier(settled)
                + ", watch points " + settled.anchors().watchPoints().size()
                + ", shelters " + settled.anchors().shelters().size() + ")"), false);
        source.sendSuccess(() -> Component.literal("  alert " + settled.alert()
                + ", plots " + settled.plots().size()
                + ", beds " + settled.bedCount()
                + (settled.plots().isEmpty() ? "  (from village beds; M3 builds its own)" : "")), false);

        // The wall is the one thing M3 actually finishes, and until now nothing said so. A
        // settlement that built a palisade and cannot tell you it has one is back where it
        // started: something happened and you have no way to know what.
        var wall = settled.defense().wall();
        source.sendSuccess(() -> Component.literal("  wall " + wall.tier()
                + (wall.tier() == com.syang.placitum.data.WallTier.NONE ? ""
                        : ", " + wall.ring().size() + " post(s), " + wall.gates().size()
                                + " gate(s)" + (wall.intact() ? "" : ", breached"))), false);
        source.sendSuccess(() -> Component.literal("  stock " + describeStock(settled)), false);
        if (!SimParams.fromConfig(source.getServer().overworld()).hostilesExist()) {
            source.sendSuccess(() -> Component.literal(
                    "  raids disabled - the world is on Peaceful").withStyle(ChatFormatting.GRAY), false);
        }
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
        long now = source.getServer().overworld().getGameTime();
        long target = settlement.lastSimTick() + ticks;
        Settlement stepped = Simulation.catchUp(seed, settlement, SimParams.fromConfig(source.getServer().overworld()), target);

        // Put the clock back where the world is. The steps really happened - stock moved, the
        // chronicle was written - but pretending the world also moved leaves lastSimTick ahead
        // of game time, and then elapsed is negative and the settlement sleeps until the world
        // catches up. One /placitum tick 20000 put a village to sleep for fifteen real minutes.
        Settlement advanced = stepped.withClock(
                new SimClock(Math.min(stepped.lastSimTick(), now), stepped.simStep()));
        manager.put(advanced);

        long steps = advanced.simStep() - settlement.simStep();
        source.sendSuccess(() -> Component.literal("Advanced " + ticks + " tick(s) = " + steps
                + " step(s). Stock: " + describeStock(advanced)), true);

        // "Nothing happened and I do not know why" is the exact experience this mod exists to
        // remove, so the debug command has to answer it rather than leave the player guessing.
        int materialized = advanced.materializedCount();
        if (materialized > 0) {
            source.sendSuccess(() -> Component.literal("  " + materialized + " of "
                    + advanced.residentCount() + " resident(s) are MATERIALIZED, so the modules"
                    + " that would count them twice sat this out - they farm, eat and die as"
                    + " real entities instead."), false);
            source.sendSuccess(() -> Component.literal("  Run /placitum demote "
                    + shortId(advanced.id()) + " first to see the virtual formulas run."), false);
        }
        int farmers = countJob(advanced, Assignment.FARMER);
        source.sendSuccess(() -> Component.literal("  " + farmers + " farmer(s), "
                + advanced.population() + " mouth(s) to feed"), false);

        // "I ran a thousand steps and no raid happened" has exactly two explanations and the
        // player cannot tell them apart from the outside. Say which one it is.
        SimParams params = SimParams.fromConfig(source.getServer().overworld());
        if (!params.hostilesExist()) {
            source.sendSuccess(() -> Component.literal(
                    "  No raids rolled: the world is on Peaceful, so none could happen for real"
                            + " either."), false);
        } else {
            double perDay = PlacitumConfig.RAID_CHANCE_PER_STEP.get() * params.stepsPerDay();
            source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                    "  raid chance %.4f/step (about %.2f a game day)",
                    PlacitumConfig.RAID_CHANCE_PER_STEP.get(), perDay)), false);
        }
        return (int) steps;
    }

    /**
     * Puts things into, or takes them out of, the settlement stores.
     *
     * <p>Stands in for the warehouse block until construction builds one. Without some way in,
     * the militia can never be armed - production only ever makes food - so "ring the bell and
     * watch them muster" would be untestable and, worse, unreachable in an actual game.
     *
     * <p>Negative counts take things out, which is the other half the warehouse will need.
     */
    private static int stock(CommandSourceStack source, String rawId, Item item, int count) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        SettlementMut mut = SettlementMut.of(settlement);
        if (count >= 0) {
            mut.addStock(item, count);
        } else {
            mut.takeStock(item, -count);
        }
        Settlement next = mut.freeze();
        manager.put(next);

        String name = com.syang.placitum.data.PlacitumCodecs.itemId(item).getPath();
        source.sendSuccess(() -> Component.literal(next.name() + " stores: " + name + " "
                + next.stockOf(item) + " (" + (count >= 0 ? "+" : "") + count + ")"), true);
        return next.stockOf(item);
    }

    /**
     * The settlement's history, most recent last.
     *
     * <p>The whole argument of this mod is that a death nobody can account for is worse than
     * the death. Recording causes and giving no way to read them would be the same failure
     * wearing a tidier face. The bell UI in M2 shows this; until then it is a command.
     */
    private static int chronicle(CommandSourceStack source, String rawId, int lines) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        List<ChronicleEntry> entries = settlement.chronicle().recent(lines);
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal(settlement.name()
                    + " has no history yet"), false);
            return 0;
        }
        // Measured against the settlement's own clock, not the world's. The two drift apart
        // as soon as /placitum tick is used, and "3 days ago" computed across the gap is a lie.
        long now = settlement.lastSimTick();
        source.sendSuccess(() -> Component.literal(settlement.name() + " - last "
                + entries.size() + " entries").withStyle(ChatFormatting.GOLD), false);
        for (ChronicleEntry entry : entries) {
            source.sendSuccess(() -> Component.literal("  " + ago(now, entry.gameTime()) + "  "
                    + entry.type() + "  " + entry.subject()
                    + (entry.detail().isEmpty() ? "" : " - " + entry.detail()))
                    .withStyle(styleFor(entry.type())), false);
        }
        return entries.size();
    }

    /** Ages read better than raw tick counts when the question is "what happened here". */
    private static String ago(long now, long then) {
        long ticks = Math.max(0, now - then);
        long days = ticks / 24000L;
        if (days > 0) {
            return days + "d ago";
        }
        long minutes = ticks / 1200L;
        return minutes > 0 ? minutes + "m ago" : "just now";
    }

    private static ChatFormatting styleFor(EntryType type) {
        return switch (type) {
            case DEATH, RAID_LOST, FAMINE, ZOMBIFIED -> ChatFormatting.RED;
            case BIRTH, RAID_REPELLED, CURED, SCALE_UP -> ChatFormatting.GREEN;
            case SCALE_DOWN -> ChatFormatting.YELLOW;
            default -> ChatFormatting.GRAY;
        };
    }

    /**
     * Repairs claims left behind by entities that no longer exist.
     *
     * <p>Earlier builds discarded villagers without releasing their beds, so a village visited a
     * few times ended up with every bed held by a ghost - nobody could sleep, and M2 would never
     * have allowed a birth there. That is fixed at source now, but the claims already made do
     * not undo themselves, and a player is owed a way back that is not "start a new village".
     */
    /**
     * Why the settlement is not growing.
     *
     * <p>Built early rather than last, on the strength of M0: the tuning command that reported
     * a number without saying why it was that number cost an hour of chasing a bug that was not
     * there. Population has three possible answers and no way to guess between them from the
     * outside, so the tool names the binding one.
     */
    private static int debugGrowth(CommandSourceStack source, String rawId) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        SimParams params = SimParams.fromConfig(source.getServer().overworld());
        Capacity capacity = Capacity.of(settlement, params);
        SettlementMut mut = SettlementMut.of(settlement);

        int population = settlement.population();
        source.sendSuccess(() -> Component.literal(settlement.name() + " (" + settlement.scale()
                + ", population " + population + ")").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("  capacity " + capacity.value()
                + "  <-  " + describe(capacity)), false);

        int consumption = population * params.consumptionPerHead();
        int production = countJob(settlement, Assignment.FARMER) * params.yieldRate();
        int stock = settlement.stockOf(net.minecraft.world.item.Items.WHEAT);
        source.sendSuccess(() -> Component.literal("  food " + stock
                + " (produces " + production + "/step, eats " + consumption + "/step"
                + foodRunway(stock, production, consumption) + ")"), false);

        double chance = Demographics.birthChance(mut, capacity, params);
        int pairs = Demographics.fertilePairs(mut, params);
        source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                "  next birth %.4f/step (about %.2f a game day), %d fertile pair(s)",
                chance, chance * params.stepsPerDay(), pairs)), false);

        if (chance <= 0.0) {
            source.sendSuccess(() -> Component.literal("  not growing: " + whyNot(
                    population, capacity, pairs)).withStyle(ChatFormatting.YELLOW), false);
        }
        if (settlement.defense().famineSteps() > 0) {
            source.sendSuccess(() -> Component.literal("  stores have been empty for "
                    + settlement.defense().famineSteps() + " step(s)")
                    .withStyle(ChatFormatting.RED), false);
        }
        return capacity.value();
    }

    /** The three numbers side by side, with the binding one marked. */
    private static String describe(Capacity capacity) {
        Capacity.Bottleneck limit = capacity.bottleneck();
        return "beds " + capacity.beds() + mark(limit == Capacity.Bottleneck.BEDS)
                + " / food " + capacity.food() + mark(limit == Capacity.Bottleneck.FOOD)
                + " / safety " + capacity.safety() + mark(limit == Capacity.Bottleneck.SAFETY);
    }

    private static String mark(boolean binding) {
        return binding ? " <" : "";
    }

    private static String foodRunway(int stock, int production, int consumption) {
        int net = production - consumption;
        if (net >= 0) {
            return ", net +" + net;
        }
        return ", " + (stock / -net) + " step(s) left at this rate";
    }

    private static String whyNot(int population, Capacity capacity, int pairs) {
        if (pairs == 0) {
            return "no fertile pairs - everyone is a child, elderly or zombified";
        }
        return "population " + population + " is at capacity " + capacity.value()
                + ", limited by " + capacity.bottleneck().name().toLowerCase(java.util.Locale.ROOT);
    }

    private static int verify(CommandSourceStack source, String rawId) {
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
        if (settlement.materializedCount() == 0) {
            source.sendFailure(Component.literal(
                    "Nobody is materialized, so their beds cannot be checked. Stand in the village."));
            return 0;
        }

        PoiRepair.Result result = PoiRepair.run(level, manager, settlement);
        source.sendSuccess(() -> Component.literal(settlement.name() + ": checked "
                + result.scanned() + " occupied bed(s), freed " + result.freed()
                + " held by nobody")
                .withStyle(result.freed() > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
        if (result.freed() > 0) {
            source.sendSuccess(() -> Component.literal(
                    "  Villagers will claim them again over the next day."), false);
        }
        return result.freed();
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
                + " vs rating " + rating + String.format(java.util.Locale.ROOT,
                        " (formula says %.1f%% to hold)",
                        100.0 * RaidResolver.chanceToHold(rating, threat)))
                .withStyle(ChatFormatting.GOLD), false);
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
            String weapon = r.gear().weapon()
                    .map(w -> "  ARMED(" + com.syang.placitum.data.PlacitumCodecs.itemId(w).getPath() + ")")
                    .orElse("");
            String line = r.lineage().fullName()
                    + "  " + r.assignment().job().getPath()
                    + "  " + r.stage()
                    + "  age " + r.ageDays()
                    + "  hp " + r.vitals().health()
                    + "  " + r.state()
                    + weapon
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
    /** Prints the grid. The tool docs/open-questions.md asks for before M3 commits to a grid. */
    private static int plotShow(CommandSourceStack source, String rawId) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        if (settlement.grid().cells().isEmpty()) {
            source.sendFailure(Component.literal(settlement.name()
                    + " has never been surveyed. Stand in it and run /placitum plot survey "
                    + rawId));
            return 0;
        }
        for (Component line : GridMap.render(settlement)) {
            source.sendSuccess(() -> line, false);
        }
        return settlement.grid().countOf(CellState.FREE);
    }

    /**
     * Re-reads the world into the grid.
     *
     * <p>Normally this rides along with the anchor refresh; the command exists because a survey
     * is only as good as the chunks that were loaded when it ran, and after building something
     * the player wants the answer now rather than at the next refresh.
     */
    private static int plotSurvey(CommandSourceStack source, String rawId) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        ServerLevel level = source.getServer().getLevel(settlement.identity().dimension());
        if (level == null) {
            source.sendFailure(Component.literal("That dimension is not loaded"));
            return 0;
        }
        GridSurvey.Result result = GridSurvey.run(level, settlement);
        Settlement updated = settlement.withGrid(result.grid());
        manager.put(updated);

        source.sendSuccess(() -> Component.literal("Surveyed " + result.scanned() + " cell(s) of "
                + settlement.name()), false);
        if (!result.complete()) {
            // Saying "surveyed" and stopping would make a half-read grid look like a whole one.
            source.sendSuccess(() -> Component.literal("  " + result.skipped()
                            + " cell(s) skipped - those chunks are not loaded. Walk the edges"
                            + " of the village and run this again.")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        for (Component line : GridMap.render(updated)) {
            source.sendSuccess(() -> line, false);
        }
        reportBlocking(source, result);
        return result.scanned();
    }

    /**
     * Why cells were refused, and what a different threshold would have bought.
     *
     * <p>Printed only when something was actually blocked. A report that says "0 blocked by
     * slope" underneath a grid with no blocked cells in it is noise, and this milestone has
     * already been taught what log spam costs.
     */
    private static void reportBlocking(CommandSourceStack source, GridSurvey.Result result) {
        if (result.blockedWet() + result.blockedSlope() + result.forbidden() == 0) {
            return;
        }
        source.sendSuccess(() -> Component.literal("  blocked by water " + result.blockedWet()
                + ", by slope " + result.blockedSlope()
                + (result.forbidden() > 0
                        ? "   (" + result.forbidden() + " forbidden, left alone)" : "")), false);

        int current = PlacitumConfig.MAX_CELL_SLOPE.get();
        StringBuilder ladder = new StringBuilder("  free at maxCellSlope");
        for (int i = 0; i < GridSurvey.SLOPE_LADDER.length; i++) {
            int threshold = GridSurvey.SLOPE_LADDER[i];
            ladder.append("  ").append(threshold)
                    .append(threshold == current ? "*" : "")
                    .append(":").append(result.freeAtSlope()[i]);
        }
        source.sendSuccess(() -> Component.literal(ladder.toString())
                .withStyle(ChatFormatting.DARK_GRAY), false);
    }

    /**
     * Marks a cell off limits, or lets it back in.
     *
     * <p>The survey guess at what counts as a building is a heuristic and will be wrong
     * somewhere. This is the override, and {@link GridSurvey} deliberately never overwrites a
     * BLOCKED cell so that the answer given here outlives the next refresh.
     */
    private static int plotMark(CommandSourceStack source, String rawId, int gx, int gz,
            boolean block) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        CellPos cell = new CellPos(gx, gz);
        int radius = (settlement.grid().size() - 1) / 2;
        if (Math.abs(gx) > radius || Math.abs(gz) > radius) {
            source.sendFailure(Component.literal("Cell " + cell.toKey() + " is outside the grid"
                    + " (+/-" + radius + " at " + settlement.scale() + ")"));
            return 0;
        }
        // Unblocking restores FREE rather than what was there before. The survey will correct
        // it on the next pass, and guessing here would mean keeping a second history to be
        // wrong about.
        manager.put(settlement.withGrid(settlement.grid().with(cell,
                block ? CellState.FORBIDDEN : CellState.FREE)));
        source.sendSuccess(() -> Component.literal((block ? "Blocked " : "Unblocked ")
                + cell.toKey() + " - world position "
                + settlement.grid().blockAt(cell).toShortString()), false);
        return 1;
    }

    /**
     * What is on the stocks.
     *
     * <p>Stage 1-4 of the pipeline place no blocks at all, so without this there is no way to
     * tell a wall being built virtually from a wall order that silently did nothing. Every
     * stage here answers "why is it not finished yet" with something specific.
     */
    private static int buildStatus(CommandSourceStack source, String rawId) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(settlement.name() + " - build queue")
                .withStyle(ChatFormatting.GOLD), false);

        if (settlement.buildQueue().isEmpty()) {
            source.sendSuccess(() -> Component.literal("  nothing queued - "
                    + whyNothingQueued(settlement, source)).withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        for (BuildJob job : settlement.buildQueue()) {
            int total = BuildPlanner.expand(job.recipe()).size();
            String cost = job.cost().isEmpty() ? "not costed yet" : describeCost(job);
            // Where, not just what. A cottage went up eighteen blocks down a slope and read as
            // "no house is being built" from the middle of the village.
            source.sendSuccess(() -> Component.literal("  " + job.recipe().template().getPath()
                    + "  " + job.stage() + "  " + job.progress() + "/" + total
                    + " blocks  (" + cost + ")  at " + job.recipe().anchor().toShortString()),
                    false);
            source.sendSuccess(() -> Component.literal("    " + explain(job, total)
                            + (job.stage() == com.syang.placitum.data.BuildStage.EXECUTING
                                    ? embodiedNote(settlement) + loadedNote(source, settlement, job)
                                    : "")
                            + (job.stage() == com.syang.placitum.data.BuildStage.WAITING_MATERIALS
                                    ? shortageNote(settlement) : ""))
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }
        return settlement.buildQueue().size();
    }

    /** The stage, in words that name what has to happen next. */
    private static String explain(BuildJob job, int total) {
        return switch (job.stage()) {
            case PLANNED, RESERVED -> "waiting for somebody to look at the ground -"
                    + " the recipe cannot be frozen from a settlement nobody is standing in";
            case QUEUED -> "recipe frozen, about to draw materials";
            case WAITING_MATERIALS -> "short of materials - put them in the settlement stores";
            case EXECUTING -> total > 0
                    ? "building; " + (total - job.progress()) + " blocks to go"
                    : "expands to nothing, which should not happen";
            case COMPLETE -> "done";
        };
    }

    /**
     * Why the stores are not filling while you stand there.
     *
     * <p>Production skips embodied residents - they are meant to be working as real entities -
     * so a village you are standing in cuts no timber. Waiting for materials that cannot arrive
     * until you leave is a stall like any other, and it gets said rather than guessed at.
     */
    private static String shortageNote(Settlement settlement) {
        int materialized = settlement.materializedCount();
        return materialized == 0 ? "" : "  (" + materialized + " resident(s) are embodied, so"
                + " nothing is being produced either - walk away, or bring the materials)";
    }

    /**
     * Whether the next block is somewhere the server can currently write.
     *
     * <p>Progress is a strict index, so one op in an unloaded chunk blocks everything behind it
     * until somebody walks over there. That is a stall with a cause and a cure, and this
     * milestone has now spent three rounds on stalls that looked identical from outside.
     */
    private static String loadedNote(CommandSourceStack source, Settlement settlement,
            BuildJob job) {
        ServerLevel level = source.getServer().getLevel(settlement.identity().dimension());
        if (level == null) {
            return "";
        }
        java.util.List<com.syang.placitum.data.BuildOp> ops = BuildPlanner.expand(job.recipe());
        if (job.progress() >= ops.size()) {
            return "";
        }
        BlockPos next = ops.get(job.progress()).pos();
        return level.isLoaded(next) ? ""
                : "  (next block " + next.toShortString() + " is in an unloaded chunk - walk"
                        + " that way, or raise your simulation distance)";
    }

    /**
     * Why a job that is EXECUTING is not moving.
     *
     * <p>Progress stops dead while the residents have bodies, because laying blocks in a
     * settlement somebody is standing in is the builder's job and the builder is stage 5. That
     * is a design decision and it looks exactly like a bug, so it gets said out loud.
     */
    private static String embodiedNote(Settlement settlement) {
        int materialized = settlement.materializedCount();
        return materialized == 0 ? "" : "  (" + materialized + " resident(s) are embodied, so"
                + " virtual building is paused - walk away or /placitum demote to see it move)";
    }

    private static String describeCost(BuildJob job) {
        StringBuilder out = new StringBuilder();
        for (var entry : job.cost().entrySet()) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(entry.getValue()).append(" ")
                    .append(entry.getKey().getDescriptionId().replaceAll(".*\\.", ""));
        }
        return out.toString();
    }

    /**
     * Why an empty queue is empty.
     *
     * <p>An empty queue has several causes that look identical, which is the failure this
     * project keeps paying for. Each one here is a different thing for the player to do.
     */
    private static String whyNothingQueued(Settlement settlement, CommandSourceStack source) {
        if (settlement.defense().wall().tier() != com.syang.placitum.data.WallTier.NONE) {
            return "it already has a " + settlement.defense().wall().tier();
        }
        SimParams params = SimParams.fromConfig(source.getServer().overworld());
        if (settlement.population() < params.wallMinPopulation()
                && settlement.alert() == com.syang.placitum.data.AlertState.PEACE) {
            return "population " + settlement.population() + " is below the wall threshold of "
                    + params.wallMinPopulation() + ", and nothing is attacking";
        }
        Capacity capacity = Capacity.of(settlement, params);
        if (capacity.bottleneck() == Capacity.Bottleneck.BEDS
                && com.syang.placitum.build.HousePlanner.findSite(settlement).isEmpty()) {
            return "it wants a house and has nowhere to put one - no free cell beside a road"
                    + " within " + settlement.scale().buildRadiusCells() + " cell(s) of the bell."
                    + " /placitum plot show marks what is free";
        }
        return "the need has not been noticed yet - it is checked once a simulation step,"
                + " and a step is " + SimParams.fromConfig(source.getServer().overworld())
                        .stepTicks() + " ticks";
    }

    /**
     * Forgets the wall so the settlement plans a new one.
     *
     * <p>Only the record, never the blocks - the logs stay where they were put. Planning happens
     * once and then never again while a wall stands, which is right in play and useless for
     * testing a change to how walls are planned. Demolition and salvage are M4.
     */
    private static int debugRewall(CommandSourceStack source, String rawId) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        int posts = settlement.defense().wall().ring().size();
        manager.put(settlement
                .withDefense(settlement.defense().withWall(com.syang.placitum.data.WallState.NONE))
                .withBuildQueue(java.util.List.of()));
        source.sendSuccess(() -> Component.literal("Forgot " + settlement.name()
                + "'s wall (" + posts + " post(s)). The blocks are still standing; it will plan"
                + " a new one."), true);
        return posts;
    }

    /**
     * Reads a finished house back out of the world.
     *
     * <p>Screenshots are how the last three shape bugs were found, which means they were found
     * by luck and described in prose. A house is a column of blocks and the server can simply
     * say what they are - whether there is a roof over it, whether the door is where a villager
     * could reach it, whether the whole thing is buried.
     */
    private static int debugHouse(CommandSourceStack source, String rawId) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        ServerLevel level = source.getServer().getLevel(settlement.identity().dimension());
        if (level == null) {
            source.sendFailure(Component.literal("That dimension is not loaded"));
            return 0;
        }
        int houses = 0;
        for (com.syang.placitum.data.Plot plot : settlement.plots().values()) {
            if (plot.kind() != com.syang.placitum.data.PlotKind.HOUSE) {
                continue;
            }
            houses++;
            describeHouse(source, level, settlement, plot);
        }
        if (houses == 0) {
            source.sendSuccess(() -> Component.literal(settlement.name()
                    + " has built no houses yet").withStyle(ChatFormatting.GRAY), false);
        }
        return houses;
    }

    /** A north-south slice through the middle of the plot, drawn from the world. */
    private static void describeHouse(CommandSourceStack source, ServerLevel level,
            Settlement settlement, com.syang.placitum.data.Plot plot) {
        BlockPos northWest = settlement.grid().blockAt(plot.anchor());
        int side = com.syang.placitum.build.CottagePlan.SIDE;
        int middleX = northWest.getX() + side / 2;

        int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types
                .MOTION_BLOCKING_NO_LEAVES, middleX, northWest.getZ() + side / 2);
        int base = surface - com.syang.placitum.build.CottagePlan.HEIGHT - 2;

        source.sendSuccess(() -> Component.literal(plot.template().getPath() + " on cell "
                        + plot.anchor().toKey() + " at " + northWest.toShortString()
                        + ", " + plot.bedCount() + " bed(s)").withStyle(ChatFormatting.GOLD),
                false);

        for (int y = surface + 2; y >= base; y--) {
            StringBuilder row = new StringBuilder();
            for (int dz = 0; dz < side; dz++) {
                row.append(glyphAt(level, new BlockPos(middleX, y, northWest.getZ() + dz)));
            }
            int atY = y;
            source.sendSuccess(() -> Component.literal("  " + atY + "  " + row), false);
        }
        source.sendSuccess(() -> Component.literal(
                        "  north -> south through the middle.  . air  # solid  D door"
                                + "  W window  B bed  t torch")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        verdict(source, level, settlement, plot, northWest, side);
    }

    /**
     * What the house has, counted over the whole footprint rather than one slice.
     *
     * <p>The slice is for looking; this is for judging. A five-wide cottage puts its beds either
     * side of the middle, so the slice through the middle shows neither of them - the drawing
     * said nothing and I read that as evidence, which is the mistake this whole project keeps
     * paying for.
     */
    private static void verdict(CommandSourceStack source, ServerLevel level,
            Settlement settlement, com.syang.placitum.data.Plot plot, BlockPos northWest,
            int side) {
        int doors = 0;
        int beds = 0;
        int floorY = Integer.MAX_VALUE;
        for (int dx = 0; dx < side; dx++) {
            for (int dz = 0; dz < side; dz++) {
                for (int dy = 0; dy < com.syang.placitum.build.CottagePlan.HEIGHT + 4; dy++) {
                    BlockPos at = northWest.offset(dx, 0, dz)
                            .atY(level.getHeight(net.minecraft.world.level.levelgen.Heightmap
                                    .Types.MOTION_BLOCKING_NO_LEAVES,
                                    northWest.getX() + dx, northWest.getZ() + dz) - dy);
                    var state = level.getBlockState(at);
                    if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                        doors++;
                    }
                    if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
                        beds++;
                        floorY = Math.min(floorY, at.getY());
                    }
                }
            }
        }
        int doorHalves = doors;
        int bedHalves = beds;
        source.sendSuccess(() -> Component.literal("  found " + doorHalves + " door half/halves"
                        + " (want 2) and " + bedHalves + " bed half/halves (want "
                        + plot.bedCount() * 2 + ")")
                .withStyle(doorHalves == 2 && bedHalves == plot.bedCount() * 2
                        ? ChatFormatting.GREEN : ChatFormatting.RED), false);
    }

    private static String glyphAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return "?";
        }
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return ".";
        }
        if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
            return "D";
        }
        if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
            return "B";
        }
        if (state.is(net.minecraft.world.level.block.Blocks.TORCH)
                || state.is(net.minecraft.world.level.block.Blocks.WALL_TORCH)) {
            return "t";
        }
        if (state.is(net.minecraft.world.level.block.Blocks.GLASS_PANE)) {
            return "W";
        }
        if (state.is(net.minecraft.world.level.block.Blocks.OAK_PLANKS)) {
            return "#";
        }
        if (state.is(net.minecraft.world.level.block.Blocks.COBBLESTONE)) {
            return "c";
        }
        return "o";
    }

    /**
     * Forgets every house, so the settlement plans new ones.
     *
     * <p>Only the records and the cells they reserved - the blocks stay standing. Like rewall,
     * this exists because planning happens once and a change to how houses are planned is
     * otherwise untestable without finding a fresh village every time.
     */
    private static int debugRehouse(CommandSourceStack source, String rawId) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        java.util.Map<java.util.UUID, com.syang.placitum.data.Plot> kept =
                new java.util.LinkedHashMap<>();
        com.syang.placitum.data.PlotGrid grid = settlement.grid();
        int forgotten = 0;
        for (var entry : settlement.plots().entrySet()) {
            if (entry.getValue().kind() == com.syang.placitum.data.PlotKind.HOUSE) {
                // The cell stays BUILT, because the house is still standing on it. Freeing it
                // invited the settlement to build a second house on the first one's roof.
                forgotten++;
            } else {
                kept.put(entry.getKey(), entry.getValue());
            }
        }
        int count = forgotten;
        manager.put(settlement.withPlots(kept).withGrid(grid).withBuildQueue(java.util.List.of()));
        source.sendSuccess(() -> Component.literal("Forgot " + count + " house(s) of "
                + settlement.name() + ". The blocks are still standing; it will plan new ones."),
                true);
        return count;
    }

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
