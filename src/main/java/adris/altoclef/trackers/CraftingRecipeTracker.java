package adris.altoclef.trackers;

import adris.altoclef.AltoClef;
import adris.altoclef.recipes.Recipe;
import adris.altoclef.recipes.RecipeRegistry;
import adris.altoclef.recipes.Station;
import adris.altoclef.util.LegacyRecipeAdapter;
import adris.altoclef.util.RecipeTarget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Old recipe lookup by output item, now backed by the new recipe registry (see docs/recipe-model.md).
 * Keeps its interface until the callers move to the new model (roadmap 2.5).
 */
public class CraftingRecipeTracker extends Tracker{


    private final HashMap<Item, List<adris.altoclef.util.CraftingRecipe>> itemRecipeMap = new HashMap<>();
    private final HashMap<adris.altoclef.util.CraftingRecipe, ItemStack> recipeResultMap = new HashMap<>();

    private boolean shouldRebuild;

    public CraftingRecipeTracker(TrackerManager manager) {
        super(manager);
        shouldRebuild = true;
    }

    public List<adris.altoclef.util.CraftingRecipe> getRecipeForItem(Item item) {
        ensureUpdated();

        if (!hasRecipeForItem(item)) {
            mod.logWarning("trying to access recipe for unknown item: "+item);
            return null;
        }

        return itemRecipeMap.get(item);
    }

    public adris.altoclef.util.CraftingRecipe getFirstRecipeForItem(Item item) {
        ensureUpdated();

        if (!hasRecipeForItem(item)) {
            mod.logWarning("trying to access recipe for unknown item: "+item);
            return null;
        }

        return itemRecipeMap.get(item).get(0);
    }

    public List<RecipeTarget> getRecipeTarget(Item item, int targetCount) {
        ensureUpdated();

        List<RecipeTarget> targets = new ArrayList<>();
        for (adris.altoclef.util.CraftingRecipe recipe : getRecipeForItem(item)) {
            targets.add(new RecipeTarget(item, targetCount, recipe));
        }

        return targets;
    }

    public RecipeTarget getFirstRecipeTarget(Item item, int targetCount) {
        ensureUpdated();

        return new RecipeTarget(item, targetCount, getFirstRecipeForItem(item));
    }

    public boolean hasRecipeForItem(Item item) {
        ensureUpdated();
        return itemRecipeMap.containsKey(item);
    }

    public ItemStack getRecipeResult(adris.altoclef.util.CraftingRecipe recipe) {
        ensureUpdated();

        if (!hasRecipe(recipe)) {
            mod.logWarning("Trying to get result for unknown recipe: "+recipe);
            return null;
        }
        ItemStack result = recipeResultMap.get(recipe);

        return new ItemStack(result.getItem(), result.getCount());
    }

    public boolean hasRecipe(adris.altoclef.util.CraftingRecipe recipe) {
        ensureUpdated();
        return recipeResultMap.containsKey(recipe);
    }


    /**
     * Built from the new {@link adris.altoclef.recipes.RecipeRegistry} (game recipes, local fallback otherwise),
     * converted to the old grid format with the real recipe shape and all allowed items per slot.
     */
    @Override
    protected void updateState() {
        if (!shouldRebuild) return;

        // rebuild once we are in game and the game's recipes arrived (or the fallback is all we get)
        if (!AltoClef.inGame()) return;
        RecipeRegistryTracker registryTracker = mod.getRecipeRegistryTracker();
        if (registryTracker == null || !registryTracker.hasGameRecipes()) return;
        RecipeRegistry registry = registryTracker.getRegistry();

        Set<String> outputs = new LinkedHashSet<>();
        for (Recipe recipe : registry.all()) {
            if (recipe.station() == Station.CRAFTING) outputs.add(recipe.output().item());
        }
        for (String output : outputs) {
            Optional<Item> outputItem = LegacyRecipeAdapter.item(output);
            if (outputItem.isEmpty()) continue;
            for (Recipe recipe : registry.byOutput(output, Station.CRAFTING)) {
                Optional<adris.altoclef.util.CraftingRecipe> legacy = LegacyRecipeAdapter.toLegacy(recipe);
                if (legacy.isEmpty()) continue;
                itemRecipeMap.computeIfAbsent(outputItem.get(), k -> new ArrayList<>()).add(legacy.get());
                recipeResultMap.put(legacy.get(), new ItemStack(outputItem.get(), recipe.output().count()));
            }
        }

        itemRecipeMap.replaceAll((k,v) -> Collections.unmodifiableList(v));

        shouldRebuild = false;
    }

    @Override
    protected void reset() {
       shouldRebuild = true;
       itemRecipeMap.clear();
       recipeResultMap.clear();
    }

    @Override
    protected boolean isDirty() {
        return shouldRebuild;
    }
}
