package adris.altoclef.util;

import adris.altoclef.AltoClef;
import adris.altoclef.multiversion.recipemanager.RecipeManagerWrapper;
import adris.altoclef.multiversion.recipemanager.WrappedRecipeEntry;
import adris.altoclef.recipes.Recipe;
import adris.altoclef.recipes.RecipeComparison;
import adris.altoclef.recipes.RecipeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the game's recipe (with id) for an old {@link CraftingRecipe}, needed to craft via the recipe book.
 * Replaces {@code JankCraftingRecipeMapping}, which rebuilt its map on every call and matched by "ingredients
 * are a subset" (could pick the wrong recipe).
 */
public final class GameRecipeLookup {

    private GameRecipeLookup() {
    }

    public static Optional<WrappedRecipeEntry> find(CraftingRecipe recipe, Item output) {
        AltoClef mod = AltoClef.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();
        if (mod == null || client.getNetworkHandler() == null) return Optional.empty();

        RecipeRegistry registry = mod.getRecipeRegistryTracker().getRegistry();
        List<Set<String>> grid = new ArrayList<>();
        for (ItemTarget slot : recipe.getSlots()) grid.add(LegacyRecipeAdapter.ids(slot));
        RecipeComparison.Result match = RecipeComparison.compare(LegacyRecipeAdapter.id(output), grid, recipe.outputCount(), registry);
        // The recipe book places the items itself, so the layout of the old grid does not matter.
        boolean usable = match.closest() != null && match.status().ordinal() <= RecipeComparison.Status.DIFFERENT_LAYOUT.ordinal();
        if (!usable || match.closest().source() != Recipe.Source.GAME) return Optional.empty();

        RecipeManagerWrapper manager = RecipeManagerWrapper.of(client.getNetworkHandler().getRecipeManager());
        if (manager == null) return Optional.empty();
        // Plain variable: the preprocessor pattern for Identifier.of only matches simple arguments.
        String recipeId = match.closest().id();
        return manager.get(Identifier.of(recipeId));
    }
}
