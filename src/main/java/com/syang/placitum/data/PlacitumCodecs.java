package com.syang.placitum.data;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * Shared codec pieces.
 *
 * <p>Two rules from CLAUDE.md are enforced here rather than left to call sites:
 * collections must iterate deterministically, and fixed-length int buffers must not be
 * raw arrays.
 */
public final class PlacitumCodecs {

    private PlacitumCodecs() {}

    /**
     * Item to count, normalised into a map that iterates in item-id order.
     *
     * <p>A plain HashMap would make the simulation depend on JVM hash order, which shows up
     * in the catch-up equivalence test as "differs occasionally" and is miserable to chase.
     */
    public static final Codec<Map<Item, Integer>> ITEM_COUNTS =
            Codec.unboundedMap(BuiltInRegistries.ITEM.byNameCodec(), Codec.INT)
                    .xmap(PlacitumCodecs::sortItems, PlacitumCodecs::sortItems);

    /**
     * Fixed-length int buffers (recent casualties, ground profiles).
     *
     * <p>Modelled as {@code List<Integer>} rather than {@code int[]} on purpose: arrays use
     * reference equality, so a record holding one silently breaks equals() and every
     * assertion written against it.
     */
    public static final Codec<List<Integer>> INT_LIST =
            Codec.INT_STREAM.xmap(
                    stream -> List.copyOf(stream.boxed().toList()),
                    list -> IntStream.of(toIntArray(list)));

    public static Map<Item, Integer> sortItems(Map<Item, Integer> in) {
        List<Map.Entry<Item, Integer>> entries = new ArrayList<>(in.entrySet());
        entries.sort(Comparator.comparing(e -> itemId(e.getKey())));
        Map<Item, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> e : entries) {
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    public static Identifier itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    public static int[] toIntArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    public static List<Integer> zeros(int size) {
        return IntStream.range(0, size).map(i -> 0).boxed().toList();
    }
}
