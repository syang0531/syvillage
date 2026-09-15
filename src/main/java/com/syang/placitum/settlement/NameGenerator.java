package com.syang.placitum.settlement;

import com.syang.placitum.data.Lineage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.RandomSource;

/**
 * Given and family names.
 *
 * <p>M2 moves these pools into the datapack at {@code data/placitum/names/*.json}, which is
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

    /** Picks a name that is not already in use, falling back to a numbered suffix. */
    public static Lineage founder(RandomSource rng, Set<String> taken) {
        for (int attempt = 0; attempt < 32; attempt++) {
            String given = GIVEN.get(rng.nextInt(GIVEN.size()));
            String family = FAMILY.get(rng.nextInt(FAMILY.size()));
            String full = given + " " + family;
            if (taken.add(full)) {
                return Lineage.founder(given, family);
            }
        }
        String given = GIVEN.get(rng.nextInt(GIVEN.size()));
        String family = FAMILY.get(rng.nextInt(FAMILY.size())) + " " + (taken.size() + 1);
        taken.add(given + " " + family);
        return Lineage.founder(given, family);
    }

    public static Set<String> usedNames() {
        return new HashSet<>();
    }

    /** Settlement names reuse the family pool; the player can rename with /placitum rename. */
    public static String settlementName(RandomSource rng) {
        return FAMILY.get(rng.nextInt(FAMILY.size())) + "stead";
    }
}
