package com.syang.placitum;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Our English, loaded into the game's language so that a translatable component in a test
 * renders as the text a player would read rather than as its key.
 *
 * <p>The game's default language knows only Minecraft's own keys; ours are in
 * {@code assets/placitum/lang/en_us.json} on the test classpath, and this lays them over it.
 */
final class TestLanguage {

    private static boolean injected;

    private TestLanguage() {}

    static synchronized void inject() {
        if (injected) {
            return;
        }
        Language base = Language.getInstance();
        Map<String, String> ours = new HashMap<>();
        try (InputStream in = TestLanguage.class.getResourceAsStream("/assets/placitum/lang/en_us.json")) {
            if (in == null) {
                throw new IllegalStateException("no en_us.json on the test classpath");
            }
            Language.loadFromJson(in, ours::put);
        } catch (IOException e) {
            throw new IllegalStateException("could not read en_us.json", e);
        }
        Language.inject(new Language() {
            @Override
            public String getOrDefault(String key, String fallback) {
                String text = ours.get(key);
                return text != null ? text : base.getOrDefault(key, fallback);
            }

            @Override
            public boolean has(String key) {
                return ours.containsKey(key) || base.has(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return false;
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText text) {
                return base.getVisualOrder(text);
            }
        });
        injected = true;
    }
}
