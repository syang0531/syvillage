package com.syang.syvillage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syang.syvillage.build.Template;
import com.syang.syvillage.data.Catalogue;
import com.syang.syvillage.data.Drawing;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The catalogue of buildings an architect can draw, checked against the game itself.
 *
 * <p>The list is shipped as data rather than worked out at runtime for one reason: the creative
 * menu is built on the client before any world exists, so there is no structure manager to ask,
 * and a tooltip has nothing to measure a template with. Shipping it also lets a data pack add
 * to it.
 *
 * <p>Shipped data goes stale, so this reads the game's own jar and says when it has. It also
 * writes the file - {@code ./gradlew test -Psyvillage.writeCatalogue} - which keeps <b>one</b>
 * NBT parser in this
 * project rather than two that can disagree. One shape read two ways is this project's oldest
 * class of bug.
 */
class CatalogueTest {

    /** Everything a village puts on the ground that is a building rather than a road. */
    private static final List<String> WANTED = List.of("houses", "town_centers");
    private static final List<String> BIOMES =
            List.of("desert", "plains", "savanna", "snowy", "taiga");

    private static final String CATALOGUE_PATH =
            "src/main/resources/data/syvillage/blueprint_catalogue.json";

    /**
     * The shipped catalogue, and the project it lives in, found from wherever the test happens
     * to be running.
     *
     * <p>Not simply a relative path: the test JVM's working directory is somewhere under
     * {@code build/}, so a relative path quietly wrote the file there and the check passed
     * against a copy nobody ships. Walk up until the project appears.
     */
    private static Path catalogue() {
        return project().resolve(CATALOGUE_PATH);
    }

