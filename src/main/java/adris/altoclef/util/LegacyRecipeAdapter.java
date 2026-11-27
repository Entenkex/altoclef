package adris.altoclef.util;

import adris.altoclef.recipes.Ingredient;
import adris.altoclef.recipes.Recipe;
import adris.altoclef.recipes.RecipeShape;
import adris.altoclef.recipes.Station;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Bridge between the new {@link Recipe} model and the old {@link CraftingRecipe} (flat 2x2 / 3x3 grid of
 * {@link ItemTarget}s) that the crafting tasks still use. Goes away with roadmap step 2.5.
 */
public final class LegacyRecipeAdapter {

    private LegacyRecipeAdapter() {
    }

    /**
     * The crafting recipe as old grid: shaped recipes placed top-left (keeps their real shape), shapeless ingredients
     * filled in order. 2x2 if it fits the inventory grid, otherwise 3x3.
     *
     * @return empty for cooking recipes or if an ingredient has no item that exists in this Minecraft version
     */
    public static Optional<CraftingRecipe> toLegacy(Recipe recipe) {
        if (recipe.station() != Station.CRAFTING) return Optional.empty();
        int size = recipe.fitsInventory() ? 2 : 3;
        List<Ingredient> grid;
        if (recipe.shape() instanceof RecipeShape.Shaped shaped) {
            grid = shaped.toGrid(size);
        } else {
            grid = new ArrayList<>(java.util.Collections.nCopies(size * size, Ingredient.EMPTY));
            List<Ingredient> ingredients = recipe.shape().ingredients();
            for (int i = 0; i < ingredients.size(); i++) grid.set(i, ingredients.get(i));
        }
        ItemTarget[] slots = new ItemTarget[grid.size()];
        for (int i = 0; i < grid.size(); i++) {
            Ingredient ingredient = grid.get(i);
            if (ingredient.isEmpty()) {
                slots[i] = ItemTarget.EMPTY;
                continue;
            }
            Item[] items = items(ingredient.items());
            if (items.length == 0) return Optional.empty();
            slots[i] = new ItemTarget(items, 1);
        }
        return Optional.of(CraftingRecipe.newShapedRecipe(shortName(recipe), slots, recipe.output().count()));
    }

    /**
     * The output as Minecraft item, empty if it does not exist in this version.
     */
    public static Optional<Item> outputItem(Recipe recipe) {
        return item(recipe.output().item());
    }

    public static Optional<Item> item(String id) {
        Identifier identifier = Identifier.of(id);
        if (!Registries.ITEM.containsId(identifier)) return Optional.empty();
        return Optional.of(Registries.ITEM.get(identifier));
    }

    public static String id(Item item) {
        return Registries.ITEM.getId(item).toString();
    }

    /**
     * Item ids of an old grid cell, empty for an empty cell.
     */
    public static Set<String> ids(ItemTarget target) {
        Set<String> ids = new java.util.LinkedHashSet<>();
        if (target == null || target.isEmpty()) return ids;
        for (Item item : target.getMatches()) ids.add(id(item));
        return ids;
    }

    private static Item[] items(Set<String> ids) {
        List<Item> items = new ArrayList<>();
        for (String id : ids) item(id).ifPresent(items::add);
        return items.toArray(new Item[0]);
    }

    private static String shortName(Recipe recipe) {
        String id = recipe.output().item();
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }
}
