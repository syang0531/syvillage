package com.syang.syvillage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.syang.syvillage.data.Catalogue;
import com.syang.syvillage.data.Drawing;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The two language files say the same things.
 *
 * <p>A key in one and not the other shows up in game as the raw key, and only in the language
 * that lacks it - which is exactly the language the developer is not playing in. An argument
 * count that differs is worse: the missing number is silently dropped from the sentence.
 */
class LangTest {

    private static final Pattern ARG = Pattern.compile("%(?:(\\d+)\\$)?s");

    private static JsonObject lang(String code) {
        try (InputStream in = LangTest.class.getResourceAsStream(
                "/assets/syvillage/lang/" + code + ".json")) {
            assertNotNull(in, code + ".json is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** How many arguments a format string takes: the highest positional index, or the count. */
    private static int arity(String text) {
        Matcher m = ARG.matcher(text);
        int count = 0;
        int highest = 0;
        while (m.find()) {
            count++;
            if (m.group(1) != null) {
                highest = Math.max(highest, Integer.parseInt(m.group(1)));
            }
        }
        return Math.max(count, highest);
    }

    @Test
    @DisplayName("en_us and ko_kr have the same keys, and each takes the same arguments")
    void theTwoLanguagesAgree() {
        JsonObject en = lang("en_us");
        JsonObject ko = lang("ko_kr");
        assertEquals(en.keySet(), ko.keySet(), "the two language files list different keys");
        for (Map.Entry<String, com.google.gson.JsonElement> entry : en.entrySet()) {
            String key = entry.getKey();
            String english = entry.getValue().getAsString();
            String korean = ko.get(key).getAsString();
            assertFalse(english.isBlank(), key + " is blank in en_us");
            assertFalse(korean.isBlank(), key + " is blank in ko_kr");
            assertEquals(arity(english), arity(korean),
                    key + " takes a different number of arguments in Korean");
        }
        // That the sweep read a file at all, rather than an empty object.
        assertTrue(en.has("syvillage.title"), "the sweep found no keys at all");
    }

    /**
     * Every building the architect can draw has a word for every part of its name.
     *
     * <p>A missing word is the quiet failure here: the name falls back to the path's own
     * spelling, which is English, and English is what the developer is reading anyway. So the
     * catalogue is walked rather than the language file - a building the game adds and we have
     * no word for fails here, not in somebody's Korean client.
     */
    @Test
    @DisplayName("every building in the catalogue is made of words both languages know")
    void everyBuildingHasItsWords() {
        JsonObject en = lang("en_us");
        JsonObject ko = lang("ko_kr");
        List<Drawing> all = Catalogue.all();
        assertFalse(all.isEmpty(), "the catalogue is empty, so this test checked nothing");
        for (Drawing drawing : all) {
            for (String key : drawing.nameKeys()) {
                assertTrue(en.has(key), drawing.template() + " needs " + key + " in en_us");
                assertTrue(ko.has(key), drawing.template() + " needs " + key + " in ko_kr");
            }
        }
    }
}
