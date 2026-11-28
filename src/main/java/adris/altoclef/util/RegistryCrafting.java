package adris.altoclef.util;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.recipes.IngredientResolver;
import adris.altoclef.recipes.Recipe;
import adris.altoclef.recipes.RecipeRegistry;
import adris.altoclef.recipes.Station;
import adris.altoclef.tasks.CraftInInventoryTask;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasks.container.CraftInTableTask;
import adris.altoclef.tasks.container.SmeltInFurnaceTask;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Crafting tasks for catalogue entries, built from the recipe registry (game recipes, local fallback otherwise)
 * instead of hand-written grids. Each ingredient is mapped to something the catalogue can collect
 * ({@link IngredientResolver}).
 */
public final class RegistryCrafting {

    private static IngredientResolver resolver;

    private RegistryCrafting() {
    }

    /**
     * @return a craft task for {@code count} of {@code output}, or null if no recipe can be used
     */
    public static ResourceTask craftTask(Item output, int count) {
        AltoClef mod = AltoClef.getInstance();
        RecipeRegistry registry = mod.getRecipeRegistryTracker().getRegistry();
        IngredientResolver resolver = resolver();
        String outputId = LegacyRecipeAdapter.id(output);

        Map<String, Integer> inventory = inventory(mod);
        Optional<Recipe> chosen = registry.choose(outputId, inventory,
                r -> r.station() == Station.CRAFTING && resolver.canCollectAll(r));
        if (chosen.isEmpty()) {
            Debug.logWarning("No usable crafting recipe for " + outputId);
            return null;
        }
        Recipe recipe = chosen.get();
        Optional<CraftingRecipe> legacy = LegacyRecipeAdapter.toLegacy(recipe,
                ingredient -> resolver.resolve(ingredient, inventory).flatMap(RegistryCrafting::toItemTarget));
        if (legacy.isEmpty()) {
            Debug.logWarning("Recipe " + recipe.id() + " uses items that do not exist in this version");
            return null;
        }
        RecipeTarget target = new RecipeTarget(output, count, legacy.get());
        return recipe.fitsInventory() ? new CraftInInventoryTask(target) : new CraftInTableTask(target);
    }

    /**
     * @return a furnace task for {@code count} of {@code output}, or null if no furnace recipe can be used.
     * Between several inputs (raw copper / copper ore) the inventory decides.
     */
    public static ResourceTask smeltTask(Item output, int count) {
        AltoClef mod = AltoClef.getInstance();
        RecipeRegistry registry = mod.getRecipeRegistryTracker().getRegistry();
        IngredientResolver resolver = resolver();
        String outputId = LegacyRecipeAdapter.id(output);
        Map<String, Integer> inventory = inventory(mod);

        Optional<Recipe> chosen = registry.choose(outputId, inventory,
                r -> r.station() == Station.FURNACE && resolver.canCollectAll(r));
        if (chosen.isEmpty()) {
            Debug.logWarning("No usable furnace recipe for " + outputId);
            return null;
        }
        Optional<ItemTarget> material = resolver.resolve(chosen.get().shape().ingredients().get(0), inventory)
                .flatMap(RegistryCrafting::toItemTarget)
                .map(target -> target.isCatalogueItem()
                        ? new ItemTarget(target.getCatalogueName(), count)
                        : new ItemTarget(target.getMatches(), count));
        if (material.isEmpty()) {
            Debug.logWarning("Recipe " + chosen.get().id() + " uses items that do not exist in this version");
            return null;
        }
        return new SmeltInFurnaceTask(new SmeltTarget(new ItemTarget(output, count), material.get()));
    }

    /**
     * Recipe + "must be the same item" mask for the CraftWithMatching* tasks ("any stairs": collect any planks,
     * then craft the wood we have most of). Taken from the registry recipe of one variant, e.g. oak stairs:
     * every slot with {@code sameMaterial} (oak planks) becomes {@code family} (any planks) and is marked.
     *
     * @return empty if the registry has no such recipe (e.g. hanging signs in old versions)
     */
    public static Optional<MatchingRecipe> matchingRecipe(Item representative, Item sameMaterial, ItemTarget family) {
        AltoClef mod = AltoClef.getInstance();
        if (mod == null || mod.getRecipeRegistryTracker() == null) return Optional.empty();
        RecipeRegistry registry = mod.getRecipeRegistryTracker().getRegistry();
        IngredientResolver resolver = resolver();
        String materialId = LegacyRecipeAdapter.id(sameMaterial);

        Optional<Recipe> recipe = registry.choose(LegacyRecipeAdapter.id(representative), Map.of(),
                r -> r.station() == Station.CRAFTING
                        && r.shape().ingredients().stream().anyMatch(i -> i.matches(materialId)));
        if (recipe.isEmpty()) return Optional.empty();
        Optional<CraftingRecipe> legacy = LegacyRecipeAdapter.toLegacy(recipe.get(), ingredient -> ingredient.matches(materialId)
                ? Optional.of(family)
                : resolver.resolve(ingredient).flatMap(RegistryCrafting::toItemTarget));
        if (legacy.isEmpty()) return Optional.empty();
        boolean[] mask = new boolean[legacy.get().getSlotCount()];
        for (int i = 0; i < mask.length; i++) mask[i] = legacy.get().getSlot(i) == family;
        return Optional.of(new MatchingRecipe(legacy.get(), mask));
    }

    public record MatchingRecipe(CraftingRecipe recipe, boolean[] sameMask) {
    }

    private static Optional<ItemTarget> toItemTarget(IngredientResolver.Resolution resolution) {
        if (resolution.isCatalogue()) return Optional.of(new ItemTarget(resolution.catalogueName(), 1));
        return LegacyRecipeAdapter.item(resolution.item()).map(item -> new ItemTarget(item, 1));
    }

    private static Map<String, Integer> inventory(AltoClef mod) {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack stack : mod.getItemStorage().getItemStacksPlayerInventory(true)) {
            if (!stack.isEmpty()) counts.merge(LegacyRecipeAdapter.id(stack.getItem()), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    /**
     * Built once from the catalogue (it is static and complete after class loading).
     */
    private static synchronized IngredientResolver resolver() {
        if (resolver == null) {
            Map<String, Set<String>> catalogue = new LinkedHashMap<>();
            for (Map.Entry<String, Item[]> entry : TaskCatalogue.catalogueEntries().entrySet()) {
                Set<String> ids = new LinkedHashSet<>();
                for (Item item : entry.getValue()) ids.add(LegacyRecipeAdapter.id(item));
                catalogue.put(entry.getKey(), ids);
            }
            Set<String> obtainable = new HashSet<>();
            for (Item item : TaskCatalogue.directlyObtainableItems()) obtainable.add(LegacyRecipeAdapter.id(item));
            resolver = new IngredientResolver(catalogue, obtainable);
        }
        return resolver;
    }
}
