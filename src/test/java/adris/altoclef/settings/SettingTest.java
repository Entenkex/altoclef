package adris.altoclef.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SettingTest {

    @Test
    void startsWithDefault() {
        Setting<Integer> s = Setting.integer("a.b", 5).range(0, 10).build();

        assertEquals(5, s.get());
        assertTrue(s.isDefault());
    }

    @Test
    void acceptsValueInRange() {
        Setting<Integer> s = Setting.integer("a.b", 5).range(0, 10).build();

        assertTrue(s.set(7).isEmpty());
        assertEquals(7, s.get());
        assertFalse(s.isDefault());
    }

    @Test
    void clampsAboveMaxToMax() {
        Setting<Integer> s = Setting.integer("a.b", 5).range(0, 10).build();

        assertTrue(s.set(99).isPresent());
        assertEquals(10, s.get());
    }

    @Test
    void clampsBelowMinToMin() {
        Setting<Float> s = Setting.floating("a.b", 1f).range(-1f, 5f).build();

        assertTrue(s.set(-3f).isPresent());
        assertEquals(-1f, s.get());
    }

    @Test
    void boundsThemselvesAreValid() {
        Setting<Integer> s = Setting.integer("a.b", 5).range(0, 10).build();

        assertTrue(s.set(0).isEmpty());
        assertTrue(s.set(10).isEmpty());
    }

    @Test
    void failedCheckFallsBackToDefault() {
        Setting<String> s = Setting.string("a.b", "NORMAL")
                .check(v -> v.equals("NORMAL") || v.equals("ALL") ? null : "unknown level").build();

        assertTrue(s.set("LOUD").isPresent());
        assertEquals("NORMAL", s.get());
    }

    @Test
    void nullFallsBackToDefault() {
        Setting<Integer> s = Setting.integer("a.b", 5).build();
        s.set(7);

        assertTrue(s.set(null).isPresent());
        assertEquals(5, s.get());
    }

    @Test
    void resetRestoresDefault() {
        Setting<Boolean> s = Setting.bool("a.b", true).build();
        s.set(false);

        s.reset();

        assertTrue(s.get());
    }

    @Test
    void invalidDefinitionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Setting.integer("a", 5).range(10, 0).build());
        assertThrows(IllegalArgumentException.class, () -> Setting.integer("a", 50).range(0, 10).build());
        assertThrows(IllegalArgumentException.class, () -> Setting.integer(" ", 5).build());
    }
}