    private static Path project() {
        Path at = Path.of("").toAbsolutePath();
        while (at != null) {
            if (Files.isDirectory(at.resolve("src/main/resources/data/syvillage/structure"))) {
                return at;
            }
            at = at.getParent();
        }
        throw new IllegalStateException("no project above " + Path.of("").toAbsolutePath());
    }

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * The game's jar, which is on the test classpath.
     *
     * <p>Read as a zip rather than as a resource: its data packages are closed to us as a
     * module, so {@code getResourceAsStream} comes back null however right the path is.
     */
    private static @Nullable Path gameJar() {
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (entry.contains("minecraft-patched") && entry.endsWith(".jar")
                    && !entry.contains("sources")) {
                return Path.of(entry);
            }
        }
        return null;
    }

    /**
     * Everything an architect can draw: ours first, then the game's villages.
     *
     * <p>Ours came last once and were simply absent - the generator only ever looked in the
     * game's jar, so a hundred and sixty-eight houses arrived and the gatehouse, tower and
     * rampart quietly left the creative menu. Anything shipped in our structure folder is a
     * drawing, which is the rule that cannot forget one.
     */
    private static List<Identifier> buildings(Path jar) throws Exception {
        List<Identifier> out = new ArrayList<>(ours());
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();) {
                String name = e.nextElement().getName();
                if (!name.startsWith("data/minecraft/structure/village/")
                        || !name.endsWith(".nbt")) {
                    continue;
                }
                String path = name.substring("data/minecraft/structure/".length(),
                        name.length() - ".nbt".length());
                String[] parts = path.split("/");
                if (parts.length == 4 && BIOMES.contains(parts[1]) && WANTED.contains(parts[2])) {
                    out.add(Identifier.withDefaultNamespace(path));
                }
            }
        }
        // Ours keep their place at the front; the game's are sorted among themselves.
        List<Identifier> ours = ours();
        out.subList(ours.size(), out.size()).sort(Comparator.comparing(Identifier::toString));
        return out;
    }

    /** Our own templates, by the files we ship. */
    private static List<Identifier> ours() throws Exception {
        Path dir = project().resolve("src/main/resources/data/syvillage/structure");
        List<Identifier> out = new ArrayList<>();
        try (var files = Files.list(dir)) {
            files.filter(f -> f.toString().endsWith(".nbt"))
                    .map(f -> f.getFileName().toString().replace(".nbt", ""))
                    .sorted()
                    .forEach(name -> out.add(
                            Identifier.fromNamespaceAndPath("syvillage", name)));
        }
        return out;
    }

    /**
     * One line of the shipped catalogue.
     *
     * <p>The measurements travel with the entry because the client cannot take them: it has no
     * structure manager, so a tooltip has no template to ask. They come from the same parser the
     * server reads templates with, which is the whole point of generating this rather than
     * typing it.
     */
    private static String entry(Path jar, Identifier id) {
        Template template = id.getNamespace().equals("syvillage")
                ? Template.of(id) : Template.loadFromJar(jar, id);
        return String.format(
                "  {\"id\": \"%s\", \"w\": %d, \"h\": %d, \"d\": %d, \"beds\": %d, \"job\": %s}",
                id, template.sizeX(), template.sizeY(), template.sizeZ(),
                template.bedCount(), template.hasJobBlock());
    }

    @Test
    @DisplayName("the shipped catalogue is every village building the game has, measured")
    void catalogueMatchesTheGame() throws Exception {
        Path jar = gameJar();
        assertNotNull(jar, "no minecraft jar on the test classpath");
        List<Identifier> ids = buildings(jar);
        assertTrue(ids.size() > 150, "only " + ids.size() + " buildings found");

        List<String> lines = new ArrayList<>();
        for (Identifier id : ids) {
            lines.add(entry(jar, id));
        }
        String wanted = "[\n" + String.join(",\n", lines) + "\n]\n";

        if (!Files.exists(catalogue()) || Boolean.getBoolean("syvillage.writeCatalogue")) {
            Files.createDirectories(catalogue().getParent());
            Files.writeString(catalogue(), wanted);
            System.out.println("wrote " + ids.size() + " buildings to " + catalogue());
            return;
        }
        assertTrue(Files.readString(catalogue()).replace("\r\n", "\n").equals(wanted),
                "the catalogue no longer matches the game's own structures;"
                        + " run ./gradlew test -Psyvillage.writeCatalogue");
    }

    @Test
    @DisplayName("the mod reads its own catalogue, and every entry has a name to show")
    void theModReadsIt() {
        List<Drawing> all = Catalogue.all();
        assertTrue(all.size() > 150, "the catalogue read back " + all.size() + " buildings");
        for (Drawing drawing : all) {
            assertFalse(drawing.title().isBlank(), drawing.template() + " has no title");
            assertFalse(drawing.title().contains("_"), drawing.title() + " is still a path");
            assertTrue(drawing.width() > 0 && drawing.depth() > 0 && drawing.height() > 0,
                    drawing.template() + " has no size");
        }
        // The two numbers a village actually grows on: somewhere to sleep, somewhere to work.
        assertTrue(all.stream().anyMatch(d -> d.beds() > 0), "no building has a bed in it");
        assertTrue(all.stream().anyMatch(Drawing::workstation), "no building has a job block");
    }

    // ---- what an architect will draw for you
    //
    // One trade per building, because that is the shape villager trades come in, and the level
    // it sits at is how the mod gates a hundred and seventy of them without counting anything
    // itself. Vanilla offers a couple of a level's trades at random, so two architects draw
    // different buildings - which is a reason to keep more than one, and a village that looks
    // like somebody's choices rather than a catalogue.

    /** Which level a building is sold at. Ours last, the game's by how much room they take. */
    private static int tier(Drawing drawing) {
        if (drawing.template().getNamespace().equals("syvillage")) {
            return 4;   // walls, gates and towers: the things a village builds after it is a village
        }
        int area = drawing.width() * drawing.depth();
        return area <= 64 ? 1 : area <= 120 ? 2 : 3;
    }

    /** Emeralds, by how much building you get. Nothing else about a drawing costs anything. */
    private static int price(Drawing drawing) {
        return Math.clamp(drawing.width() * drawing.depth() / 6, 4, 48);
    }

    /**
     * How many of one drawing an architect keeps in stock.
     *
     * <p>Six of a house, because nobody wants ten of the same cottage. Sixteen of ours, because
     * a wall is a run of segments and a restock in the middle of one is a walk home.
     */
    private static int uses(Drawing drawing) {
        return drawing.template().getNamespace().equals("syvillage") ? 16 : 6;
    }

    private static String tradeName(Drawing drawing) {
        String path = drawing.template().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * Where an architect has to be standing to draw this building.
     *
     * <p>A desert village's architect draws desert houses, which is what makes a village look
     * like the place it is in rather than like a catalogue, and what gives somebody a reason to
     * go and find a taiga one.
     *
     * <p>Plains is the fallback, and it says so by being the one with no biome of its own: it is
     * sold wherever none of the other four applies - a plains village, a jungle, a mushroom
     * island, anywhere a player founded a settlement of their own. That is the same rule
     * {@code Craft} already uses for materials, and it needs no list of every biome that is not
     * one of four.
     *
     * <p>Ours - the wall, the gate, the tower - have no biome. They are built out of whatever
     * the ground is made of wherever they are put down.
     */
    private static List<String> merchantPredicate(Drawing drawing) {
        if (drawing.template().getNamespace().equals("syvillage")) {
            return List.of();
        }
        String biome = drawing.template().getPath().split("/")[1];
        if (biome.equals("plains")) {
            return List.of(
                    "  \"merchant_predicate\": {",
                    "    \"condition\": \"minecraft:inverted\",",
                    "    \"term\": {",
                    "      \"condition\": \"minecraft:location_check\",",
                    "      \"predicate\": {\"biomes\": \"#syvillage:has_own_buildings\"}",
                    "    }",
                    "  },");
        }
        return List.of(
                "  \"merchant_predicate\": {",
                "    \"condition\": \"minecraft:location_check\",",
                "    \"predicate\": {\"biomes\": \"#minecraft:has_structure/village_" + biome
                        + "\"}",
                "  },");
    }

    /** A file, one line at a time, ending in a newline the way every other json here does. */
    private static String lines(String... rows) {
        return String.join("\n", rows) + "\n";
    }

    private static String lines(List<String> rows) {
        return String.join("\n", rows) + "\n";
    }

    /** Git hands these back with carriage returns on Windows; the generator never writes one. */
    private static String unix(String text) {
        return text.replace("\r", "");
    }

    @Test
    @DisplayName("every building is something an architect sells, at a level and a price")
    void everyBuildingIsForSale() throws Exception {
        Path dir = project().resolve("src/main/resources/data/syvillage");
        Map<Integer, List<String>> byTier = new TreeMap<>();
        Map<String, String> files = new LinkedHashMap<>();

        for (Drawing drawing : Catalogue.all()) {
            int tier = tier(drawing);
            String name = tradeName(drawing);
            byTier.computeIfAbsent(tier, t -> new ArrayList<>())
                    .add("syvillage:architect/" + tier + "/" + name);
            List<String> rows = new ArrayList<>(List.of(
                    "{",
                    "  \"wants\": {",
                    "    \"id\": \"minecraft:emerald\",",
                    "    \"count\": " + price(drawing) + ".0",
                    "  },"));
            rows.addAll(merchantPredicate(drawing));
            rows.addAll(List.of(
                    "  \"gives\": {",
                    "    \"id\": \"syvillage:blueprint\",",
                    "    \"components\": {",
                    "      \"syvillage:drawing\": {\"id\": \"" + drawing.template()
                            + "\", \"w\": " + drawing.width()
                            + ", \"h\": " + drawing.height()
                            + ", \"d\": " + drawing.depth()
                            + ", \"beds\": " + drawing.beds()
                            + ", \"job\": " + drawing.workstation() + "}",
                    "    }",
                    "  },",
                    "  \"max_uses\": " + uses(drawing) + ".0,",
                    "  \"reputation_discount\": 0.05,",
                    "  \"xp\": " + (tier * 10) + ".0",
                    "}"));
            files.put("villager_trade/architect/" + tier + "/" + name + ".json", lines(rows));
        }
        for (Map.Entry<Integer, List<String>> tier : byTier.entrySet()) {
            List<String> out = new ArrayList<>();
            out.add("{");
            out.add("  \"values\": [");
            for (int i = 0; i < tier.getValue().size(); i++) {
                out.add("    \"" + tier.getValue().get(i) + "\""
                        + (i + 1 < tier.getValue().size() ? "," : ""));
            }
            out.add("  ]");
            out.add("}");
            files.put("tags/villager_trade/architect/blueprints_" + tier.getKey() + ".json",
                    lines(out.toArray(new String[0])));
        }

        if (Boolean.getBoolean("syvillage.writeCatalogue")) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                Path at = dir.resolve(file.getKey());
                Files.createDirectories(at.getParent());
                Files.writeString(at, file.getValue());
            }
            System.out.println("wrote " + files.size() + " trade files");
            return;
        }
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path at = dir.resolve(file.getKey());
            assertTrue(Files.exists(at), at + " is missing; run ./gradlew test"
                    + " -Psyvillage.writeCatalogue");
            assertTrue(unix(Files.readString(at)).equals(file.getValue()),
                    at + " is out of date; run ./gradlew test -Psyvillage.writeCatalogue");
        }
    }

    @Test
    @DisplayName("every building in it loads, and none of it is a road or a ruin")
    void everyBuildingLoads() throws Exception {
        Path jar = gameJar();
        assertNotNull(jar, "no minecraft jar on the test classpath");
        for (Identifier id : buildings(jar)) {
            Template template = id.getNamespace().equals("syvillage")
                    ? Template.of(id) : Template.loadFromJar(jar, id);
            assertFalse(template.columns().isEmpty(), id + " has no blocks in it");
            assertFalse(id.getPath().contains("/streets/"), id + " is a road");
            assertTrue(template.sizeX() > 0, id + " has no size");
            assertFalse(id.getPath().contains("/zombie/"), id + " is a ruin");
        }
    }
}
