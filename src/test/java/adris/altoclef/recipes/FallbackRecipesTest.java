package adris.altoclef.recipes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the shipped fallback files (src/main/resources/assets/altoclef/recipes).
 */
class FallbackRecipesTest {

    private static RecipeRegistry load(List<String> warnings) {
        RecipeRegistry registry = new RecipeRegistry();
        registry.addAll(FallbackRecipes.loadFromClasspath(warnings));
        return registry;
    }

    @Test
    void allShippedRecipesParse() {
        List<String> warnings = new ArrayList<>();
        RecipeRegistry registry = load(warnings);
        assertEquals(List.of(), warnings);
        assertTrue(registry.size(Recipe.Source.FALLBACK) > 700, "only " + registry.size(Recipe.Source.FALLBACK));
    }

    @Test
    void basicRecipesArePresent() {
        RecipeRegistry registry = load(new ArrayList<>());
        for (String item : List.of("crafting_table", "stick", "wooden_pickaxe", "stone_pickaxe", "iron_pickaxe",
                "diamond_pickaxe", "furnace", "chest", "torch", "bucket", "shield", "white_bed", "oak_boat",
                "iron_ingot", "gold_ingot", "glass", "charcoal", "stone", "bread", "iron_helmet", "ender_eye",
                // special recipe in the game (not imported), hand-written fallback
                "suspicious_stew")) {
            assertFalse(registry.byOutput(item).isEmpty(), "no fallback recipe for " + item);
        }
    }

    @Test
    void sticksFromAnyPlanksPlacedVertically() {
        RecipeRegistry registry = load(new ArrayList<>());
        Recipe sticks = registry.byOutput("stick").stream()
                .filter(r -> r.shape().ingredients().get(0).matches("oak_planks")).findFirst().orElseThrow();
        RecipeShape.Shaped shape = (RecipeShape.Shaped) sticks.shape();
        assertEquals(1, shape.width());
        assertEquals(2, shape.height());
        assertTrue(shape.cell(0, 0).matches("birch_planks"), "tag #planks resolved");
        assertEquals(4, sticks.output().count());
    }

    @Test
    void ironIngotCanBeSmeltedOrBlasted() {
        RecipeRegistry registry = load(new ArrayList<>());
        assertFalse(registry.byOutput("iron_ingot", Station.FURNACE).isEmpty());
        assertFalse(registry.byOutput("iron_ingot", Station.BLAST_FURNACE).isEmpty());
        Recipe chosen = registry.choose("iron_ingot", Map.of("minecraft:iron_nugget", 9)).orElseThrow();
        assertEquals(Station.CRAFTING, chosen.station(), "with 9 nuggets crafting is possible: " + chosen);
    }
}
