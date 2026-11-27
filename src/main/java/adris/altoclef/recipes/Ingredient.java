package adris.altoclef.recipes;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What one slot of a recipe accepts: one of several items (e.g. any planks).
 * Items are ids like {@code minecraft:oak_planks}. {@link #EMPTY} is an empty grid cell.
 *
 * @param items alternatives, sorted; empty for {@link #EMPTY}
 * @param label for display/logs, e.g. {@code #minecraft:planks} or the single item id
 */
public record Ingredient(Set<String> items, String label) {

    public static final Ingredient EMPTY = new Ingredient(Set.of(), "");

    public Ingredient {
        items = Collections.unmodifiableSet(new TreeSet<>(items));
        label = label == null ? "" : label;
    }

    public static Ingredient of(String item) {
        String id = ItemIds.normalize(item);
        return new Ingredient(Set.of(id), id);
    }

    public static Ingredient anyOf(String label, Collection<String> items) {
        TreeSet<String> ids = new TreeSet<>();
        for (String item : items) ids.add(ItemIds.normalize(item));
        if (ids.isEmpty()) return EMPTY;
        return new Ingredient(ids, label == null || label.isEmpty() ? String.join("|", ids) : label);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public boolean matches(String item) {
        return items.contains(ItemIds.normalize(item));
    }

    /**
     * How many of this ingredient could be taken from {@code inventory} (item id → count).
     */
    public int available(Map<String, Integer> inventory) {
        int total = 0;
        for (String item : items) total += inventory.getOrDefault(item, 0);
        return total;
    }

    @Override
    public String toString() {
        return isEmpty() ? "-" : label;
    }
}
