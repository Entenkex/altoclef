package adris.altoclef.recipes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Can an item be crafted from what is in the inventory, possibly in several steps (log → planks → sticks)?
 * Only crafting recipes; nothing is collected. Pure, works on item id → count.
 */
public final class CraftableFromInventory {

    private CraftableFromInventory() {
    }

    public static boolean canCraft(RecipeRegistry registry, String item, Map<String, Integer> inventory) {
        return craft(registry, ItemIds.normalize(item), new HashMap<>(inventory), new HashSet<>()) != null;
    }

    /**
     * Crafts {@code item} once on a copy of {@code inventory}.
     *
     * @param path items being crafted further up (no loops like "iron block from iron ingots from iron block")
     * @return the inventory afterwards (with the output added), null if not possible
     */
    private static Map<String, Integer> craft(RecipeRegistry registry, String item, Map<String, Integer> inventory, Set<String> path) {
        if (!path.add(item)) return null;
        try {
            for (Recipe recipe : registry.byOutput(item, Station.CRAFTING)) {
                Map<String, Integer> left = new HashMap<>(inventory);
                if (takeIngredients(registry, recipe, left, path)) {
                    left.merge(recipe.output().item(), recipe.output().count(), Integer::sum);
                    return left;
                }
            }
            return null;
        } finally {
            path.remove(item);
        }
    }

    private static boolean takeIngredients(RecipeRegistry registry, Recipe recipe, Map<String, Integer> left, Set<String> path) {
        for (Ingredient ingredient : recipe.shape().ingredients()) {
            if (ingredient.isEmpty()) continue;
            if (takeOne(ingredient, left)) continue;
            // Not in the inventory: try to craft one of the alternatives, then take it.
            boolean crafted = false;
            for (String alternative : ingredient.items()) {
                Map<String, Integer> after = craft(registry, alternative, left, path);
                if (after != null) {
                    left.clear();
                    left.putAll(after);
                    crafted = takeOne(ingredient, left);
                    if (crafted) break;
                }
            }
            if (!crafted) return false;
        }
        return true;
    }

    private static boolean takeOne(Ingredient ingredient, Map<String, Integer> left) {
        for (String item : ingredient.items()) {
            int count = left.getOrDefault(item, 0);
            if (count > 0) {
                left.put(item, count - 1);
                return true;
            }
        }
        return false;
    }
}
