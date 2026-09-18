package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A settlement: a bell, the ground around it, and what has been built on it.
 *
 * <p>No residents. Villagers are vanilla's - it decides their professions from workstations,
 * breeds them from beds and food, and kills them when something gets in. Every one of those was
 * simulated here once, and the simulation is what made the mod impossible to balance and
 * invisible to play. See docs/why-the-reset.md.
 *
 * <p>What is left is the part a player can see: a village that builds itself roads, lamps,
 * houses and fields, one block at a time, while they watch.
 */
public record Settlement(
        SettlementId identity,
        PlotGrid grid,
        Map<UUID, Plot> plots,
        List<BuildJob> buildQueue,
        Craft craft,
        Stage stage,
        Chronicle chronicle) {

    public static final Codec<Settlement> CODEC = RecordCodecBuilder.create(i -> i.group(
            SettlementId.CODEC.fieldOf("identity").forGetter(Settlement::identity),
            PlotGrid.CODEC.fieldOf("grid").forGetter(Settlement::grid),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Plot.CODEC).fieldOf("plots")
                    .forGetter(Settlement::plots),
            BuildJob.CODEC.listOf().fieldOf("build_queue").forGetter(Settlement::buildQueue),
            // Optional with a default: a settlement saved before there were palettes has to
            // load as a plains town rather than not at all.
            Craft.CODEC.optionalFieldOf("craft", Craft.PLAINS).forGetter(Settlement::craft),
            Stage.CODEC.optionalFieldOf("stage").forGetter(s -> Optional.of(s.stage)),
            // The flag the stage replaced. Still written, so a save opened by the build before
            // this one keeps its wall; still read, so a save from that build loads at the right
            // stage. Drop it one version from now.
            Codec.BOOL.optionalFieldOf("walled", false).forGetter(Settlement::walled),
            Chronicle.CODEC.fieldOf("chronicle").forGetter(Settlement::chronicle)
    ).apply(i, Settlement::load));

    /**
     * A settlement as read from disk, placed at a stage if the save did not say.
     *
     * <p>Older saves have no stage. They have a palette that used to be a ladder and a flag for
     * the wall, and both say where the town got to: a wall means a lord, and the top rung of the
     * ladder was only ever reached with a village head. Everything else had rung a bell.
     */
    private static Settlement load(SettlementId identity, PlotGrid grid, Map<UUID, Plot> plots,
            List<BuildJob> buildQueue, Craft craft, Optional<Stage> stage, boolean walled,
            Chronicle chronicle) {
        Stage placed = stage.orElse(walled ? Stage.WALLED
                : craft == Craft.MASONRY ? Stage.HEADED : Stage.LIT);
        return new Settlement(identity, grid, plots, buildQueue, craft, placed, chronicle);
    }

    /**
     * Normalises iteration order, so plots are walked the same way on every machine.
     *
     * <p>Determinism matters less than it did without a simulation to keep reproducible, but a
     * build that picks a different site depending on HashMap ordering is still a build nobody
     * can test.
     */
    public Settlement {
        plots = sorted(plots);
        buildQueue = List.copyOf(buildQueue);
    }

    private static Map<UUID, Plot> sorted(Map<UUID, Plot> in) {
        List<UUID> keys = new ArrayList<>(in.keySet());
        Collections.sort(keys);
        Map<UUID, Plot> out = new LinkedHashMap<>();
        for (UUID key : keys) {
            out.put(key, in.get(key));
        }
        return Collections.unmodifiableMap(out);
    }

    /** A bell just rung: unread ground, the biome's palette, and nothing yet allowed. */
    public static Settlement founding(SettlementId identity, Craft palette) {
        return new Settlement(identity,
                PlotGrid.empty(identity.center(), PlotGrid.sizeForClaim(identity.claimRadiusChunks())),
                Map.of(), List.of(), palette, Stage.LIT, Chronicle.EMPTY);
    }

    public UUID id() {
        return identity.id();
    }

    public String name() {
        return identity.name();
    }

    public BlockPos center() {
        return identity.center();
    }

    public ResourceKey<Level> dimension() {
        return identity.dimension();
    }

    public int houseCount() {
        int n = 0;
        for (Plot plot : plots.values()) {
            if (plot.kind() == PlotKind.HOUSE) {
                n++;
            }
        }
        return n;
    }

    // Copy helpers. Callers do not rebuild the record by hand.

    public Settlement withGrid(PlotGrid newGrid) {
        return new Settlement(identity, newGrid, plots, buildQueue, craft, stage, chronicle);
    }

    public Settlement withPlots(Map<UUID, Plot> newPlots) {
        return new Settlement(identity, grid, newPlots, buildQueue, craft, stage, chronicle);
    }

    public Settlement withBuildQueue(List<BuildJob> newQueue) {
        return new Settlement(identity, grid, plots, newQueue, craft, stage, chronicle);
    }

    /**
     * The settlement having got further along.
     *
     * <p>Only ever further: {@link Stage#or} keeps the high-water mark. Every entitlement is
     * read off a living village, and a head can be eaten; a town does not take its own streets
     * up because nobody is sitting at the table this afternoon.
     */
    public Settlement withStage(Stage earned) {
        return new Settlement(identity, grid, plots, buildQueue, craft, stage.or(earned),
                chronicle);
    }

    /** Whether somebody has ever held the village head's table here. Streets and houses. */
    public boolean headed() {
        return stage.atLeast(Stage.HEADED);
    }

    /** Whether somebody has ever held the lord's table here. The wall. */
    public boolean walled() {
        return stage.atLeast(Stage.WALLED);
    }

    public Settlement withChronicle(Chronicle newChronicle) {
        return new Settlement(identity, grid, plots, buildQueue, craft, stage, newChronicle);
    }

    public Settlement record(EntryType type, String subject, String detail, long gameTime) {
        return withChronicle(chronicle.with(new ChronicleEntry(gameTime, type, subject, detail)));
    }
}
