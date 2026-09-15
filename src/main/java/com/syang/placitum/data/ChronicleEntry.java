package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** One recorded event. Deaths always carry a cause; that was the original complaint. */
public record ChronicleEntry(long gameTime, EntryType type, String subject, String detail) {

    public static final Codec<ChronicleEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("game_time").forGetter(ChronicleEntry::gameTime),
            EntryType.CODEC.fieldOf("type").forGetter(ChronicleEntry::type),
            Codec.STRING.fieldOf("subject").forGetter(ChronicleEntry::subject),
            Codec.STRING.fieldOf("detail").forGetter(ChronicleEntry::detail)
    ).apply(i, ChronicleEntry::new));
}
