package adris.altoclef.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProfileStoreTest {

    @TempDir
    Path dir;

    private ObjectMapper yaml;
    private ProfileStore store;

    @BeforeEach
    void setUp() {
        yaml = SettingsYaml.createMapper();
        store = new ProfileStore(dir, yaml);
    }

    private void write(String profile, String content) throws Exception {
        Files.createDirectories(dir.resolve("profiles"));
        Files.writeString(dir.resolve("profiles").resolve(profile + ".yml"), content);
    }

    @Test
    void ensureDefaultsCreatesFilesOnce() throws Exception {
        assertTrue(store.ensureDefaults());
        assertFalse(store.ensureDefaults());

        assertTrue(Files.exists(dir.resolve("profiles.yml")));
        assertTrue(Files.exists(dir.resolve("profiles/default.yml")));
        assertEquals("default", store.getActiveProfile());
        assertEquals(List.of("default"), store.listProfiles());
    }

    @Test
    void activeProfileRoundTrip() throws Exception {
        store.setActiveProfile("speedrun");

        assertEquals("speedrun", store.getActiveProfile());
    }

    @Test
    void extendsMergesDeeplyAndChildWins() throws Exception {
        write("default", """
                settings:
                  core:
                    autoEat: true
                    commandPrefix: "@"
                  beatMinecraft: {targetEyes: 14}
                """);
        write("speedrun", """
                extends: default
                settings:
                  core:
                    commandPrefix: "#"
                """);
        List<String> warnings = new ArrayList<>();

        ObjectNode merged = store.resolve("speedrun", warnings);

        assertTrue(warnings.isEmpty(), warnings::toString);
        assertEquals("#", merged.at("/core/commandPrefix").asText());
        assertTrue(merged.at("/core/autoEat").asBoolean());
        assertEquals(14, merged.at("/beatMinecraft/targetEyes").asInt());
    }

    @Test
    void listsAreReplacedNotMerged() throws Exception {
        write("base", "settings: {core: {fuels: [coal, charcoal]}}");
        write("child", "extends: base\nsettings: {core: {fuels: [coal]}}");

        ObjectNode merged = store.resolve("child", new ArrayList<>());

        assertEquals(1, merged.at("/core/fuels").size());
    }

    @Test
    void cycleIsDetected() throws Exception {
        write("a", "extends: b\nsettings: {x: 1}");
        write("b", "extends: a\nsettings: {y: 2}");
        List<String> warnings = new ArrayList<>();

        ObjectNode merged = store.resolve("a", warnings);

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("cycle"));
        assertEquals(1, merged.get("x").asInt());
        assertEquals(2, merged.get("y").asInt());
    }

    @Test
    void missingProfileGivesWarningAndEmptyValues() {
        List<String> warnings = new ArrayList<>();

        ObjectNode merged = store.resolve("nope", warnings);

        assertEquals(1, warnings.size());
        assertTrue(merged.isEmpty());
    }

    @Test
    void brokenYamlGivesWarning() throws Exception {
        write("broken", "settings: [unclosed");
        List<String> warnings = new ArrayList<>();

        store.resolve("broken", warnings);

        assertEquals(1, warnings.size());
        // One readable line with position instead of the multi-line SnakeYAML text.
        assertFalse(warnings.get(0).contains("\n"), warnings.get(0));
        assertTrue(warnings.get(0).contains("(line "), warnings.get(0));
    }

    @Test
    void emptyFileIsAnEmptyProfile() throws Exception {
        write("empty", "");
        List<String> warnings = new ArrayList<>();

        assertTrue(store.resolve("empty", warnings).isEmpty());
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void profileNamesCannotEscapeTheFolder() {
        assertFalse(ProfileStore.isValidName("../evil"));
        assertThrows(IllegalArgumentException.class, () -> store.setActiveProfile("../evil"));
        List<String> warnings = new ArrayList<>();
        store.resolve("../evil", warnings);
        assertEquals(1, warnings.size());
    }

    @Test
    void writtenProfileCanBeReadBack() throws Exception {
        ObjectNode settings = (ObjectNode) yaml.readTree("core: {commandPrefix: '#'}");

        store.writeProfile("mine", "default", settings);
        write("default", "settings: {core: {autoEat: false}}");
        ObjectNode merged = store.resolve("mine", new ArrayList<>());

        assertEquals("#", merged.at("/core/commandPrefix").asText());
        assertFalse(merged.at("/core/autoEat").asBoolean());
    }

    @Test
    void referenceListsAllSettingsWithLimits() throws Exception {
        SettingsManager manager = new SettingsManager(yaml, w -> {
        });
        Setting.integer("beatMinecraft.targetEyes", 14).range(0, 64).description("How many eyes.").register(manager);
        Setting.bool("core.autoEat", true).register(manager);

        store.writeReference(manager);
        String reference = Files.readString(dir.resolve("settings_reference.yml"));

        assertTrue(reference.contains("# How many eyes."));
        assertTrue(reference.contains("# (min 0, max 64)"));
        assertTrue(reference.contains("beatMinecraft.targetEyes: 14"));
        assertTrue(reference.contains("core.autoEat: true"));
    }
}
