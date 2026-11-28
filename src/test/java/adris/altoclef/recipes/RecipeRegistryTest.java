package adris.altoclef.recipes;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecipeRegistryTest {

    private static Recipe bed(String color, Recipe.Source source) {
        Ingredient wool = Ingredient.of(color + "_wool");
        Ingredient planks = Ingredient.anyOf("#minecraft:planks", List.of("oak_planks", "spruce_planks"));
        return new Recipe((source == Recipe.Source.GAME ? "minecraft:" : "altoclef:") + color + "_bed", Station.CRAFTING,
                RecipeShape.Shaped.of(List.of("WWW", "PPP"), Map.of('W', wool, 'P', planks)),
                new ItemAmount(color + "_bed", 1), source);
    }

    private static Recipe torch(String id, String fuel, Recipe.Source source) {
        return new Recipe(id, Station.CRAFTING,
                RecipeShape.Shaped.of(List.of("C", "S"), Map.of('C', Ingredient.of(fuel), 'S', Ingredient.of("stick"))),
                new ItemAmount("torch", 4), source);
    }

    @Test
    void gameRecipesWinOverFallback() {
        RecipeRegistry registry = new RecipeRegistry();
        registry.add(torch("altoclef:torch", "coal", Recipe.Source.FALLBACK));
        assertEquals(1, registry.byOutput("torch").size());
        assertEquals(Recipe.Source.FALLBACK, registry.byOutput("torch").get(0).source());
        assertEquals(java.util.Set.of("minecraft:torch"), registry.fallbackUsed());

        registry.addAll(List.of(torch("minecraft:torch", "coal", Recipe.Source.GAME),
                torch("minecraft:torch_from_charcoal", "charcoal", Recipe.Source.GAME)));
        List<Recipe> torches = registry.byOutput("minecraft:torch");
        assertEquals(2, torches.size());
        assertTrue(torches.stream().allMatch(r -> r.source() == Recipe.Source.GAME));

        registry.clearGameRecipes();
        assertEquals(Recipe.Source.FALLBACK, registry.byOutput("torch").get(0).source());
        assertEquals(0, registry.size(Recipe.Source.GAME));
        assertEquals(1, registry.size(Recipe.Source.FALLBACK));
    }

    @Test
    void sameIdReplacesOldRecipe() {
        RecipeRegistry registry = new RecipeRegistry();
        registry.add(torch("minecraft:torch", "coal", Recipe.Source.GAME));
        registry.add(torch("minecraft:torch", "charcoal", Recipe.Source.GAME));
        assertEquals(1, registry.byOutput("torch").size());
        assertTrue(registry.byId("minecraft:torch").orElseThrow().shape().ingredients().get(0).matches("charcoal"));
    }

    @Test
    void stationFilterAndUnknownItems() {
        RecipeRegistry registry = new RecipeRegistry();
        registry.add(new Recipe("minecraft:charcoal", Station.FURNACE,
                new RecipeShape.Cooking(Ingredient.of("oak_log"), 200), new ItemAmount("charcoal", 1), Recipe.Source.GAME));
        assertEquals(1, registry.byOutput("charcoal", Station.FURNACE).size());
        assertTrue(registry.byOutput("charcoal", Station.CRAFTING).isEmpty());
        assertTrue(registry.byOutput("diamond").isEmpty());
        assertTrue(registry.choose("diamond", Map.of()).isEmpty());
    }

    @Test
    void chooseTakesTheColourWeCanMake() {
        // Replaces CraftWithMatchingWoolTask: "any bed" = choose among the per-colour recipes.
        RecipeRegistry registry = new RecipeRegistry();
        registry.addAll(List.of(bed("white", Recipe.Source.GAME), bed("red", Recipe.Source.GAME), bed("blue", Recipe.Source.GAME)));
        // Same output id for the test: register all three under one output item
        RecipeRegistry anyBed = new RecipeRegistry();
        for (Recipe r : registry.all()) {
            anyBed.add(new Recipe(r.id(), r.station(), r.shape(), new ItemAmount("bed", 1), r.source()));
        }

        Map<String, Integer> inventory = Map.of("minecraft:red_wool", 3, "minecraft:white_wool", 2,
                "minecraft:oak_planks", 3);
        assertEquals("minecraft:red_bed", anyBed.choose("bed", inventory).orElseThrow().id());

        // Nothing complete: the colour with most wool present wins
        Map<String, Integer> partial = Map.of("minecraft:blue_wool", 2, "minecraft:white_wool", 1);
        assertEquals("minecraft:blue_bed", anyBed.choose("bed", partial).orElseThrow().id());

        // Nothing at all: alphabetically first id (stable, independent of the order the game sends recipes in)
        assertEquals("minecraft:blue_bed", anyBed.choose("bed", Map.of()).orElseThrow().id());
    }
}
