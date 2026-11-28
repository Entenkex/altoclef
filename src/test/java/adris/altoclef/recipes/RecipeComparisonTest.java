package adris.altoclef.recipes;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecipeComparisonTest {

    private static final Set<String> NONE = Set.of();
    private static final Set<String> ANY_PLANKS = Set.of("minecraft:oak_planks", "minecraft:birch_planks");
    private static final Set<String> STICK = Set.of("minecraft:stick");

    private static RecipeRegistry registryWith(Recipe... recipes) {
        RecipeRegistry registry = new RecipeRegistry();
        registry.addAll(List.of(recipes));
        return registry;
    }

    private static Recipe shaped(String output, int count, List<String> pattern, Map<Character, Ingredient> key) {
        return new Recipe("minecraft:" + output, Station.CRAFTING, RecipeShape.Shaped.of(pattern, key),
                new ItemAmount(output, count), Recipe.Source.GAME);
    }

    private static final Recipe OAK_SLAB = shaped("oak_slab", 6, List.of("###"), Map.of('#', Ingredient.of("oak_planks")));
    private static final Recipe SIGN = shaped("oak_sign", 3, List.of("###", "###", " | "),
            Map.of('#', Ingredient.of("oak_planks"), '|', Ingredient.of("stick")));

    @Test
    void slabInBottomRowIsAcceptedByTheGame() {
        // TaskCatalogue.shapedRecipeSlab puts the planks into the bottom row; the game accepts any position.
        List<Set<String>> hand = List.of(NONE, NONE, NONE, NONE, NONE, NONE, ANY_PLANKS, ANY_PLANKS, ANY_PLANKS);
        assertEquals(RecipeComparison.Status.SAME, RecipeComparison.compare("oak_slab", hand, 6, registryWith(OAK_SLAB)).status());
        assertEquals(RecipeComparison.Status.DIFFERENT_COUNT, RecipeComparison.compare("oak_slab", hand, 4, registryWith(OAK_SLAB)).status());
    }

    @Test
    void wrongShapeIsFound() {
        // Planks in a column instead of a row
        List<Set<String>> hand = List.of(ANY_PLANKS, NONE, NONE, ANY_PLANKS, NONE, NONE, ANY_PLANKS, NONE, NONE);
        assertEquals(RecipeComparison.Status.DIFFERENT_LAYOUT, RecipeComparison.compare("oak_slab", hand, 6, registryWith(OAK_SLAB)).status());
    }

    @Test
    void wrongIngredientsAndMissingRecipe() {
        List<Set<String>> hand = List.of(STICK, STICK, STICK, NONE, NONE, NONE, NONE, NONE, NONE);
        assertEquals(RecipeComparison.Status.DIFFERENT_INGREDIENTS, RecipeComparison.compare("oak_slab", hand, 6, registryWith(OAK_SLAB)).status());
        assertEquals(RecipeComparison.Status.NO_RECIPE, RecipeComparison.compare("diamond", hand, 1, registryWith(OAK_SLAB)).status());
    }

    @Test
    void mirroredShapeIsAccepted() {
        Recipe stairs = shaped("oak_stairs", 4, List.of("#  ", "## ", "###"), Map.of('#', Ingredient.of("oak_planks")));
        List<Set<String>> mirrored = List.of(NONE, NONE, ANY_PLANKS, NONE, ANY_PLANKS, ANY_PLANKS, ANY_PLANKS, ANY_PLANKS, ANY_PLANKS);
        assertTrue(RecipeComparison.layoutFits(mirrored, (RecipeShape.Shaped) stairs.shape()));
    }

    @Test
    void twoByTwoGridAndSign() {
        Recipe table = shaped("crafting_table", 1, List.of("##", "##"), Map.of('#', Ingredient.anyOf("#planks", ANY_PLANKS)));
        List<Set<String>> hand = List.of(ANY_PLANKS, ANY_PLANKS, ANY_PLANKS, ANY_PLANKS);
        assertEquals(RecipeComparison.Status.SAME, RecipeComparison.compare("crafting_table", hand, 1, registryWith(table)).status());

        List<Set<String>> sign = List.of(ANY_PLANKS, ANY_PLANKS, ANY_PLANKS, ANY_PLANKS, ANY_PLANKS, ANY_PLANKS, NONE, STICK, NONE);
        assertEquals(RecipeComparison.Status.SAME, RecipeComparison.compare("oak_sign", sign, 3, registryWith(SIGN)).status());
    }

    @Test
    void describeGrid() {
        assertEquals("[oak_planks, -] [-, -]", RecipeComparison.describe(
                List.of(new Ingredient(Set.of("minecraft:oak_planks"), "oak_planks"), Ingredient.EMPTY, Ingredient.EMPTY, Ingredient.EMPTY), 2));
    }
}
