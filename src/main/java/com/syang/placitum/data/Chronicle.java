package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;

/**
 * The settlement's history, for the player.
 *
 * <p>Lossy by design - the oldest entries are dropped past {@link #MAX}. Nothing in the
 * simulation may read it back as an input; anything that needs a count keeps its own counter.
 */
public record Chronicle(List<ChronicleEntry> entries) {

    public static final int MAX = 200;

    public static final Codec<Chronicle> CODEC = RecordCodecBuilder.create(i -> i.group(
            ChronicleEntry.CODEC.listOf().fieldOf("entries").forGetter(Chronicle::entries)
    ).apply(i, Chronicle::new));

    public static final Chronicle EMPTY = new Chronicle(List.of());

    public Chronicle with(ChronicleEntry entry) {
        List<ChronicleEntry> next = new ArrayList<>(entries);
        next.add(entry);
        while (next.size() > MAX) {
            next.removeFirst();
        }
        return new Chronicle(List.copyOf(next));
    }

    public List<ChronicleEntry> recent(int n) {
        int from = Math.max(0, entries.size() - n);
        return entries.subList(from, entries.size());
    }
}
