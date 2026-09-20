package com.syang.syvillage.build;

import com.syang.syvillage.data.CellPos;
import com.syang.syvillage.data.Craft;
import com.syang.syvillage.data.PlotKind;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * The vanilla village buildings a settlement may put on a lot: the ones with beds.
 *
 * <p>Measured, not guessed: of the hundred and fifty-two buildings in vanilla's five village
 * sets, forty-two fit a seven-by-seven lot roof and all, and every one of those carries a
 * {@code building_entrance} jigsaw that says which way it faces. Thirty have beds, and those
 * thirty are the catalogue.
 *
 * <p>The other eleven - armourers, weaponsmiths, a tannery, a pen, the farms with composters -
 * were in it for a release, and came out after 0.1.0 was played: a lot spent on a building
 * with no bed is a lot the village does not grow on, and a pen or a smithy on every fourth
 * lot held a small village at its size. What a lot gets now is a dwelling or a wheat field,
 * three to one ({@link Need}), because those are the two things vanilla breeding runs on.
 *
 * <p>Nobody here authored any of it. That was the objection to templates for as long as they
 * meant writing blocks from code and saving the result; it does not apply to Mojang's own
 * artists' work, which comes with the game and in five biomes' worth of materials.
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

    /** Every building, for a test that wants to load them all. */
    public static List<Identifier> all() {
        return List.of(PLAINS_DWELLINGS, DESERT_DWELLINGS, SAVANNA_DWELLINGS, SNOWY_DWELLINGS,
                        TAIGA_DWELLINGS)
                .stream().flatMap(List::stream).toList();
    }

    /**
     * Which dwelling goes on this lot, or empty for a wheat field.
     *
     * <p>Chosen by the lot, not by chance, so that the same town plans the same house on the
     * same lot every time it looks - a plan that changed its mind between planning and laying
     * would be one nobody could test. A lot that is to be a field is a field, nothing else.
     */
    public static Optional<Identifier> pick(Craft craft, Need.Kind kind, CellPos cell) {
        if (kind == Need.Kind.FARM) {
            return Optional.empty();
        }
        int roll = Math.floorMod(cell.gx() * 31 + cell.gz() * 17, 1_000);
        List<Identifier> houses = dwellings(craft);
        return Optional.of(houses.get(roll % houses.size()));
    }

    /** Whether this is one of ours to build from a vanilla template. */
    public static boolean isVanilla(Identifier template) {
        return template.getNamespace().equals("minecraft");
    }

    /**
     * What a finished building counts as, for the plot record and for {@link Need}.
     *
     * <p>Every building in the catalogue has beds now, so this is HOUSE for anything the
     * settlement builds today; a plot recorded as WORKSHOP by 0.1.0 still loads and still
     * counts as neither house nor field.
     */
    public static PlotKind kindOf(Identifier template) {
        Template t = Template.of(template);
        if (t.bedCount() > 0) {
            return PlotKind.HOUSE;
        }
        return t.hasJobBlock() ? PlotKind.WORKSHOP : PlotKind.FARM;
    }
}
