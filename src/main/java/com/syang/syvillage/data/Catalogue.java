package com.syang.syvillage.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.syang.syvillage.SyVillage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Every building an architect can draw.
 *
 * <p>Read once from {@code data/syvillage/blueprint_catalogue.json}, which is generated from the
 * game's own structures by {@code CatalogueTest} and checked against them on every build. It is
 * shipped rather than worked out at runtime because the creative menu is built before any world
 * exists: there is no structure manager to ask and no template to measure.
 *
 * <p>Static content, like a template. Nothing here is saved and nothing here changes.
 */
public final class Catalogue {

    private Catalogue() {}

    private static final String PATH = "/data/syvillage/blueprint_catalogue.json";

    private static List<Drawing> drawings;

    public static synchronized List<Drawing> all() {
        if (drawings == null) {
            drawings = read();
        }
        return drawings;
    }

    private static List<Drawing> read() {
        try (InputStream in = Catalogue.class.getResourceAsStream(PATH)) {
            if (in == null) {
                throw new IllegalStateException("no catalogue at " + PATH);
            }
            JsonArray array = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            List<Drawing> out = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                out.add(Drawing.CODEC.parse(JsonOps.INSTANCE, element)
                        .getOrThrow(why -> new IllegalStateException(
                                "bad catalogue entry " + element + ": " + why)));
            }
            SyVillage.LOGGER.debug("Catalogue: {} buildings", out.size());
            return List.copyOf(out);
        } catch (Exception e) {
            throw new IllegalStateException("could not read " + PATH, e);
        }
    }
}
