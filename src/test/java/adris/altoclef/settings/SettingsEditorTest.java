package adris.altoclef.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettingsEditorTest {

    @TempDir
    Path dir;

    private ObjectMapper yaml;
    private SettingsManager manager;
    private ProfileStore store;
    private SettingsEditor editor;

    private Setting<Integer> eyes;
    private Setting<Boolean> autoEat;
    private Setting<String> chatPrefix;
    private Setting<String> logLevel;
    private Setting<List<String>> fuels;

    @BeforeEach
    void setUp() throws Exception {
        yaml = SettingsYaml.createMapper();
        manager = new SettingsManager(yaml, w -> {
        });
        store = new ProfileStore(dir, yaml);
        store.ensureDefaults();
        editor = new SettingsEditor(manager, store, () -> manager.load(store.resolve(store.getActiveProfile(), new ArrayList<>())));

        eyes = Setting.integer("beatMinecraft.targetEyes", 14).range(0, 64).description("How many eyes.").register(manager);
        autoEat = Setting.bool("core.autoEat", true).register(manager);
        chatPrefix = Setting.string("core.chatLogPrefix", "[Alto Clef] ").register(manager);
        logLevel = Setting.string("core.logLevel", "NORMAL")
                .check(v -> List.of("ALL", "NORMAL").contains(v) ? null : "unknown level").register(manager);
        fuels = Setting.of("core.supportedFuels", new TypeReference<List<String>>() {
        }, List.of("coal")).register(manager);
    }

    private String profileText(String name) throws Exception {
        return Files.readString(dir.resolve("profiles").resolve(name + ".yml"));
    }

    @Test
    void setSavesIntoActiveProfileAndApplies() throws Exception {
        SettingsEditor.Change change = editor.set("core.autoEat", "false");

        assertFalse(autoEat.get());
        assertEquals("false", change.value());
        assertTrue(change.warnings().isEmpty());
        assertTrue(profileText("default").contains("autoEat: false"));
    }

    @Test
    void setClampsAndSavesTheClampedValue() throws Exception {
        SettingsEditor.Change change = editor.set("beatMinecraft.targetEyes", "500");

        assertEquals(64, eyes.get());
        assertEquals(1, change.warnings().size());
        assertTrue(profileText("default").contains("targetEyes: 64"));
    }

    @Test
    void invalidValueIsRejectedAndNotSaved() throws Exception {
        String before = profileText("default");

        assertThrows(IllegalArgumentException.class, () -> editor.set("beatMinecraft.targetEyes", "lots"));
        IllegalArgumentException level = assertThrows(IllegalArgumentException.class, () -> editor.set("core.logLevel", "LOUD"));
        assertEquals("core.logLevel: unknown level - not saved", level.getMessage());
        assertThrows(IllegalArgumentException.class, () -> editor.set("does.not.exist", "1"));

        assertEquals(before, profileText("default"));
        assertEquals(14, eyes.get());
        assertEquals("NORMAL", logLevel.get());
    }

    @Test
    void textValuesKeepSpecialCharactersAndQuotedSpaces() throws Exception {
        editor.set("core.chatLogPrefix", "\"[Bot] \"");
        assertEquals("[Bot] ", chatPrefix.get());

        editor.set("core.chatLogPrefix", "# @ :");
        assertEquals("# @ :", chatPrefix.get());
    }

    @Test
    void listValuesAreParsedAsYaml() throws Exception {
        editor.set("core.supportedFuels", "[coal, charcoal, blaze_rod]");

        assertEquals(List.of("coal", "charcoal", "blaze_rod"), fuels.get());
    }

    @Test
    void resetRemovesValueFromProfile() throws Exception {
        editor.set("core.autoEat", "false");
        editor.set("beatMinecraft.targetEyes", "12");

        editor.reset("core.autoEat");

        assertTrue(autoEat.get());
        assertEquals(12, eyes.get());
        assertFalse(profileText("default").contains("autoEat"));
    }

    @Test
    void setKeepsExtendsAndOtherValues() throws Exception {
        editor.createProfile("speedrun", "default");
        editor.set("beatMinecraft.targetEyes", "12");
        editor.set("core.autoEat", "false");

        assertEquals("default", store.getParent("speedrun"));
        assertEquals(12, eyes.get());
        assertFalse(autoEat.get());
    }

    @Test
    void createdProfileInheritsAndResetFallsBackToParent() throws Exception {
        editor.set("beatMinecraft.targetEyes", "12");          // in default
        editor.createProfile("speedrun", "default");
        assertEquals("speedrun", editor.getActiveProfile());
        assertEquals(12, eyes.get());                          // inherited

        editor.set("beatMinecraft.targetEyes", "10");          // override in speedrun
        assertEquals(10, eyes.get());

        editor.reset("beatMinecraft.targetEyes");
        assertEquals(12, eyes.get());                          // back to default profile's value
    }

    @Test
    void switchProfileLoadsItsValues() throws Exception {
        editor.createProfile("pvp", null);
        editor.set("core.autoEat", "false");

        editor.switchProfile("default");
        assertTrue(autoEat.get());

        editor.switchProfile("pvp");
        assertFalse(autoEat.get());
    }

    @Test
    void switchToMissingOrInvalidProfileFails() {
        assertThrows(IllegalArgumentException.class, () -> editor.switchProfile("nope"));
        assertThrows(IllegalArgumentException.class, () -> editor.switchProfile("../x"));
        assertThrows(IllegalArgumentException.class, () -> editor.createProfile("default", null));
        assertThrows(IllegalArgumentException.class, () -> editor.createProfile("x", "nope"));
        assertEquals("default", editor.getActiveProfile());
    }

    @Test
    void findByPrefixAndNonDefault() throws Exception {
        editor.set("core.autoEat", "false");

        assertEquals(List.of("core.autoEat", "core.chatLogPrefix", "core.logLevel", "core.supportedFuels"),
                editor.findByPrefix("core").stream().map(Setting::getKey).toList());
        assertEquals(List.of("core.autoEat"), editor.nonDefault().stream().map(Setting::getKey).toList());
    }

    @Test
    void describeShowsValueDefaultLimitsAndHelp() {
        List<String> lines = editor.describe(eyes);

        assertEquals("beatMinecraft.targetEyes = 14 (default)", lines.get(0));
        assertEquals("default: 14, min: 0, max: 64", lines.get(1));
        assertEquals("How many eyes.", lines.get(2));
    }
}
