package com.syang.syvillage.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Which building a drawing is of, and the few facts a tooltip needs.
 *
 * <p>The measurements ride on the item rather than being read off the template, because the side
 * that draws a tooltip cannot read a template: a client has no structure manager, and the
 * buildings are the game's own rather than ours. They are put here once, from the catalogue,
 * which was itself measured by the same parser the server expands with.
 *
 * <p>One item and one texture cover a hundred and sixty-eight buildings this way. Registering
 * one item each would have been a hundred and sixty-eight registry entries and a creative menu
 * nobody could search.
 *
 * @param template     the structure, ours or the game's
 * @param beds         how many people could live in it - the number a village actually grows on
 * @param workstation  whether it carries a job block, so a villager could take a trade in it
 */
public record Drawing(Identifier template, int width, int height, int depth, int beds,
        boolean workstation) {

    public static final Codec<Drawing> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(Drawing::template),
            Codec.INT.fieldOf("w").forGetter(Drawing::width),
            Codec.INT.fieldOf("h").forGetter(Drawing::height),
            Codec.INT.fieldOf("d").forGetter(Drawing::depth),
            Codec.INT.fieldOf("beds").forGetter(Drawing::beds),
            Codec.BOOL.fieldOf("job").forGetter(Drawing::workstation)
    ).apply(i, Drawing::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Drawing> STREAM_CODEC =
            StreamCodec.of((buf, it) -> {
                buf.writeIdentifier(it.template());
                buf.writeVarInt(it.width());
                buf.writeVarInt(it.height());
                buf.writeVarInt(it.depth());
                buf.writeVarInt(it.beds());
                buf.writeBoolean(it.workstation());
            }, buf -> new Drawing(buf.readIdentifier(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

    /**
     * The building's name, as a player would read it.
     *
     * <p>Taken from the structure's own path rather than from a translation key, because there
     * are a hundred and sixty-eight of them and they are Mojang's names for Mojang's buildings.
     * A key each would be a hundred and sixty-eight lines in two language files that say the
     * same thing the path already says, and a data pack's own house would have none.
     */
    public String title() {
        String path = template.getPath();
        String last = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder out = new StringBuilder(last.length());
        boolean capital = true;
        for (char c : last.toCharArray()) {
            if (c == '_') {
                out.append(' ');
                capital = true;
            } else {
                out.append(capital ? Character.toUpperCase(c) : c);
                capital = false;
            }
        }
        return out.toString();
    }

    /** Only a name is nothing to test against; this says what to expect on the ground. */
    public String extent() {
        return width + "×" + depth + "×" + height;
    }
}
