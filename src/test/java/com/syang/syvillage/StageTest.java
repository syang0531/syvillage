package com.syang.syvillage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.syang.syvillage.data.Settlement;
import com.syang.syvillage.data.Stage;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The three stages, and how a save that predates them finds its place.
 *
 * <p>The stage replaced two fields - a palette that was a ladder, and a flag for the wall - and
 * both of those said where a town had got to. Reading them back correctly is the difference
 * between an old test world loading as itself and loading as a town that has only rung a bell.
 */
class StageTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    @DisplayName("a stage never goes back down")
    void itIsARatchet() {
        assertEquals(Stage.WALLED, Stage.WALLED.or(Stage.LIT));
        assertEquals(Stage.HEADED, Stage.LIT.or(Stage.HEADED));

        Settlement headed = SettlementFixture.founded().withStage(Stage.HEADED);
        assertEquals(Stage.HEADED, headed.withStage(Stage.LIT).stage(),
                "losing the head to a creeper does not take the streets back up");
    }

    @Test
    @DisplayName("a new settlement has only rung its bell")
    void itStartsLit() {
        Settlement founded = SettlementFixture.founded();
        assertEquals(Stage.LIT, founded.stage());
        assertFalse(founded.headed());
        assertFalse(founded.walled());
    }

    @Test
    @DisplayName("a walled town is a headed town")
    void theStagesNest() {
        assertTrue(Stage.WALLED.atLeast(Stage.HEADED));
        assertTrue(SettlementFixture.standard().headed(),
                "the lord's table cannot be crafted without the seal a head sells, so a wall"
                        + " means a head was here");
    }

    @Test
    @DisplayName("a save with no stage is placed by what it had")
    void oldSavesFindTheirStage() {
        assertEquals(Stage.WALLED, reload(json -> {
            json.remove("stage");
            json.addProperty("walled", true);
        }).stage(), "a wall means a lord");

        assertEquals(Stage.HEADED, reload(json -> {
            json.remove("stage");
            json.remove("walled");
            json.addProperty("craft", "masonry");
        }).stage(), "the top rung of the old ladder was only reached with a village head");

        assertEquals(Stage.LIT, reload(json -> {
            json.remove("stage");
            json.remove("walled");
            json.addProperty("craft", "timber");
        }).stage(), "anything else had only ever rung a bell");

        assertEquals(Stage.LIT, reload(json -> {
            json.remove("stage");
            json.remove("walled");
            json.remove("craft");
        }).stage(), "and a save from before there were any of these fields loads at all");
    }

    @Test
    @DisplayName("a stage in the save is believed over the fields it replaced")
    void theStageWins() {
        assertEquals(Stage.HEADED, reload(json -> {
            json.addProperty("stage", "headed");
            json.addProperty("walled", true);
        }).stage());
    }

    @Test
    @DisplayName("the save carries the stage, and still the flag an older build would read")
    void theSaveCarriesBoth() {
        JsonObject json = encode(SettlementFixture.standard());
        assertEquals("walled", json.get("stage").getAsString());
        assertTrue(json.get("walled").getAsBoolean(),
                "a save opened by the build before this one should keep its wall");
    }

    private static JsonObject encode(Settlement settlement) {
        return Settlement.CODEC.encodeStart(JsonOps.INSTANCE, settlement).getOrThrow()
                .getAsJsonObject();
    }

    private static Settlement reload(java.util.function.Consumer<JsonObject> edit) {
        JsonObject json = encode(SettlementFixture.standard());
        edit.accept(json);
        return Settlement.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    @Test
    @DisplayName("lamp posts once planned are remembered, and a save without the record loads")
    void lampsAreRemembered() {
        Settlement lit = SettlementFixture.founded().withLamps(java.util.List.of(7L, 9L));
        assertTrue(lit.withLamps(java.util.List.of(11L)).lamps().containsAll(
                java.util.Set.of(7L, 9L, 11L)), "only ever more");

        JsonObject json = encode(lit);
        assertEquals(2, json.getAsJsonArray("lamps").size());
        json.remove("lamps");
        assertTrue(Settlement.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().lamps().isEmpty(),
                "a save from before posts were remembered loads with none remembered");
    }
}
