package com.syang.placitum.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.syang.placitum.build.BuildPlanner;
import com.syang.placitum.build.GridMap;
import com.syang.placitum.build.GridSurvey;
import com.syang.placitum.build.LampPlan;
import com.syang.placitum.data.BuildJob;
import com.syang.placitum.data.CellPos;
import com.syang.placitum.data.CellState;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.data.SettlementId;
import com.syang.placitum.settlement.Registration;
import com.syang.placitum.store.SettlementManager;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * The debug surface.
 *
 * <p>Much smaller than it was, because most of what it used to report - population, capacity,
 * food, morale, defence rating, growth - no longer exists to report. What is left answers the
 * two questions a player can actually act on: what is this settlement building, and why is it
 * not building anything.
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

        root.then(Commands.literal("build")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> build(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        root.then(Commands.literal("light")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> light(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

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
                                        .then(Commands.argument("gz",
                                                IntegerArgumentType.integer(-64, 64))
                                                .executes(ctx -> plotMark(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        IntegerArgumentType.getInteger(ctx, "gx"),
                                                        IntegerArgumentType.getInteger(ctx, "gz"),
                                                        true)))))));

        dispatcher.register(root);
    }

    private static int register(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos bell = nearestBell(level, BlockPos.containing(source.getPosition()));
        if (bell == null) {
            source.sendFailure(Component.literal("No bell within 32 blocks"));
            return 0;
        }
        SettlementManager manager = SettlementManager.get(source.getServer());
        Registration.Result result = Registration.register(level, manager, bell);

        switch (result) {
            case Registration.Result.Success success -> source.sendSuccess(() ->
                    Component.literal("Registered " + success.settlement().name())
                            .withStyle(ChatFormatting.GREEN), true);
            case Registration.Result.AlreadyRegistered already -> source.sendFailure(
                    Component.literal("This bell already belongs to " + already.name()));
            case Registration.Result.Overlaps overlaps -> source.sendFailure(
                    Component.literal(overlaps.otherName() + " is only " + overlaps.distance()
                            + " blocks away; " + overlaps.required() + " is the minimum"));
        }
        return result instanceof Registration.Result.Success ? 1 : 0;
    }

    private static int unregister(CommandSourceStack source, String rawId) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        String name = settlement.name();
        manager.remove(settlement.id());
        source.sendSuccess(() -> Component.literal("Unregistered " + name
                + " - the buildings stay where they are"), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        if (manager.listed().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No settlements registered")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        for (SettlementId entry : manager.listed()) {
            source.sendSuccess(() -> Component.literal(SettlementManager.shortId(entry.id())
                    + "  " + entry.name() + "  " + entry.center().toShortString()), false);
        }
        return manager.listed().size();
    }

    private static int info(CommandSourceStack source, String rawId) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        for (Component line : SettlementReport.of(settlement,
                source.getServer().getLevel(settlement.dimension()))) {
            source.sendSuccess(() -> line, false);
        }
        return settlement.houseCount();
    }

    /** What is being built, and where - an invisible building reads as no building at all. */
    private static int build(CommandSourceStack source, String rawId) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        if (settlement.buildQueue().isEmpty()) {
            ServerLevel where = source.getServer().getLevel(settlement.dimension());
            source.sendSuccess(() -> Component.literal(settlement.name()
                    + " is not building anything").withStyle(ChatFormatting.GRAY), false);
            if (where != null) {
                source.sendSuccess(() -> Component.literal("  lots within "
                        + com.syang.placitum.build.TownPlan.radius(settlement) + " cell(s): "
                        + com.syang.placitum.build.Lots.describe(
                                com.syang.placitum.build.Lots.tally(where, settlement))), false);
            }
            return 0;
        }
        for (BuildJob job : settlement.buildQueue()) {
            int total = BuildPlanner.expand(job.recipe()).size();
            source.sendSuccess(() -> Component.literal("  "
                    + job.recipe().template().getPath() + "  " + job.progress() + "/" + total
                    + " at " + job.recipe().anchor().toShortString()), false);
        }
        return settlement.buildQueue().size();
    }

    /**
     * Where mobs can still spawn.
     *
     * <p>The question the mod exists to answer, so it gets a command of its own. Dark cells are
     * where villagers get killed at night, and until now the only way to find out was to stand
     * there after dark and wait.
     */
    private static int light(CommandSourceStack source, String rawId) {
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
        var dark = LampPlan.darkPosts(level, settlement);
        if (dark.isEmpty()) {
            source.sendSuccess(() -> Component.literal(settlement.name()
                            + " is lit: nothing can spawn on its ground")
                    .withStyle(ChatFormatting.GREEN), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(dark.size() + " unlit lamp post(s) in "
                + settlement.name()).withStyle(ChatFormatting.RED), false);
        for (int i = 0; i < Math.min(8, dark.size()); i++) {
            BlockPos where = dark.get(i);
            source.sendSuccess(() -> Component.literal("  " + where.toShortString()), false);
        }
        return dark.size();
    }

    private static int plotShow(CommandSourceStack source, String rawId) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        for (Component line : GridMap.render(settlement)) {
            source.sendSuccess(() -> line, false);
        }
        return settlement.grid().countOf(CellState.FREE);
    }

    private static int plotSurvey(CommandSourceStack source, String rawId) {
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
        GridSurvey.Result result = GridSurvey.run(level, settlement);
        Settlement updated = settlement.withGrid(result.grid());
        manager.put(updated);

        source.sendSuccess(() -> Component.literal("Surveyed " + result.scanned() + " cell(s)"),
                false);
        if (!result.complete()) {
            // Saying "surveyed" and stopping would make a half-read grid look like a whole one.
            source.sendSuccess(() -> Component.literal("  " + result.skipped()
                            + " cell(s) skipped - those chunks are not loaded")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        for (Component line : GridMap.render(updated)) {
            source.sendSuccess(() -> line, false);
        }
        return result.scanned();
    }

    /** The override for everything the survey's guess at "already built on" gets wrong. */
    private static int plotMark(CommandSourceStack source, String rawId, int gx, int gz,
            boolean block) {
        SettlementManager manager = SettlementManager.get(source.getServer());
        Settlement settlement = resolve(manager, rawId).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.literal("No such settlement: " + rawId));
            return 0;
        }
        CellPos cell = new CellPos(gx, gz);
        manager.put(settlement.withGrid(settlement.grid().with(cell,
                block ? CellState.FORBIDDEN : CellState.FREE)));
        source.sendSuccess(() -> Component.literal((block ? "Blocked " : "Unblocked ")
                + cell.toKey() + " - world position "
                + com.syang.placitum.build.TownPlan.lotCorner(cell, settlement.center())
                        .toShortString()), true);
        return 1;
    }

    private static @org.jspecify.annotations.Nullable BlockPos nearestBell(ServerLevel level,
            BlockPos near) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(near.offset(-32, -8, -32),
                near.offset(32, 8, 32))) {
            if (!level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BELL)) {
                continue;
            }
            double distance = pos.distSqr(near);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
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
}
