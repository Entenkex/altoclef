package adris.altoclef.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DropOrMineTest {

    private static final double NONE = Double.POSITIVE_INFINITY;

    @Test
    void enoughOnTheGroundIsPickedUpFirst() {
        // Gravel patch: next gravel 1.5 blocks away, 5 flint lying 12 blocks away, 1 flint needed.
        assertTrue(DropOrMine.preferDrop(12, 1.5, 5, 1));
        // Before the fix: 12² + 10 = 154 > 1.5² → mined the gravel.
    }

    @Test
    void notEnoughOnTheGroundUsesDistanceWithABonus() {
        assertTrue(DropOrMine.preferDrop(5, 2, 1, 3), "drop at most 4 blocks further than the block");
        assertFalse(DropOrMine.preferDrop(10, 2, 1, 3), "far drop that doesn't cover the need: mine");
    }

    @Test
    void enoughButTooFarAway() {
        assertFalse(DropOrMine.preferDrop(40, 2, 10, 1));
    }

    @Test
    void onlyOneOptionLeft() {
        assertTrue(DropOrMine.preferDrop(50, NONE, 1, 5));
        assertFalse(DropOrMine.preferDrop(NONE, 50, 0, 5));
        assertFalse(DropOrMine.preferDrop(NONE, NONE, 0, 5));
    }
}
