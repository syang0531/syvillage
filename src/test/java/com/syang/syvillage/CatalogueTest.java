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
import java.util.List;
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
     * The shipped catalogue, found from wherever the test happens to be running.
     *
     * <p>Not simply a relative path: the test JVM's working directory is somewhere under
     * {@code build/}, so a relative path quietly wrote the file there and the check passed
     * against a copy nobody ships. Walk up until the project appears.
     */
    private static Path catalogue() {
        Path at = Path.of("").toAbsolutePath();
        while (at != null) {
            Path candidate = at.resolve(CATALOGUE_PATH);
            if (Files.isDirectory(candidate.getParent().getParent().getParent())) {
                return candidate;
            }
            at = at.getParent();
        }
        throw new IllegalStateException("no src/main/resources above " + Path.of("").toAbsolutePath());
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

    /** Every village building in the jar, in a fixed order. */
    private static List<Identifier> buildings(Path jar) throws Exception {
        List<Identifier> out = new ArrayList<>();
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
        out.sort(Comparator.comparing(Identifier::toString));
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
        Template template = Template.loadFromJar(jar, id);
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
        assertTrue(ids.size() > 150, "only " + ids.size() + " village buildings found");

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

    @Test
    @DisplayName("every building in it loads, and none of it is a road or a ruin")
    void everyBuildingLoads() throws Exception {
        Path jar = gameJar();
        assertNotNull(jar, "no minecraft jar on the test classpath");
        for (Identifier id : buildings(jar)) {
            Template template = Template.loadFromJar(jar, id);
            assertFalse(template.columns().isEmpty(), id + " has no blocks in it");
            assertFalse(id.getPath().contains("/streets/"), id + " is a road");
            assertFalse(id.getPath().contains("/zombie/"), id + " is a ruin");
        }
    }
}
