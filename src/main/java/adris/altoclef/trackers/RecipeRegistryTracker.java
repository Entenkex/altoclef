package adris.altoclef.trackers;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.multiversion.recipemanager.GameRecipeImporter;
import adris.altoclef.multiversion.recipemanager.RecipeManagerWrapper;
import adris.altoclef.recipes.FallbackRecipes;
import adris.altoclef.recipes.Recipe;
import adris.altoclef.recipes.RecipeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the {@link RecipeRegistry} filled: local fallback recipes once at start, the game's recipes
 * after joining a world (they are sent by the server), cleared again when leaving.
 * <p>
 * Built lazily on first access. Recipes can arrive a few ticks after joining, so an empty import is retried.
 */
public class RecipeRegistryTracker extends Tracker {

    private static final int MAX_EMPTY_IMPORTS = 200;

    private final RecipeRegistry registry = new RecipeRegistry();
    private boolean gameRecipesLoaded = false;
    private int emptyImports = 0;
    private GameRecipeImporter.Result lastImport;
    private final List<String> fallbackWarnings = new ArrayList<>();

    public RecipeRegistryTracker(TrackerManager manager) {
        super(manager);
        registry.addAll(FallbackRecipes.loadFromClasspath(fallbackWarnings));
        fallbackWarnings.forEach(w -> Debug.logInternal("Recipes: " + w));
        Debug.logInternal("Recipes: " + registry.size(Recipe.Source.FALLBACK) + " fallback recipes loaded");
    }

    public RecipeRegistry getRegistry() {
        ensureUpdated();
        return registry;
    }

    /**
     * True once the game's recipes were imported in the current world.
     */
    public boolean hasGameRecipes() {
        ensureUpdated();
        return gameRecipesLoaded;
    }

    public GameRecipeImporter.Result getLastImport() {
        return lastImport;
    }

    public List<String> getFallbackWarnings() {
        return List.copyOf(fallbackWarnings);
    }

    @Override
    protected void updateState() {
        if (gameRecipesLoaded || !AltoClef.inGame()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler network = client.getNetworkHandler();
        if (network == null || client.world == null) return;
        RecipeManagerWrapper manager = RecipeManagerWrapper.of(network.getRecipeManager());
        if (manager == null) return;

        GameRecipeImporter.Result result = GameRecipeImporter.importAll(manager, client.world);
        if (result.recipes().isEmpty() && ++emptyImports < MAX_EMPTY_IMPORTS) {
            // Probably not received from the server yet.
            return;
        }
        registry.addAll(result.recipes());
        lastImport = result;
        gameRecipesLoaded = true;
        Debug.logInternal("Recipes: imported " + result.recipes().size() + " game recipes (" + result.skipped()
                + " skipped), " + registry.size(Recipe.Source.FALLBACK) + " fallback recipes available");
        result.warnings().forEach(w -> Debug.logInternal("Recipes: could not import " + w));
        if (result.recipes().isEmpty()) {
            Debug.logWarning("The server sent no recipes, only the built-in fallback recipes are used.");
        }
    }

    @Override
    protected void reset() {
        registry.clearGameRecipes();
        gameRecipesLoaded = false;
        emptyImports = 0;
        lastImport = null;
    }
}
