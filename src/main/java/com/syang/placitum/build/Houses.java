package com.syang.placitum.build;

import com.syang.placitum.data.CellPos;
import com.syang.placitum.data.Craft;
import com.syang.placitum.data.PlotKind;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * The vanilla village buildings a settlement may put on a lot.
 *
 * <p>Measured, not guessed: of the hundred and fifty-two buildings in vanilla's five village
 * sets, forty-two fit a seven-by-seven lot roof and all, and every one of those carries a
 * {@code building_entrance} jigsaw that says which way it faces. Thirty have beds. The one left
 * out is {@code plains_accessory_1}, a three-by-five ornament two blocks high, which is not
 * worth a lot.
 *
 * <p>Nobody here authored any of it. That was the objection to templates for as long as they
 * meant writing blocks from code and saving the result; it does not apply to Mojang's own
 * artists' work, which comes with the game and in five biomes' worth of materials.
 *
 * <p>Some of the workshops carry job blocks - a blast furnace, a grindstone, a composter. That
 * means the town starts making vanilla professions on its own, and it is meant to: we put the
 * block down and vanilla decides who takes the job, as a generated village does. The two
 * tables that open a stage stay the player's.
 */
public final class Houses {

    private Houses() {}

    private static Identifier v(String biome, String name) {
        return Identifier.withDefaultNamespace("village/" + biome + "/houses/" + name);
    }

    /** Buildings with beds: what a lot gets when there are more villagers than beds. */
    private static final List<Identifier> PLAINS_DWELLINGS = List.of(
            v("plains", "plains_small_house_1"), v("plains", "plains_small_house_2"),
            v("plains", "plains_small_house_3"), v("plains", "plains_small_house_4"),
            v("plains", "plains_small_house_6"));
    private static final List<Identifier> DESERT_DWELLINGS = List.of(
            v("desert", "desert_medium_house_1"), v("desert", "desert_small_house_1"),
            v("desert", "desert_small_house_2"), v("desert", "desert_small_house_3"),
            v("desert", "desert_small_house_4"), v("desert", "desert_small_house_5"),
            v("desert", "desert_small_house_6"), v("desert", "desert_small_house_8"));
    private static final List<Identifier> SAVANNA_DWELLINGS = List.of(
            v("savanna", "savanna_small_house_1"), v("savanna", "savanna_small_house_2"),
            v("savanna", "savanna_small_house_3"), v("savanna", "savanna_small_house_5"),
            v("savanna", "savanna_small_house_6"), v("savanna", "savanna_small_house_7"),
            v("savanna", "savanna_small_house_8"));
    private static final List<Identifier> SNOWY_DWELLINGS = List.of(
            v("snowy", "snowy_medium_house_3"), v("snowy", "snowy_small_house_1"),
            v("snowy", "snowy_small_house_2"), v("snowy", "snowy_small_house_3"),
            v("snowy", "snowy_small_house_5"), v("snowy", "snowy_small_house_6"),
            v("snowy", "snowy_small_house_7"), v("snowy", "snowy_small_house_8"));
    private static final List<Identifier> TAIGA_DWELLINGS = List.of(
            v("taiga", "taiga_small_house_2"), v("taiga", "taiga_small_house_3"));

    /** Everything else that fits: workshops, farms, a pen. Mixed in the way fields are. */
    private static final List<Identifier> PLAINS_OTHERS = List.of(
            v("plains", "plains_animal_pen_1"));
    private static final List<Identifier> DESERT_OTHERS = List.of(
            v("desert", "desert_armorer_1"), v("desert", "desert_cartographer_house_1"),
            v("desert", "desert_farm_1"), v("desert", "desert_tannery_1"));
    private static final List<Identifier> SAVANNA_OTHERS = List.of(
            v("savanna", "savanna_armorer_1"));
    private static final List<Identifier> SNOWY_OTHERS = List.of(
            v("snowy", "snowy_armorer_house_2"), v("snowy", "snowy_farm_1"));
    private static final List<Identifier> TAIGA_OTHERS = List.of(
            v("taiga", "taiga_armorer_2"), v("taiga", "taiga_weaponsmith_1"),
            v("taiga", "taiga_weaponsmith_2"));

    /** The dwellings for a palette. The old ladder's rungs build plains houses. */
    public static List<Identifier> dwellings(Craft craft) {
        return switch (craft) {
            case DESERT -> DESERT_DWELLINGS;
            case SAVANNA -> SAVANNA_DWELLINGS;
            case SNOWY -> SNOWY_DWELLINGS;
            case TAIGA -> TAIGA_DWELLINGS;
            default -> PLAINS_DWELLINGS;
        };
    }

    public static List<Identifier> others(Craft craft) {
        return switch (craft) {
            case DESERT -> DESERT_OTHERS;
            case SAVANNA -> SAVANNA_OTHERS;
            case SNOWY -> SNOWY_OTHERS;
            case TAIGA -> TAIGA_OTHERS;
            default -> PLAINS_OTHERS;
        };
    }

    /** Every building, for a test that wants to load them all. */
    public static List<Identifier> all() {
        return List.of(PLAINS_DWELLINGS, DESERT_DWELLINGS, SAVANNA_DWELLINGS, SNOWY_DWELLINGS,
                        TAIGA_DWELLINGS, PLAINS_OTHERS, DESERT_OTHERS, SAVANNA_OTHERS,
                        SNOWY_OTHERS, TAIGA_OTHERS)
                .stream().flatMap(List::stream).toList();
    }

    /**
     * Which building goes on this lot, or empty for a plain field.
     *
     * <p>Chosen by the lot, not by chance, so that the same town plans the same house on the
     * same lot every time it looks - a plan that changed its mind between planning and laying
     * would be one nobody could test. When a lot needs no beds it takes turns between a field
     * and whatever else the biome has: a village of nothing but wheat is a farm.
     */
    public static Optional<Identifier> pick(Craft craft, Need.Kind kind, CellPos cell) {
        int roll = Math.floorMod(cell.gx() * 31 + cell.gz() * 17, 1_000);
        if (kind == Need.Kind.HOUSE) {
            List<Identifier> houses = dwellings(craft);
            return Optional.of(houses.get(roll % houses.size()));
        }
        List<Identifier> others = others(craft);
        int choice = roll % (others.size() + 1);
        return choice == 0 ? Optional.empty() : Optional.of(others.get(choice - 1));
    }

    /** Whether this is one of ours to build from a vanilla template. */
    public static boolean isVanilla(Identifier template) {
        return template.getNamespace().equals("minecraft");
    }

    /** What a finished building counts as, for the plot record and for {@link Need}. */
    public static PlotKind kindOf(Identifier template) {
        Template t = Template.of(template);
        if (t.bedCount() > 0) {
            return PlotKind.HOUSE;
        }
        return t.hasJobBlock() ? PlotKind.WORKSHOP : PlotKind.FARM;
    }
}
