package adris.altoclef.recipes;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CraftableFromInventoryTest {

    private static Recipe shaped(String output, int count, List<String> pattern, Map<Character, Ingredient> key) {
        return new Recipe("minecraft:" + output, Station.CRAFTING, RecipeShape.Shaped.of(pattern, key),
                new ItemAmount(output, count), Recipe.Source.GAME);
    }

    private static RecipeRegistry woodRegistry() {
        Ingredient planks = Ingredient.anyOf("#minecraft:planks", List.of("oak_planks", "birch_planks"));
        RecipeRegistry registry = new RecipeRegistry();
        registry.add(new Recipe("minecraft:oak_planks", Station.CRAFTING,
                new RecipeShape.Shapeless(List.of(Ingredient.of("oak_log"))), new ItemAmount("oak_planks", 4), Recipe.Source.GAME));
        registry.add(shaped("stick", 4, List.of("P", "P"), Map.of('P', planks)));
        registry.add(shaped("wooden_pickaxe", 1, List.of("PPP", " S ", " S "), Map.of('P', planks, 'S', Ingredient.of("stick"))));
        return registry;
    }

    @Test
    void directlyFromTheInventory() {
        assertTrue(CraftableFromInventory.canCraft(woodRegistry(), "stick", Map.of("minecraft:birch_planks", 2)));
        assertFalse(CraftableFromInventory.canCraft(woodRegistry(), "stick", Map.of("minecraft:birch_planks", 1)));
    }

    @Test
    void severalSteps() {
        // pickaxe: 3 planks + 2 sticks (= 2 more planks) = 5 planks; one log gives only 4.
        assertFalse(CraftableFromInventory.canCraft(woodRegistry(), "wooden_pickaxe", Map.of("minecraft:oak_log", 1)));
        assertTrue(CraftableFromInventory.canCraft(woodRegistry(), "wooden_pickaxe", Map.of("minecraft:oak_log", 2)));
        // Leftovers of an intermediate craft are used: 4 sticks from one craft, the pickaxe needs 2.
        assertTrue(CraftableFromInventory.canCraft(woodRegistry(), "wooden_pickaxe",
                Map.of("minecraft:oak_log", 1, "minecraft:birch_planks", 1)));
    }

    @Test
    void doesNotChangeTheGivenInventory() {
        Map<String, Integer> inventory = new java.util.HashMap<>(Map.of("minecraft:oak_log", 2));
        CraftableFromInventory.canCraft(woodRegistry(), "wooden_pickaxe", inventory);
        assertEquals(Map.of("minecraft:oak_log", 2), inventory);
    }

    @Test
    void noEndlessLoopBetweenReversibleRecipes() {
        RecipeRegistry registry = new RecipeRegistry();
        registry.add(shaped("iron_block", 1, List.of("III", "III", "III"), Map.of('I', Ingredient.of("iron_ingot"))));
        registry.add(new Recipe("minecraft:iron_ingot_from_iron_block", Station.CRAFTING,
                new RecipeShape.Shapeless(List.of(Ingredient.of("iron_block"))), new ItemAmount("iron_ingot", 9), Recipe.Source.GAME));
        assertFalse(CraftableFromInventory.canCraft(registry, "iron_block", Map.of()));
        assertTrue(CraftableFromInventory.canCraft(registry, "iron_ingot", Map.of("minecraft:iron_block", 1)));
    }

    @Test
    void cookingRecipesDontCount() {
        RecipeRegistry registry = new RecipeRegistry();
        registry.add(new Recipe("minecraft:charcoal", Station.FURNACE,
                new RecipeShape.Cooking(Ingredient.of("oak_log"), 200), new ItemAmount("charcoal", 1), Recipe.Source.GAME));
        assertFalse(CraftableFromInventory.canCraft(registry, "charcoal", Map.of("minecraft:oak_log", 5)));
    }
}
