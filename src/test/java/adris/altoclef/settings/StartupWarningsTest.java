package adris.altoclef.settings;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StartupWarningsTest {

    @Test
    void emptyGivesNoMessage() {
        assertTrue(new StartupWarnings().drainAsMessage("@").isEmpty());
    }

    @Test
    void messageListsEachWarningOnceAndIsShownOnlyOnce() {
        StartupWarnings warnings = new StartupWarnings();
        warnings.add("Settings: core.autoEat: expected true or false, using default true");
        warnings.add("Settings: unknown keys in profile \"default\" (typo?): core.autoEet");
        warnings.add("Settings: core.autoEat: expected true or false, using default true");

        StartupWarnings.Message message = warnings.drainAsMessage("#").orElseThrow();
        assertTrue(message.header().contains("2 problems"), message.header());
        List<String> lines = message.lines();
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).endsWith("- core.autoEat: expected true or false, using default true"), lines.get(0));
        assertTrue(lines.get(2).contains("#reload_settings"), lines.get(2));

        assertTrue(warnings.isEmpty());
        assertTrue(warnings.drainAsMessage("#").isEmpty());
    }

    @Test
    void clearDropsWarningsOfThePreviousLoad() {
        StartupWarnings warnings = new StartupWarnings();
        warnings.add("Settings: old problem");
        warnings.clear();
        assertTrue(warnings.isEmpty());
    }

    @Test
    void singleProblemIsSingular() {
        StartupWarnings warnings = new StartupWarnings();
        warnings.add("x");
        assertTrue(warnings.drainAsMessage("@").orElseThrow().header().contains("1 problem while"));
    }
}
