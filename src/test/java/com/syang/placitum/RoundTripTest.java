package com.syang.placitum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.placitum.data.Resident;
import com.syang.placitum.data.ResidentState;
import com.syang.placitum.data.Settlement;
import com.syang.placitum.store.SettlementManager;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test 1 of docs/testing.md - round-trip integrity.
 *
 * <p>Principle 1 says the record is the truth. This is the test that keeps it true: every time
 * a field is added, it catches the one that does not survive the trip. It is expected to fail
 * at least once per new field, and that is the whole point of running it.
 *
 * <p>Note what is NOT done here: the simulation clock is never advanced. Promote begins with a
 * catch-up, so if time moved, state changing across the trip would be correct behaviour and
 * the test would be measuring nothing.
 */
class RoundTripTest {

    private static RegistryOps<Tag> ops;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
                .createSerializationContext(NbtOps.INSTANCE);
    }

    private static Tag encode(Settlement settlement) {
        return Settlement.CODEC.encodeStart(ops, settlement).getOrThrow();
    }

    private static Settlement decode(Tag tag) {
        return Settlement.CODEC.parse(ops, tag).getOrThrow();
    }

    @Test
    @DisplayName("a fully populated settlement survives encode -> decode -> encode")
    void settlementSurvivesSerializationRoundTrip() {
        Settlement before = SettlementFixture.standard();

        Tag first = encode(before);
        Settlement decoded = decode(first);
        Tag second = encode(decoded);

        assertEquals(first, second, "a field is being lost or reordered by the codec");
    }

    @Test
    @DisplayName("the fields that are easiest to drop come back intact")
    void awkwardFieldsSurvive() {
        Settlement decoded = decode(encode(SettlementFixture.standard()));
        Settlement before = SettlementFixture.standard();

        assertEquals(before.defense().recentCasualties(), decoded.defense().recentCasualties(),
                "the casualty ring buffer changed length or contents");
        assertEquals(before.buildQueue().getFirst().progress(), decoded.buildQueue().getFirst().progress());
        assertEquals(before.buildQueue().getFirst().recipe().groundProfile(),
                decoded.buildQueue().getFirst().recipe().groundProfile(),
                "the frozen ground profile is what makes op expansion a pure function");
        assertEquals(before.grid().cells(), decoded.grid().cells());
        assertEquals(before.defense().wall().gates().getFirst().open(),
                decoded.defense().wall().gates().getFirst().open());
        assertEquals(before.chronicle().entries().size(), decoded.chronicle().entries().size());
        assertEquals(before.parentId(), decoded.parentId(), "the V2 hook has to survive too");
        assertEquals(before.scaleHoldSteps(), decoded.scaleHoldSteps());
    }

    @Test
    @DisplayName("the vanilla blob survives, since demote is the only thing holding it")
    void vanillaStateSurvives() {
        Settlement decoded = decode(encode(SettlementFixture.standard()));

        Resident before = SettlementFixture.standard().residents().getFirst();
        Resident after = decoded.residents().getFirst();
        assertEquals(before.vanillaState(), after.vanillaState(),
                "trades and profession vanish on the first demote if this codec is wrong");
        assertTrue(before.vanillaState().contains("offers"),
                "the fixture must exercise a non-empty blob");
        assertTrue(before.vanillaState().contains("villager_data"),
                "VillagerData is the piece whose loss silently unemployed every farmer");
    }

    @Test
    @DisplayName("iteration order is normalised, so two equal settlements encode identically")
    void orderIsNormalised() {
        Settlement ordered = SettlementFixture.standard();
        // Same residents, reversed. The canonical constructor must sort them back.
        java.util.List<Resident> reversed = new java.util.ArrayList<>(ordered.residents());
        java.util.Collections.reverse(reversed);
        Settlement shuffled = SettlementManager.withResidents(ordered, reversed);

        assertEquals(encode(ordered), encode(shuffled),
                "residents must iterate in id order regardless of insertion order");
    }

    @Test
    @DisplayName("boot resync clears MATERIALIZED without losing anybody")
    void bootResyncKeepsEveryone() {
        Settlement crashed = SettlementFixture.full(5, ResidentState.MATERIALIZED);
        assertEquals(5, crashed.materializedCount());

        Settlement recovered = SettlementManager.allVirtual(crashed);

        assertEquals(0, recovered.materializedCount(), "entities do not exist yet after a restart");
        assertEquals(crashed.residentCount(), recovered.residentCount(), "nobody may be dropped");
        assertTrue(recovered.residents().stream().allMatch(r -> r.state() == ResidentState.VIRTUAL));
        // Everything except the LOD flag is untouched.
        assertEquals(encode(SettlementManager.withResidents(crashed,
                        crashed.residents().stream().map(r -> r.withState(ResidentState.VIRTUAL)).toList())),
                encode(recovered));
    }
}
