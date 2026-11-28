package adris.altoclef.recipes;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Decides how the bot collects an {@link Ingredient}: the item collection code only knows catalogue names
 * ({@code "planks"} → collect any planks) or single items. A recipe ingredient is a set of items (e.g. 11 planks),
 * so it has to be mapped to one of those. Does not depend on Minecraft.
 * <p>
 * Order:
 * <ol>
 *     <li>a catalogue name with exactly these items</li>
 *     <li>an allowed item that is already in the inventory (most of it)</li>
 *     <li>the catalogue name with the most items that are all allowed (anything it collects fits)</li>
 *     <li>the first allowed single item that can be collected</li>
 * </ol>
 * In steps 3 and 4, items that can only be collected in another dimension (e.g. blackstone: nether) come last, so
 * a stone pickaxe doesn't send the bot to the nether (that made {@code @get iron_pickaxe} loop: nether portal →
 * flint and steel → iron → stone pickaxe → blackstone → nether …).
 */
public final class IngredientResolver {

    /**
     * How to collect an ingredient: by catalogue name or a single item id (exactly one of both is set).
     */
    public record Resolution(String catalogueName, String item) {
        public static Resolution catalogue(String name) {
            return new Resolution(name, null);
        }

        public static Resolution single(String item) {
            return new Resolution(null, item);
        }

        public boolean isCatalogue() {
            return catalogueName != null;
        }
    }

    private final Map<String, Set<String>> catalogue;
    private final Set<String> obtainableItems;
    private final Set<String> otherDimensionItems;

    /**
     * @param catalogue       catalogue name → item ids it collects (names with one item are fine too)
     * @param obtainableItems single items the catalogue can collect directly
     */
    public IngredientResolver(Map<String, Set<String>> catalogue, Set<String> obtainableItems) {
        this(catalogue, obtainableItems, Set.of());
    }

    /**
     * @param otherDimensionItems items whose catalogue entry only collects them outside the overworld
     */
    public IngredientResolver(Map<String, Set<String>> catalogue, Set<String> obtainableItems, Set<String> otherDimensionItems) {
        this.catalogue = new LinkedHashMap<>(catalogue);
        this.obtainableItems = new HashSet<>(obtainableItems);
        this.otherDimensionItems = new HashSet<>(otherDimensionItems);
    }

    public Optional<Resolution> resolve(Ingredient ingredient) {
        return resolve(ingredient, Map.of());
    }

    /**
     * @param inventory item id → count; an allowed item the bot already has wins over collecting another one
     *                  (e.g. suspicious stew: use the dandelion in the inventory instead of searching an allium)
     */
    public Optional<Resolution> resolve(Ingredient ingredient, Map<String, Integer> inventory) {
        if (ingredient.isEmpty()) return Optional.empty();
        Set<String> items = ingredient.items();
        if (items.size() == 1) {
            String item = items.iterator().next();
            boolean usable = obtainableItems.contains(item) || inventory.getOrDefault(item, 0) > 0;
            return usable ? Optional.of(Resolution.single(item)) : Optional.empty();
        }

        String best = null;
        int bestSize = 0;
        String bestFar = null;
        int bestFarSize = 0;
        for (Map.Entry<String, Set<String>> entry : catalogue.entrySet()) {
            Set<String> collects = entry.getValue();
            if (collects.isEmpty()) continue;
            if (collects.equals(items)) return Optional.of(Resolution.catalogue(entry.getKey()));
            if (collects.size() > 1 && items.containsAll(collects)) {
                if (otherDimensionItems.containsAll(collects)) {
                    if (collects.size() > bestFarSize) {
                        bestFar = entry.getKey();
                        bestFarSize = collects.size();
                    }
                } else if (collects.size() > bestSize) {
                    best = entry.getKey();
                    bestSize = collects.size();
                }
            }
        }

        String present = null;
        int presentCount = 0;
        for (String item : items) {
            int count = inventory.getOrDefault(item, 0);
            if (count > presentCount) {
                present = item;
                presentCount = count;
            }
        }
        if (present != null) return Optional.of(Resolution.single(present));

        if (best != null) return Optional.of(Resolution.catalogue(best));

        for (String item : items) {
            if (obtainableItems.contains(item) && !otherDimensionItems.contains(item)) return Optional.of(Resolution.single(item));
        }
        if (bestFar != null) return Optional.of(Resolution.catalogue(bestFar));
        for (String item : items) {
            if (obtainableItems.contains(item)) return Optional.of(Resolution.single(item));
        }
        return Optional.empty();
    }

    /**
     * True if every ingredient can be collected and none of them is the output itself (no loops like
     * "iron block from iron block").
     */
    public boolean canCollectAll(Recipe recipe) {
        for (Ingredient ingredient : recipe.shape().ingredients()) {
            if (ingredient.matches(recipe.output().item())) return false;
            if (resolve(ingredient).isEmpty()) return false;
        }
        return true;
    }
}
