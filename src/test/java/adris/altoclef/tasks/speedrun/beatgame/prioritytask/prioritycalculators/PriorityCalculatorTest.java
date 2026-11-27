package adris.altoclef.tasks.speedrun.beatgame.prioritytask.prioritycalculators;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization tests for the BeatMinecraft priority calculators (TODO step 3c).
 * Note the current inconsistency: {@link ItemPriorityCalculator} uses {@code count > max}
 * while {@link DistancePriorityCalculator} uses {@code count >= max}.
 */
class PriorityCalculatorTest {

    @Test
    void staticCalculatorReturnsFixedPriorityUntilAboveMax() {
        StaticItemPriorityCalculator calc = new StaticItemPriorityCalculator(300, 1, 2);

        assertEquals(300, calc.getPriority(0));
        assertEquals(300, calc.getPriority(2)); // == max still counts
        assertEquals(Double.NEGATIVE_INFINITY, calc.getPriority(3));
    }

    @Test
    void itemCalculatorLatchesMaxSatisfied() {
        StaticItemPriorityCalculator calc = new StaticItemPriorityCalculator(300, 1, 2);

        calc.getPriority(3);
        // Once above max, it stays "done" even if the items are lost again.
        assertEquals(Double.NEGATIVE_INFINITY, calc.getPriority(0));
    }

    @Test
    void itemCalculatorClampsCountToMinOnceMinWasExceeded() {
        int[] seen = new int[1];
        ItemPriorityCalculator calc = new ItemPriorityCalculator(4, 10) {
            @Override
            double calculatePriority(int count) {
                seen[0] = count;
                return count;
            }
        };

        calc.getPriority(5);
        calc.getPriority(1);

        assertEquals(4, seen[0], "count below min is raised to min after min was exceeded once");
    }

    @Test
    void ofCreatesSingleItemCalculator() {
        StaticItemPriorityCalculator calc = StaticItemPriorityCalculator.of(520);

        assertEquals(1, calc.minCount);
        assertEquals(1, calc.maxCount);
        assertEquals(520, calc.getPriority(1));
        assertEquals(Double.NEGATIVE_INFINITY, calc.getPriority(2));
    }

    @Test
    void distanceCalculatorIsInverseDistanceTimesMultiplier() {
        DistanceItemPriorityCalculator calc = new DistanceItemPriorityCalculator(1000, 100, 5, 2, 4);

        assertEquals(100.0, calc.getPriority(10), 1e-9);
        assertEquals(Double.NEGATIVE_INFINITY, calc.getPriority(Double.POSITIVE_INFINITY));
        assertEquals(Double.NEGATIVE_INFINITY, calc.getPriority(Integer.MAX_VALUE));
    }

    @Test
    void distanceCalculatorOnlyTakesCloseItemsAfterMinIsReached() {
        DistanceItemPriorityCalculator calc = new DistanceItemPriorityCalculator(1000, 100, 5, 2, 4);
        calc.update(2); // min reached (>=)

        assertEquals(25.0, calc.getPriority(4), 1e-9);              // closer than threshold: 1/4 * 100
        assertEquals(Double.NEGATIVE_INFINITY, calc.getPriority(6)); // too far, not needed anymore
    }

    @Test
    void distanceCalculatorStopsAtMax() {
        DistanceItemPriorityCalculator calc = new DistanceItemPriorityCalculator(1000, 100, 5, 2, 4);
        calc.update(4); // max reached (>=)

        assertEquals(Double.NEGATIVE_INFINITY, calc.getPriority(1));
    }
}
