package adris.altoclef.recipes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One recipe, independent of the Minecraft version.
 *
 * @param id      {@code minecraft:oak_stairs} for game recipes, {@code altoclef:...} for local fallback recipes
 * @param station where it is made
 * @param shape   what goes in
 * @param output  what comes out
 * @param source  imported from the game or local fallback
 */
public record Recipe(String id, Station station, RecipeShape shape, ItemAmount output, Source source) {

    public enum Source {
        /** Sent by the server / read from the game's recipe manager. */
        GAME,
        /** Local data file shipped with the mod, used when the game has no recipe for the output. */
        FALLBACK
    }

    public Recipe {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Recipe without id");
        boolean cooking = shape instanceof RecipeShape.Cooking;
        if (cooking != station.isCooking()) {
            throw new IllegalArgumentException("Recipe " + id + ": station " + station + " does not fit " + shape.getClass().getSimpleName());
        }
    }

    /**
     * True if this crafting recipe fits the 2x2 inventory grid (cooking recipes: false).
     */
    public boolean fitsInventory() {
        if (shape instanceof RecipeShape.Shaped shaped) return shaped.fitsInventory();
        if (shape instanceof RecipeShape.Shapeless shapeless) return shapeless.fitsInventory();
        return false;
    }

    /**
     * How often the recipe has to be made for {@code wanted} output items.
     */
    public int craftsFor(int wanted) {
        if (wanted <= 0) return 0;
        return (wanted + output.count() - 1) / output.count();
    }

    /**
     * Ingredient → how many are used per craft (same ingredients summed, e.g. 3 planks for a slab).
     */
    public Map<Ingredient, Integer> inputsPerCraft() {
        Map<Ingredient, Integer> result = new LinkedHashMap<>();
        for (Ingredient ingredient : shape.ingredients()) {
            result.merge(ingredient, 1, Integer::sum);
        }
        return result;
    }

    /**
     * How many crafts are possible with {@code inventory} (item id → count), ingredients sharing items included.
     */
    public int possibleCrafts(Map<String, Integer> inventory) {
        Map<String, Integer> left = new java.util.HashMap<>(inventory);
        int crafts = 0;
        while (takeOneCraft(left)) crafts++;
        return crafts;
    }

    private boolean takeOneCraft(Map<String, Integer> left) {
        Map<String, Integer> backup = new java.util.HashMap<>(left);
        for (Ingredient ingredient : shape.ingredients()) {
            // Use the item we have most of, so alternatives are spread sensibly.
            String best = null;
            int bestCount = 0;
            for (String item : ingredient.items()) {
                int count = left.getOrDefault(item, 0);
                if (count > bestCount) {
                    best = item;
                    bestCount = count;
                }
            }
            if (best == null) {
                left.clear();
                left.putAll(backup);
                return false;
            }
            left.put(best, bestCount - 1);
        }
        return true;
    }

    @Override
    public String toString() {
        return id + " (" + station + ", " + source + ") -> " + output;
    }
}
