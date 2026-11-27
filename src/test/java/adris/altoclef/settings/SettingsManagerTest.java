package adris.altoclef.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettingsManagerTest {

    enum Mode {FAST, SMART, OFF}

    private ObjectMapper yaml;
    private List<String> warnings;
    private SettingsManager manager;

    @BeforeEach
    void setUp() {
        yaml = SettingsYaml.createMapper();
        warnings = new ArrayList<>();
        manager = new SettingsManager(yaml, warnings::add);
    }

    private ObjectNode profile(String text) throws JsonProcessingException {
        return (ObjectNode) yaml.readTree(text);
    }

    @Test
    void loadsNestedAndFlatKeys() throws Exception {
        Setting<String> prefix = Setting.string("core.commandPrefix", "@").register(manager);
        Setting<Integer> eyes = Setting.integer("beatMinecraft.targetEyes", 14).range(0, 64).register(manager);

        manager.load(profile("""
                core:
                  commandPrefix: "#"
                beatMinecraft.targetEyes: 12
                """));

        assertEquals("#", prefix.get());
        assertEquals(12, eyes.get());
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void clampsOutOfRangeValuesWithWarning() throws Exception {
        Setting<Integer> eyes = Setting.integer("beatMinecraft.targetEyes", 14).range(0, 64).register(manager);
        Setting<Float> reach = Setting.floating("core.entityReachRange", 4f).range(0f, 10f).register(manager);

        manager.load(profile("""
                beatMinecraft: {targetEyes: 500}
                core: {entityReachRange: -2}
                """));

        assertEquals(64, eyes.get());
        assertEquals(0f, reach.get());
        assertEquals(2, warnings.size());
    }

    @Test
    void wrongTypeFallsBackToDefault() throws Exception {
        Setting<Integer> eyes = Setting.integer("beatMinecraft.targetEyes", 14).register(manager);
        Setting<Mode> mode = Setting.enumeration("core.mode", Mode.class, Mode.SMART).register(manager);

        manager.load(profile("""
                beatMinecraft: {targetEyes: lots}
                core: {mode: TURBO}
                """));

        assertEquals(14, eyes.get());
        assertEquals(Mode.SMART, mode.get());
        assertEquals(2, warnings.size());
        assertTrue(warnings.get(0).contains("expected a whole number"), warnings.get(0));
        assertTrue(warnings.get(1).contains("expected one of FAST, SMART, OFF"), warnings.get(1));
    }

    @Test
    void enumsAreCaseInsensitive() throws Exception {
        Setting<Mode> mode = Setting.enumeration("core.mode", Mode.class, Mode.SMART).register(manager);

        manager.load(profile("core: {mode: fast}"));

        assertEquals(Mode.FAST, mode.get());
    }

    @Test
    void listsAreRead() throws Exception {
        Setting<List<String>> formats = Setting.of("butler.whisperFormats", new TypeReference<List<String>>() {
        }, List.of("{from} {to} {message}")).register(manager);

        manager.load(profile("""
                butler:
                  whisperFormats:
                    - "{from} whispers: {message}"
                    - "[{from} -> {to}] {message}"
                """));

        assertEquals(List.of("{from} whispers: {message}", "[{from} -> {to}] {message}"), formats.get());
    }

    @Test
    void reloadResetsValuesThatAreNoLongerInTheProfile() throws Exception {
        Setting<Boolean> autoEat = Setting.bool("core.autoEat", true).register(manager);
        manager.load(profile("core: {autoEat: false}"));
        assertFalse(autoEat.get());

        manager.load(profile("core: {}"));

        assertTrue(autoEat.get());
    }

    @Test
    void lateRegistrationGetsProfileValue() throws Exception {
        manager.load(profile("task: {collectFood: {units: 50}}"));
        assertEquals(List.of("task.collectFood.units"), manager.getUnknownKeys());

        Setting<Integer> units = Setting.integer("task.collectFood.units", 20).register(manager);

        assertEquals(50, units.get());
        assertTrue(manager.getUnknownKeys().isEmpty());
    }

    @Test
    void reportsUnknownKeys() throws Exception {
        Setting.bool("core.autoEat", true).register(manager);

        manager.load(profile("""
                core:
                  autoEat: false
                  autoEet: true
                butler: {useWhitelist: true}
                """));

        assertEquals(List.of("core.autoEet", "butler.useWhitelist"), manager.getUnknownKeys());
    }

    @Test
    void duplicateKeysAreRejected() {
        Setting.bool("core.autoEat", true).register(manager);

        assertThrows(IllegalArgumentException.class, () -> Setting.bool("core.autoEat", false).register(manager));
    }

    @Test
    void setAtRuntimeClampsToo() {
        Setting<Integer> eyes = Setting.integer("beatMinecraft.targetEyes", 14).range(0, 64).register(manager);

        assertTrue(manager.set("beatMinecraft.targetEyes", yaml.getNodeFactory().numberNode(100)).isPresent());
        assertEquals(64, eyes.get());
        assertTrue(manager.set("does.not.exist", yaml.getNodeFactory().numberNode(1)).isPresent());
    }

    @Test
    void failingWarningOutputDoesNotAbortLoading() throws Exception {
        // Regression: in game, the first warning threw (settings not set yet) and the rest of loading was skipped.
        SettingsManager fragile = new SettingsManager(yaml, w -> {
            throw new NullPointerException("chat not ready");
        });
        Setting<Integer> first = Setting.integer("a.first", 5).range(0, 10).register(fragile);
        Setting<Integer> second = Setting.integer("a.second", 5).range(0, 10).register(fragile);
        List<Boolean> listenerRan = new ArrayList<>();
        fragile.addReloadListener(() -> listenerRan.add(true));

        List<String> result = fragile.load(profile("a: {first: 99, second: 99}"));

        assertEquals(10, first.get());
        assertEquals(10, second.get());
        assertEquals(2, result.size());
        assertEquals(List.of(true), listenerRan);
    }

    @Test
    void reloadListenersRunAfterLoad() throws Exception {
        Setting<Boolean> autoEat = Setting.bool("core.autoEat", true).register(manager);
        List<Boolean> seen = new ArrayList<>();
        manager.addReloadListener(() -> seen.add(autoEat.get()));

        manager.load(profile("core: {autoEat: false}"));

        assertEquals(List.of(false), seen);
    }

    @Test
    void nonDefaultValuesOnlyContainsChangedSettings() throws Exception {
        Setting.bool("core.autoEat", true).register(manager);
        Setting.integer("beatMinecraft.targetEyes", 14).register(manager);

        manager.load(profile("beatMinecraft: {targetEyes: 12}"));

        assertEquals(profile("beatMinecraft: {targetEyes: 12}"), manager.nonDefaultValues());
    }
}
