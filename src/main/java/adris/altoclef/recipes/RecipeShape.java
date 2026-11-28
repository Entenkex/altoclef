package adris.altoclef.recipes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What goes into a recipe: {@link Shaped}, {@link Shapeless} or {@link Cooking}.
 */
public interface RecipeShape {

    /**
     * All non-empty ingredients, one entry per item that is used up (a 3x3 block recipe has 9 entries).
     */
    List<Ingredient> ingredients();

    /**
     * Crafting grid with a fixed layout. Cells are row-major, {@code width * height} entries,
     * {@link Ingredient#EMPTY} for empty cells.
     */
    record Shaped(int width, int height, List<Ingredient> cells) implements RecipeShape {

        public Shaped {
            if (width < 1 || width > 3 || height < 1 || height > 3) {
                throw new IllegalArgumentException("Shaped recipe must be 1x1 to 3x3, got " + width + "x" + height);
            }
            if (cells.size() != width * height) {
                throw new IllegalArgumentException("Shaped recipe " + width + "x" + height + " needs "
                        + width * height + " cells, got " + cells.size());
            }
            cells = List.copyOf(cells.stream().map(c -> c == null ? Ingredient.EMPTY : c).toList());
        }

        /**
         * Builds a shape from rows like {@code "##", "##"} and a key ({@code '#'} → ingredient).
         * A space is an empty cell. Rows may have different lengths (padded with empty cells).
         */
        public static Shaped of(List<String> pattern, java.util.Map<Character, Ingredient> key) {
            if (pattern.isEmpty()) throw new IllegalArgumentException("Empty pattern");
            int width = pattern.stream().mapToInt(String::length).max().orElse(0);
            List<Ingredient> cells = new ArrayList<>();
            for (String row : pattern) {
                for (int x = 0; x < width; x++) {
                    char c = x < row.length() ? row.charAt(x) : ' ';
                    if (c == ' ') {
                        cells.add(Ingredient.EMPTY);
                    } else {
                        Ingredient ingredient = key.get(c);
                        if (ingredient == null) throw new IllegalArgumentException("Pattern uses '" + c + "' which is not in the key");
                        cells.add(ingredient);
                    }
                }
            }
            return new Shaped(width, pattern.size(), cells);
        }

        public Ingredient cell(int x, int y) {
            return cells.get(y * width + x);
        }

        /**
         * True if the recipe can be made in the 2x2 inventory grid.
         */
        public boolean fitsInventory() {
            return width <= 2 && height <= 2;
        }

        /**
         * The cells placed into the top-left corner of a {@code gridSize x gridSize} grid (2 or 3), row-major.
         * Index {@code y * gridSize + x}, like the crafting slots.
         */
        public List<Ingredient> toGrid(int gridSize) {
            if (gridSize != 2 && gridSize != 3) throw new IllegalArgumentException("Grid size must be 2 or 3");
            if (width > gridSize || height > gridSize) {
                throw new IllegalArgumentException(width + "x" + height + " recipe does not fit a " + gridSize + "x" + gridSize + " grid");
            }
            List<Ingredient> grid = new ArrayList<>(Collections.nCopies(gridSize * gridSize, Ingredient.EMPTY));
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    grid.set(y * gridSize + x, cell(x, y));
                }
            }
            return grid;
        }

        @Override
        public List<Ingredient> ingredients() {
            return cells.stream().filter(c -> !c.isEmpty()).toList();
        }
    }

    /**
     * Crafting grid, any layout.
     */
    record Shapeless(List<Ingredient> ingredients) implements RecipeShape {

        public Shapeless {
            ingredients = List.copyOf(ingredients.stream().filter(i -> i != null && !i.isEmpty()).toList());
            if (ingredients.isEmpty() || ingredients.size() > 9) {
                throw new IllegalArgumentException("Shapeless recipe needs 1 to 9 ingredients, got " + ingredients.size());
            }
        }

        public boolean fitsInventory() {
            return ingredients.size() <= 4;
        }
    }

    /**
     * Furnace / smoker / blast furnace / campfire: one input item per output.
     */
    record Cooking(Ingredient input, int cookTicks) implements RecipeShape {

        public Cooking {
            if (input == null || input.isEmpty()) throw new IllegalArgumentException("Cooking recipe needs an input");
            if (cookTicks < 0) throw new IllegalArgumentException("Negative cooking time");
        }

        @Override
        public List<Ingredient> ingredients() {
            return List.of(input);
        }
    }
}
