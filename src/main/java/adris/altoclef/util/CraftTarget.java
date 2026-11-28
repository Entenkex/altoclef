package adris.altoclef.util;

import adris.altoclef.multiversion.recipemanager.RecipeManagerWrapper;
import adris.altoclef.multiversion.recipemanager.WrappedRecipeEntry;
import adris.altoclef.recipes.Ingredient;
import adris.altoclef.recipes.Recipe;
import adris.altoclef.recipes.RecipeShape;
import adris.altoclef.recipes.Station;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * What a crafting task crafts: a crafting {@link Recipe} from the registry, the wanted output count, and the recipe
 * placed into the crafting grid as {@link ItemTarget}s (what to collect and put into each slot).
 * <p>
 * Replaces the old {@code CraftingRecipe} + {@code RecipeTarget}: the grid comes from the real recipe
 * ({@link RecipeShape.Shaped#toGrid}), and the recipe id is known, so the recipe book can be used directly.
 */
public final class CraftTarget {

    private final Recipe recipe;
    private final Item output;
    private final int targetCount;
    private final ItemTarget[] grid;

    private CraftTarget(Recipe recipe, Item output, int targetCount, ItemTarget[] grid) {
        this.recipe = recipe;
        this.output = output;
        this.targetCount = targetCount;
        this.grid = grid;
    }

    /**
     * @param cell which {@link ItemTarget} a non-empty ingredient becomes (e.g. a catalogue name, so the bot knows
     *             how to collect it); empty = this recipe can't be used
     * @return empty for non-crafting recipes, if the output doesn't exist in this version or a cell can't be filled
     */
    public static Optional<CraftTarget> of(Recipe recipe, int targetCount, Function<Ingredient, Optional<ItemTarget>> cell) {
        if (recipe.station() != Station.CRAFTING) return Optional.empty();
        Optional<Item> output = MinecraftItemIds.item(recipe.output().item());
        if (output.isEmpty()) return Optional.empty();
        int size = recipe.fitsInventory() ? 2 : 3;
        List<Ingredient> cells;
        if (recipe.shape() instanceof RecipeShape.Shaped shaped) {
            cells = shaped.toGrid(size);
        } else {
            // Shapeless: the slots don't matter, fill them in order.
            cells = new ArrayList<>(Collections.nCopies(size * size, Ingredient.EMPTY));
            List<Ingredient> ingredients = recipe.shape().ingredients();
            for (int i = 0; i < ingredients.size(); i++) cells.set(i, ingredients.get(i));
        }
        ItemTarget[] grid = new ItemTarget[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            Ingredient ingredient = cells.get(i);
            if (ingredient.isEmpty()) {
                grid[i] = ItemTarget.EMPTY;
                continue;
            }
            Optional<ItemTarget> target = cell.apply(ingredient);
            if (target.isEmpty()) return Optional.empty();
            grid[i] = target.get();
        }
        return Optional.of(new CraftTarget(recipe, output.get(), targetCount, grid));
    }

    /**
     * Every cell becomes "any of the ingredient's items" (no catalogue names).
     */
    public static Optional<CraftTarget> of(Recipe recipe, int targetCount) {
        return of(recipe, targetCount, ingredient -> {
            Item[] items = MinecraftItemIds.items(ingredient.items());
            return items.length == 0 ? Optional.empty() : Optional.of(new ItemTarget(items, 1));
        });
    }

    public CraftTarget withTargetCount(int count) {
        return new CraftTarget(recipe, output, count, grid);
    }

    /**
     * Same recipe with the given grid cells replaced (e.g. "any planks" instead of "oak planks").
     */
    public CraftTarget withGrid(ItemTarget[] newGrid) {
        if (newGrid.length != grid.length) throw new IllegalArgumentException("grid size " + newGrid.length + " != " + grid.length);
        return new CraftTarget(recipe, output, targetCount, newGrid.clone());
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public Item getOutputItem() {
        return output;
    }

    public int getTargetCount() {
        return targetCount;
    }

    /**
     * Items one craft produces.
     */
    public int outputCount() {
        return recipe.output().count();
    }

    public ItemTarget getSlot(int index) {
        ItemTarget slot = grid[index];
        return slot != null ? slot : ItemTarget.EMPTY;
    }

    public int getSlotCount() {
        return grid.length;
    }

    public ItemTarget[] getSlots() {
        return grid.clone();
    }

    /**
     * Needs the 3x3 crafting table.
     */
    public boolean isBig() {
        return grid.length > 4;
    }

    /**
     * The game's recipe for the recipe book; empty for local fallback recipes or when not connected.
     */
    public Optional<WrappedRecipeEntry> gameRecipe() {
        if (recipe.source() != Recipe.Source.GAME) return Optional.empty();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) return Optional.empty();
        RecipeManagerWrapper manager = RecipeManagerWrapper.of(client.getNetworkHandler().getRecipeManager());
        if (manager == null) return Optional.empty();
        // Plain variable: the preprocessor pattern for Identifier.of only matches simple arguments.
        String recipeId = recipe.id();
        return manager.get(Identifier.of(recipeId));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CraftTarget that)) return false;
        return targetCount == that.targetCount && recipe.id().equals(that.recipe.id())
                && Objects.equals(output, that.output) && Arrays.equals(grid, that.grid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipe.id(), output, targetCount);
    }

    @Override
    public String toString() {
        String name = recipe.id().startsWith("minecraft:") ? recipe.id().substring("minecraft:".length()) : recipe.id();
        return targetCount == 1 ? "Recipe{" + name + "}" : "Recipe{" + name + " x " + targetCount + "}";
    }
}
