package com.syang.placitum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
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
 * The palettes: what a town is made of, decided once by where its bell stands.
 *
 * <p>Not a ladder any more. What these tests guard is that every palette is whole, that the
 * three rungs of the old ladder still load by name, and that paving of any of them - old or
 * new - is recognised as ours, because a town does not take up its own street.
 */
class CraftTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    @DisplayName("every palette has all four blocks")
    void everyPaletteIsWhole() {
        for (Craft craft : Craft.values()) {
            assertFalse(craft.paving().isAir(), craft + " has no paving");
            assertFalse(craft.wall().isAir(), craft + " has no wall");
            assertFalse(craft.roof().isAir(), craft + " has no roof");
            assertFalse(craft.foundation().isAir(), craft + " has no foundation");
            assertEquals(craft.foundation(), craft.floor(),
                    "the floor of a house is the same block as what holds it up");
        }
    }

    @Test
    @DisplayName("the cottage wears the foundation block, and the wall block is for the wall")
    void wallsAndFortificationsAreDifferentBlocks() {
        // Cobblestone under a timber roof is the vanilla village house; stone brick is what the
        // rampart, the gates and the towers are made of. The old ladder had both in one slot,
        // which is how a walled town ended up stone brick from the road to the roof.
        assertEquals(Blocks.COBBLESTONE, Craft.PLAINS.foundation().getBlock());
        assertEquals(Blocks.STONE_BRICKS, Craft.PLAINS.wall().getBlock());
        assertEquals(Blocks.SANDSTONE, Craft.DESERT.foundation().getBlock());
        assertEquals(Blocks.CUT_SANDSTONE, Craft.DESERT.wall().getBlock());
        assertEquals(Blocks.SPRUCE_PLANKS, Craft.TAIGA.roof().getBlock());
    }

    @Test
    @DisplayName("paving of any palette, the old ladder's included, is recognised as ours")
    void pavingIsRecognised() {
        for (Craft craft : Craft.values()) {
            assertTrue(Craft.isPaving(craft.paving()),
                    craft + "'s paving is not recognised, so a town would take up its own street");
        }
        // The three rungs, by block: every test world so far is paved in one of these.
        assertTrue(Craft.isPaving(Blocks.DIRT_PATH.defaultBlockState()));
        assertTrue(Craft.isPaving(Blocks.COBBLESTONE.defaultBlockState()),
                "the old ladder's cobblestone street is still a street");
    }

    @Test
    @DisplayName("streets are what vanilla lays there, and a lamp is a block, a post and a lantern")
    void streetsAndLampsAreTheBiomes() {
        // Vanilla's taiga and snowy streets are dirt path like the plains'; only the desert paves.
        for (Craft craft : new Craft[] {Craft.PLAINS, Craft.TAIGA, Craft.SNOWY, Craft.SAVANNA}) {
            assertEquals(Blocks.DIRT_PATH, craft.paving().getBlock(), craft + " paves in grass");
        }
        assertEquals(Blocks.SMOOTH_SANDSTONE, Craft.DESERT.paving().getBlock());

        // A stone brick wall for a foot, the palette's fence on it, the lantern on that.
        for (Craft craft : new Craft[] {Craft.PLAINS, Craft.TAIGA, Craft.SNOWY, Craft.SAVANNA}) {
            assertEquals(Blocks.STONE_BRICK_WALL, craft.lampBase().getBlock(), craft + " foot");
            assertEquals(craft.fence().getBlock(), craft.lampPost().getBlock(), craft + " post");
        }
        // No wood in the desert: a sandstone wall, and birch for the post.
        assertEquals(Blocks.SANDSTONE_WALL, Craft.DESERT.lampBase().getBlock());
        assertEquals(Blocks.BIRCH_FENCE, Craft.DESERT.lampPost().getBlock());
        assertTrue(Craft.isPaving(Blocks.STONE_BRICKS.defaultBlockState()));

        assertFalse(Craft.isPaving(Blocks.GRASS_BLOCK.defaultBlockState()));
        assertFalse(Craft.isPaving(Blocks.OAK_PLANKS.defaultBlockState()));
        assertFalse(Craft.isPaving(Blocks.GRAVEL.defaultBlockState()),
                "vanilla's own village paths are not ours to relay");
    }

    @Test
    @DisplayName("a recipe carries its palette, so a half-built wall does not change material")
    void recipesCarryTheirPalette() {
        for (Craft craft : Craft.values()) {
            assertEquals(craft, Craft.fromPalette(craft.paletteId()),
                    craft + " does not survive being frozen into a recipe");
        }
        assertEquals(Craft.PLAINS,
                Craft.fromPalette(Identifier.fromNamespaceAndPath("placitum", "craft/marble")),
                "an unknown palette builds in plains rather than not at all");
    }

    @Test
    @DisplayName("the old rungs still load by name")
    void theOldLadderStillLoads() {
        // Kept only because saves and queued recipes name them. A save that names a palette
        // which does not exist is a save that does not load.
        assertEquals(Craft.MASONRY,
                Craft.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("masonry")).getOrThrow());
        assertEquals(Craft.TIMBER,
                Craft.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("timber")).getOrThrow());
        assertEquals("plains", Craft.CODEC.encodeStart(JsonOps.INSTANCE, Craft.PLAINS)
                .getOrThrow().getAsString());
    }

    @Test
    @DisplayName("a settlement saved before palettes existed loads as a plains town")
    void oldSavesLoadAsPlains() {
        // The rule from CLAUDE.md: a required field added to a stored shape is how a roster
        // gets silently emptied. optionalFieldOf with a default is the only safe way to add one.
        Settlement settlement = SettlementFixture.standard();
        JsonObject json = Settlement.CODEC.encodeStart(JsonOps.INSTANCE, settlement)
                .getOrThrow().getAsJsonObject();
        json.remove("craft");

        Settlement loaded = Settlement.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(Craft.PLAINS, loaded.craft(), "a save with no palette has to load, not fail");
        assertEquals(settlement.plots().size(), loaded.plots().size(),
                "and everything else in it survives");
    }
}
