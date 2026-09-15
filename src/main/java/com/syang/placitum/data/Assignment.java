package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

/**
 * What a resident does and where they live and work.
 *
 * <p>{@code job} is an {@link Identifier} rather than {@code ResourceKey<JobDef>} because the
 * JobDef registry lands in M4. Both serialise as the same string, so the swap needs no save
 * migration - see docs/data-model.md.
 */
public record Assignment(Identifier job, Optional<UUID> homePlot, Optional<UUID> workPlot) {

    public static final Codec<Assignment> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("job").forGetter(Assignment::job),
            UUIDUtil.CODEC.optionalFieldOf("home_plot").forGetter(Assignment::homePlot),
            UUIDUtil.CODEC.optionalFieldOf("work_plot").forGetter(Assignment::workPlot)
    ).apply(i, Assignment::new));

    public static final Identifier NONE = Identifier.fromNamespaceAndPath("placitum", "none");
    public static final Identifier FARMER = Identifier.fromNamespaceAndPath("placitum", "farmer");
    public static final Identifier WOODCUTTER = Identifier.fromNamespaceAndPath("placitum", "woodcutter");
    public static final Identifier BUILDER = Identifier.fromNamespaceAndPath("placitum", "builder");
    public static final Identifier SMITH = Identifier.fromNamespaceAndPath("placitum", "smith");
    public static final Identifier SCHOLAR = Identifier.fromNamespaceAndPath("placitum", "scholar");

    public static Assignment unassigned() {
        return new Assignment(NONE, Optional.empty(), Optional.empty());
    }

    public Assignment withJob(Identifier newJob) {
        return new Assignment(newJob, homePlot, workPlot);
    }
}
