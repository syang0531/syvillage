package com.syang.syvillage.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
     * The prefixes vanilla puts on its village buildings. None is a prefix of another, so the
     * first that matches is the only one that can.
     */
    private static final List<String> BIOMES =
            List.of("desert", "plains", "savanna", "snowy", "taiga");

    /**
     * The building's name, as a player would read it.
     *
     * <p>Not one key per building. A hundred and seventy-one keys in two language files would
     * say what the path already says, and a data pack's own house would have none of them.
     * A name is made of at most three things - the biome it belongs to, what kind of building
     * it is, and which variant - so the <em>words</em> are translated and the name is put back
     * together from them. Thirty-nine keys cover every building the game ships, and a word with
     * no key falls back to the path's own spelling, which is what a data pack gets for free.
     */
    public Component name() {
        Parts parts = parts();
        MutableComponent out = Component.empty();
        if (!parts.biome().isEmpty()) {
            out.append(word("syvillage.biome.", parts.biome())).append(" ");
        }
        out.append(word("syvillage.building.", parts.kind()));
        if (!parts.number().isEmpty()) {
            out.append(" ").append(parts.number());
        }
        return out;
    }

    /**
     * The same name with nothing translated, which is what English reads and what a test can
     * assert on without a language file loaded.
     */
    public String title() {
        Parts parts = parts();
        StringBuilder out = new StringBuilder();
        if (!parts.biome().isEmpty()) {
            out.append(titleCase(parts.biome())).append(' ');
        }
        out.append(titleCase(parts.kind()));
        if (!parts.number().isEmpty()) {
            out.append(' ').append(parts.number());
        }
        return out.toString();
    }

    /**
     * The keys {@link #name()} will look up.
     *
     * <p>A word with no key still reads, in English, in every language - which is the one way
     * this can go wrong without anybody noticing. A test walks the catalogue through here, so a
     * game update that adds a kind of building we have no word for breaks the build instead.
     */
    public List<String> nameKeys() {
        Parts parts = parts();
        String kind = "syvillage.building." + parts.kind();
        return parts.biome().isEmpty() ? List.of(kind)
                : List.of("syvillage.biome." + parts.biome(), kind);
    }

    /** Only a name is nothing to test against; this says what to expect on the ground. */
    public String extent() {
        return width + "×" + depth + "×" + height;
    }

    /** What a structure's name is made of, read once so the two renderings cannot disagree. */
    private record Parts(String biome, String kind, String number) {}

    /**
     * Take the name apart.
     *
     * <p>The biome comes off only when something is left after it, and the number only when it
     * is a suffix on something - so an id that is nothing but a biome, or nothing but digits,
     * stays whole rather than becoming a blank name.
     */
    private Parts parts() {
        String path = template.getPath();
        String rest = path.substring(path.lastIndexOf('/') + 1);

        String number = "";
        int digits = rest.length();
        while (digits > 0 && Character.isDigit(rest.charAt(digits - 1))) {
            digits--;
        }
        if (digits > 1 && digits < rest.length() && rest.charAt(digits - 1) == '_') {
            number = rest.substring(digits);
            rest = rest.substring(0, digits - 1);
        }

        String biome = "";
        for (String candidate : BIOMES) {
            if (rest.startsWith(candidate + "_")) {
                biome = candidate;
                rest = rest.substring(candidate.length() + 1);
                break;
            }
        }
        return new Parts(biome, rest, number);
    }

    /** One word, translated if we know it and spelled out if we do not. */
    private static MutableComponent word(String prefix, String token) {
        return Component.translatableWithFallback(prefix + token, titleCase(token));
    }

    private static String titleCase(String token) {
        StringBuilder out = new StringBuilder(token.length());
        boolean capital = true;
        for (char c : token.toCharArray()) {
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
}
