package adris.altoclef.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InventoryWatchTest {

    private static final String SWORD = "minecraft:stone_sword";
    private static final String SHOVEL = "minecraft:stone_shovel";

    @Test
    void firstUpdateOnlySetsTheBaseline() {
        InventoryWatch watch = new InventoryWatch();
        assertEquals(List.of(), watch.update(Map.of(), Set.of()));
    }

    @Test
    void brokenToolIsRecognisedByItsLastDurability() {
        InventoryWatch watch = new InventoryWatch();
        watch.update(Map.of(SWORD, new InventoryWatch.Tracked(1, 2)), Set.of());

        assertEquals(List.of(new InventoryWatch.Loss(SWORD, 1, 0, "broke")), watch.update(Map.of(), Set.of()));
    }

    @Test
    void botDropAndUnknownLoss() {
        InventoryWatch watch = new InventoryWatch();
        watch.update(Map.of(SWORD, new InventoryWatch.Tracked(1, 100), SHOVEL, new InventoryWatch.Tracked(2, 50)), Set.of());

        List<InventoryWatch.Loss> losses = watch.update(Map.of(SHOVEL, new InventoryWatch.Tracked(1, 50)), Set.of(SWORD));

        assertEquals(2, losses.size());
        assertTrue(losses.contains(new InventoryWatch.Loss(SWORD, 1, 0, "bot_drop")));
        assertTrue(losses.contains(new InventoryWatch.Loss(SHOVEL, 2, 1, "unknown")));
    }

    @Test
    void gainsAndUnchangedItemsAreNoLoss() {
        InventoryWatch watch = new InventoryWatch();
        watch.update(Map.of(SWORD, new InventoryWatch.Tracked(1, 100)), Set.of());
        assertEquals(List.of(), watch.update(Map.of(SWORD, new InventoryWatch.Tracked(1, 99),
                SHOVEL, new InventoryWatch.Tracked(1, 131)), Set.of()));
    }

    @Test
    void resetStartsANewBaseline() {
        InventoryWatch watch = new InventoryWatch();
        watch.update(Map.of(SWORD, new InventoryWatch.Tracked(1, 100)), Set.of());
        watch.reset();
        assertEquals(List.of(), watch.update(Map.of(), Set.of()), "after death the empty inventory is no loss");
    }
}
