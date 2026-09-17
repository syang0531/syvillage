package com.syang.placitum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.placitum.data.Craft;
import com.syang.placitum.data.Settlement;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The standard a village builds to, and the one thing it must never do: get worse.
 */
class CraftTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    @DisplayName("a settlement never builds worse than it once did")
    void theStandardIsAHighWaterMark() {
        // Losing the mason to a creeper must not turn the high street back into mud. Every
        // trigger anyone might use for this - trades, population, anything read off a living
        // village - can go away again, so the ratchet belongs here rather than in the reading.
        Settlement stone = SettlementFixture.founded().withCraft(Craft.STONE);
        assertEquals(Craft.STONE, stone.craft());
        assertEquals(Craft.STONE, stone.withCraft(Craft.TIMBER).craft(),
                "the village forgot how to work stone because its mason died");

        assertEquals(Craft.STONE, Craft.TIMBER.or(Craft.STONE));
        assertEquals(Craft.STONE, Craft.STONE.or(Craft.TIMBER));
        assertTrue(Craft.STONE.betterThan(Craft.TIMBER));
        assertFalse(Craft.TIMBER.betterThan(Craft.STONE));
    }

    @Test
    @DisplayName("a new settlement starts in timber")
    void everyVillageStartsInMud() {
        assertEquals(Craft.TIMBER, SettlementFixture.founded().craft());
        assertEquals(Blocks.DIRT_PATH, Craft.TIMBER.paving().getBlock());
        assertEquals(Blocks.COBBLESTONE, Craft.STONE.paving().getBlock());
    }

    @Test
    @DisplayName("paving of any standard is recognised as paving")
    void everyStandardsPavingIsOurs() {
        // What lets a street be re-laid rather than left: the road planner asks whether a column
        // already holds paving of the standard it is building to, and a lower one comes back as
        // work to do. If a standard's block were not recognised here the upgrade would stall.
        for (Craft craft : Craft.values()) {
            assertTrue(Craft.isPaving(craft.paving()),
                    craft + " paves in something the mod does not recognise as paving");
        }
        assertFalse(Craft.isPaving(Blocks.GRASS_BLOCK.defaultBlockState()));
        assertFalse(Craft.isPaving(Blocks.OAK_PLANKS.defaultBlockState()));
    }

    @Test
    @DisplayName("a recipe carries its standard, so a half-built house does not change material")
    void theStandardSurvivesTheTripThroughARecipe() {
        // A cottage half up when the mason arrives has to finish in the timber it started in.
        // The alternative is a wall that is oak for three courses and cobble for the fourth.
        for (Craft craft : Craft.values()) {
            assertEquals(craft, Craft.fromPalette(craft.paletteId()),
                    craft + " does not survive being written into a recipe and read back");
        }
        assertEquals(Craft.TIMBER,
                Craft.fromPalette(Identifier.fromNamespaceAndPath("placitum", "craft/marble")),
                "a standard this version has never heard of has to build something, not crash");
    }

    @Test
    @DisplayName("every standard builds a whole house, and the roof stays timber")
    void everyStandardIsAWholePalette() {
        for (Craft craft : Craft.values()) {
            assertNotNull(craft.paving());
            assertNotNull(craft.wall());
            assertNotNull(craft.floor());
            assertNotNull(craft.roof());
            assertNotNull(craft.foundation());
        }
        // Cobblestone walls under an oak roof: what vanilla's own village houses look like, and
        // the reason stone is the middle rung rather than the top one.
        assertEquals(Blocks.COBBLESTONE, Craft.STONE.wall().getBlock());
        assertEquals(Blocks.OAK_PLANKS, Craft.STONE.roof().getBlock());
        assertEquals(Blocks.OAK_PLANKS, Craft.TIMBER.wall().getBlock());
    }

    @Test
    @DisplayName("a settlement saved before standards existed loads as one that builds in timber")
    void oldSavesLoadAsTimber() {
        // The rule from CLAUDE.md: a required field added to a stored shape is how a roster
        // gets silently emptied. optionalFieldOf with a default is the only safe way to add one.
        Settlement settlement = SettlementFixture.standard();
        var encoded = Settlement.CODEC.encodeStart(
                com.mojang.serialization.JsonOps.INSTANCE, settlement)
                .getOrThrow();
        com.google.gson.JsonObject json = encoded.getAsJsonObject();
        json.remove("craft");

        Settlement loaded = Settlement.CODEC.parse(
                com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(Craft.TIMBER, loaded.craft(),
                "a save with no standard in it has to load, not fail");
        assertEquals(settlement.plots().size(), loaded.plots().size(),
                "and it must not lose anything else on the way through");
    }
}
