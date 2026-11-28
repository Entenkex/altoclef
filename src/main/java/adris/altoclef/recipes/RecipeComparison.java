package adris.altoclef.recipes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Compares an old hand-written crafting grid (TaskCatalogue) with the recipes in the registry,
 * to find mistakes in either before the hand-written ones are replaced. Does not depend on Minecraft.
 */
public final class RecipeComparison {

    private RecipeComparison() {
    }

    public enum Status {
        /** Same ingredients, a layout the game accepts and the same output count. */
        SAME,
        /** Ingredients and layout fit, but the output count differs. */
        DIFFERENT_COUNT,
        /** Same ingredients, but the game would not accept this layout (wrong shape). */
        DIFFERENT_LAYOUT,
        /** No registry recipe uses these ingredients. */
        DIFFERENT_INGREDIENTS,
        /** The registry has no crafting recipe for the output. */
        NO_RECIPE
    }

    public record Result(Status status, Recipe closest) {
    }

    /**
     * @param handGrid    4 or 9 cells (row-major), each the allowed item ids, empty set = empty cell
     * @param handCount   output count of the hand-written recipe
     */
    public static Result compare(String output, List<Set<String>> handGrid, int handCount, RecipeRegistry registry) {
        List<Recipe> candidates = registry.byOutput(output, Station.CRAFTING);
        if (candidates.isEmpty()) return new Result(Status.NO_RECIPE, null);

        Status best = Status.DIFFERENT_INGREDIENTS;
        Recipe closest = candidates.get(0);
        for (Recipe recipe : candidates) {
            Status status = compareOne(recipe, handGrid, handCount);
            if (status.ordinal() < best.ordinal()) {
                best = status;
                closest = recipe;
            }
        }
        return new Result(best, closest);
    }

    private static Status compareOne(Recipe recipe, List<Set<String>> handGrid, int handCount) {
        List<Set<String>> handItems = handGrid.stream().filter(s -> !s.isEmpty()).toList();
        if (!sameIngredients(handItems, recipe.shape().ingredients())) return Status.DIFFERENT_INGREDIENTS;

        boolean layoutOk = !(recipe.shape() instanceof RecipeShape.Shaped shaped) || layoutFits(handGrid, shaped);
        if (!layoutOk) return Status.DIFFERENT_LAYOUT;
        return recipe.output().count() == handCount ? Status.SAME : Status.DIFFERENT_COUNT;
    }

    /**
     * Every hand-written cell can be matched to a different recipe ingredient sharing at least one item.
     */
    static boolean sameIngredients(List<Set<String>> hand, List<Ingredient> recipe) {
        if (hand.size() != recipe.size()) return false;
        return match(hand, 0, recipe, new boolean[recipe.size()]);
    }

    private static boolean match(List<Set<String>> hand, int index, List<Ingredient> recipe, boolean[] used) {
        if (index == hand.size()) return true;
        for (int i = 0; i < recipe.size(); i++) {
            if (!used[i] && overlaps(hand.get(index), recipe.get(i))) {
                used[i] = true;
                if (match(hand, index + 1, recipe, used)) return true;
                used[i] = false;
            }
        }
        return false;
    }

    /**
     * The game accepts a shaped recipe anywhere in the grid and mirrored: compare the hand grid's
     * bounding box with the shape and its mirror image.
     */
    static boolean layoutFits(List<Set<String>> handGrid, RecipeShape.Shaped shape) {
        int size = handGrid.size() == 4 ? 2 : 3;
        int minX = size, minY = size, maxX = -1, maxY = -1;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (!handGrid.get(y * size + x).isEmpty()) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) return false;
        int width = maxX - minX + 1, height = maxY - minY + 1;
        if (width != shape.width() || height != shape.height()) return false;
        return fits(handGrid, size, minX, minY, shape, false) || fits(handGrid, size, minX, minY, shape, true);
    }

    private static boolean fits(List<Set<String>> grid, int size, int offX, int offY, RecipeShape.Shaped shape, boolean mirrored) {
        for (int y = 0; y < shape.height(); y++) {
            for (int x = 0; x < shape.width(); x++) {
                int sx = mirrored ? shape.width() - 1 - x : x;
                Set<String> hand = grid.get((offY + y) * size + offX + x);
                Ingredient cell = shape.cell(sx, y);
                if (hand.isEmpty() != cell.isEmpty()) return false;
                if (!hand.isEmpty() && !overlaps(hand, cell)) return false;
            }
        }
        return true;
    }

    private static boolean overlaps(Set<String> hand, Ingredient ingredient) {
        for (String item : hand) {
            if (ingredient.items().contains(item)) return true;
        }
        return false;
    }

    /**
     * Grid as text rows for reports, e.g. {@code [planks, -, -] [planks, -, -] ...}.
     */
    public static String describe(List<Ingredient> grid, int size) {
        List<String> rows = new ArrayList<>();
        for (int y = 0; y < size; y++) {
            List<String> cells = new ArrayList<>();
            for (int x = 0; x < size; x++) cells.add(grid.get(y * size + x).toString());
            rows.add("[" + String.join(", ", cells) + "]");
        }
        return String.join(" ", rows);
    }
}
