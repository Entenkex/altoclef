package adris.altoclef.recipes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * All known recipes, looked up by output item.
 * <p>
 * Game recipes (imported on world join) always win. Local fallback recipes are only used for outputs the game
 * has no recipe for. Does not depend on Minecraft.
 */
public final class RecipeRegistry {

    private final Map<String, Recipe> byId = new LinkedHashMap<>();
    private final Map<String, List<Recipe>> gameByOutput = new HashMap<>();
    private final Map<String, List<Recipe>> fallbackByOutput = new HashMap<>();
    private final Set<String> fallbackUsed = new TreeSet<>();

    /**
     * Adds recipes; a recipe with an id that is already known replaces the old one.
     */
    public synchronized void addAll(Collection<Recipe> recipes) {
        for (Recipe recipe : recipes) add(recipe);
    }

    public synchronized void add(Recipe recipe) {
        Recipe old = byId.put(recipe.id(), recipe);
        if (old != null) mapFor(old).getOrDefault(old.output().item(), new ArrayList<>()).remove(old);
        mapFor(recipe).computeIfAbsent(recipe.output().item(), k -> new ArrayList<>()).add(recipe);
    }

    /**
     * Removes all game recipes (e.g. when leaving a world). Fallback recipes stay.
     */
    public synchronized void clearGameRecipes() {
        byId.values().removeIf(r -> r.source() == Recipe.Source.GAME);
        gameByOutput.clear();
        fallbackUsed.clear();
    }

    private Map<String, List<Recipe>> mapFor(Recipe recipe) {
        return recipe.source() == Recipe.Source.GAME ? gameByOutput : fallbackByOutput;
    }

    /**
     * Recipes that make {@code item}: the game's if there are any, otherwise the fallback recipes.
     */
    public synchronized List<Recipe> byOutput(String item) {
        String id = ItemIds.normalize(item);
        List<Recipe> game = gameByOutput.get(id);
        if (game != null && !game.isEmpty()) return Collections.unmodifiableList(new ArrayList<>(game));
        List<Recipe> fallback = fallbackByOutput.getOrDefault(id, List.of());
        if (!fallback.isEmpty()) fallbackUsed.add(id);
        return Collections.unmodifiableList(new ArrayList<>(fallback));
    }

    /**
     * Like {@link #byOutput} but only recipes made at {@code station}.
     */
    public List<Recipe> byOutput(String item, Station station) {
        return byOutput(item).stream().filter(r -> r.station() == station).toList();
    }

    public synchronized Optional<Recipe> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public synchronized List<Recipe> all() {
        return List.copyOf(byId.values());
    }

    public synchronized int size(Recipe.Source source) {
        return (int) byId.values().stream().filter(r -> r.source() == source).count();
    }

    /**
     * Outputs for which a fallback recipe was handed out since the last {@link #clearGameRecipes()}
     * (to log once that the game did not provide them).
     */
    public synchronized Set<String> fallbackUsed() {
        return Set.copyOf(fallbackUsed);
    }

    /**
     * Picks the recipe for {@code item} that can be made most often with the inventory
     * (e.g. which bed colour or which wood). Ties: inventory-craftable first, then the order of the recipes.
     *
     * @param inventory item id → count
     */
    public Optional<Recipe> choose(String item, Map<String, Integer> inventory) {
        List<Recipe> candidates = byOutput(item);
        return candidates.stream().max(Comparator
                .comparingInt((Recipe r) -> r.possibleCrafts(inventory))
                .thenComparingInt(r -> ingredientsPresent(r, inventory))
                .thenComparing(Recipe::fitsInventory)
                .thenComparingInt(r -> -candidates.indexOf(r)));
    }

    /**
     * How many of the recipe's ingredient slots could be filled right now (for partially present materials).
     */
    private static int ingredientsPresent(Recipe recipe, Map<String, Integer> inventory) {
        int present = 0;
        for (Map.Entry<Ingredient, Integer> input : recipe.inputsPerCraft().entrySet()) {
            present += Math.min(input.getValue(), input.getKey().available(inventory));
        }
        return present;
    }
}
