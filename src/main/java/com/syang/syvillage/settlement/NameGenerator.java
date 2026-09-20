package com.syang.syvillage.settlement;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.RandomSource;

/**
 * Given and family names.
 *
 * <p>M2 moves these pools into the datapack at {@code data/syvillage/names/*.json}, which is
 * where docs/data-model.md says they belong. The built-in lists exist now because registration
 * cannot adopt a villager without naming them, and M0 needs registration.
 */
public final class NameGenerator {

    private static final List<String> GIVEN = List.of(
            "Johann", "Anna", "Bernhard", "Mila", "Konrad", "Greta", "Otto", "Hedwig",
            "Ulrich", "Adela", "Rudolf", "Irmgard", "Wilhelm", "Sieglinde", "Gerhard",
            "Kunigunde", "Dietrich", "Mechthild", "Albrecht", "Walburga");

    private static final List<String> FAMILY = List.of(
            "Bernhardt", "Mielen", "Waldmann", "Steiner", "Kaufmann", "Achen", "Reuter",
            "Lindner", "Brandt", "Falk", "Hartmann", "Osterrath", "Vogt", "Zimmer", "Kern");

    private NameGenerator() {}


    public static Set<String> usedNames() {
        return new HashSet<>();
    }

    /** Settlement names reuse the family pool; the player can rename with /syvillage rename. */
    public static String settlementName(RandomSource rng) {
        return FAMILY.get(rng.nextInt(FAMILY.size())) + "stead";
    }
}
