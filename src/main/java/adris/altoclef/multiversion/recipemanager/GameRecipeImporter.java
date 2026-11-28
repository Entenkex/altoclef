package adris.altoclef.multiversion.recipemanager;

import adris.altoclef.multiversion.RecipeVer;
import adris.altoclef.recipes.Ingredient;
import adris.altoclef.recipes.ItemAmount;
import adris.altoclef.recipes.Recipe;
import adris.altoclef.recipes.RecipeShape;
import adris.altoclef.recipes.Station;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.BlastingRecipe;
import net.minecraft.recipe.CampfireCookingRecipe;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.recipe.SmokingRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the game's recipes (client {@code RecipeManager}, sent by the server) into the version independent
 * {@link Recipe} model. Covers crafting (shaped, shapeless) and cooking (furnace, smoker, blast furnace, campfire).
 * Special recipes (fireworks, dyeing, map copies, ...) and smithing/stonecutting are skipped.
 */
public final class GameRecipeImporter {

    private GameRecipeImporter() {
    }

    public record Result(List<Recipe> recipes, int skipped, List<String> warnings) {
    }

    public static Result importAll(RecipeManagerWrapper manager, World world) {
        List<Recipe> recipes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int skipped = 0;
        for (WrappedRecipeEntry entry : manager.values()) {
            try {
                Recipe recipe = convert(entry, world);
                if (recipe == null) skipped++;
                else recipes.add(recipe);
            } catch (RuntimeException e) {
                skipped++;
                if (warnings.size() < 20) warnings.add(entry.id() + ": " + e.getMessage());
            }
        }
        return new Result(recipes, skipped, warnings);
    }

    /**
     * @return the recipe, or null if this kind of recipe is not part of the model
     */
    private static Recipe convert(WrappedRecipeEntry entry, World world) {
        net.minecraft.recipe.Recipe<?> value = entry.value();
        RecipeShape shape;
        Station station;
        // Exact classes only: special recipes like map extending subclass ShapedRecipe.
        if (value.getClass() == ShapedRecipe.class) {
            ShapedRecipe shaped = (ShapedRecipe) value;
            station = Station.CRAFTING;
            List<Ingredient> cells = new ArrayList<>();
            for (net.minecraft.recipe.Ingredient ingredient : shaped.getIngredients()) cells.add(ingredient(ingredient));
            shape = new RecipeShape.Shaped(shaped.getWidth(), shaped.getHeight(), cells);
        } else if (value.getClass() == ShapelessRecipe.class) {
            station = Station.CRAFTING;
            List<Ingredient> ingredients = new ArrayList<>();
            for (net.minecraft.recipe.Ingredient ingredient : value.getIngredients()) ingredients.add(ingredient(ingredient));
            shape = new RecipeShape.Shapeless(ingredients);
        } else if (value instanceof AbstractCookingRecipe cooking) {
            station = cookingStation(cooking);
            if (station == null) return null;
            shape = new RecipeShape.Cooking(ingredient(cooking.getIngredients().get(0)), cookTicks(cooking));
        } else {
            return null;
        }
        ItemStack output = RecipeVer.getOutput(value, world);
        if (output.isEmpty()) return null;
        return new Recipe(entry.id().toString(), station, shape,
                new ItemAmount(itemId(output), output.getCount()), Recipe.Source.GAME);
    }

    private static Station cookingStation(AbstractCookingRecipe recipe) {
        if (recipe instanceof SmeltingRecipe) return Station.FURNACE;
        if (recipe instanceof BlastingRecipe) return Station.BLAST_FURNACE;
        if (recipe instanceof SmokingRecipe) return Station.SMOKER;
        if (recipe instanceof CampfireCookingRecipe) return Station.CAMPFIRE;
        return null;
    }

    private static int cookTicks(AbstractCookingRecipe recipe) {
        return recipe.getCookingTime();
    }

    private static Ingredient ingredient(net.minecraft.recipe.Ingredient ingredient) {
        if (ingredient.isEmpty()) return Ingredient.EMPTY;
        Set<String> items = new LinkedHashSet<>();
        for (ItemStack stack : ingredient.getMatchingStacks()) {
            if (!stack.isEmpty()) items.add(itemId(stack));
        }
        if (items.isEmpty()) return Ingredient.EMPTY;
        return Ingredient.anyOf(label(items), items);
    }

    /**
     * Short label for logs: the item, or "oak_planks|birch_planks|..." for alternatives.
     * (The game does not tell which tag an ingredient came from.)
     */
    private static String label(Set<String> items) {
        if (items.size() == 1) return items.iterator().next();
        List<String> names = new ArrayList<>();
        for (String id : items) {
            names.add(id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id);
            if (names.size() == 3) break;
        }
        return String.join("|", names) + (items.size() > 3 ? "|... (" + items.size() + ")" : "");
    }

    private static String itemId(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).toString();
    }
}
