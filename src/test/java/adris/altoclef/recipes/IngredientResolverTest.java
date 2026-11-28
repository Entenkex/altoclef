package adris.altoclef.recipes;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IngredientResolverTest {

    private static final Set<String> ALL_PLANKS = Set.of("minecraft:oak_planks", "minecraft:birch_planks", "minecraft:crimson_planks");

    private static IngredientResolver resolver() {
        Map<String, Set<String>> catalogue = new LinkedHashMap<>();
        catalogue.put("planks", ALL_PLANKS);
        catalogue.put("overworld_planks", Set.of("minecraft:oak_planks", "minecraft:birch_planks"));
        catalogue.put("oak_planks", Set.of("minecraft:oak_planks"));
        catalogue.put("log", Set.of("minecraft:oak_log", "minecraft:birch_log"));
        return new IngredientResolver(catalogue, Set.of("minecraft:oak_planks", "minecraft:coal", "minecraft:stick",
                "minecraft:oak_log", "minecraft:bamboo"));
    }

    @Test
    void exactCatalogueMatch() {
        assertEquals(Optional.of(IngredientResolver.Resolution.catalogue("planks")),
                resolver().resolve(Ingredient.anyOf("#planks", ALL_PLANKS)));
    }

    @Test
    void largestCatalogueSubset() {
        // #logs contains more than our "log" entry: collecting any "log" is still valid
        Ingredient logs = Ingredient.anyOf("#logs", List.of("oak_log", "birch_log", "oak_wood", "stripped_oak_log"));
        assertEquals(Optional.of(IngredientResolver.Resolution.catalogue("log")), resolver().resolve(logs));
    }

    @Test
    void singleItemsAndFirstObtainable() {
        assertEquals(Optional.of(IngredientResolver.Resolution.single("minecraft:stick")), resolver().resolve(Ingredient.of("stick")));
        assertTrue(resolver().resolve(Ingredient.of("nether_star")).isEmpty());
        // #coals {coal, charcoal}: no catalogue entry, but coal can be collected
        assertEquals(Optional.of(IngredientResolver.Resolution.single("minecraft:coal")),
                resolver().resolve(Ingredient.anyOf("#coals", List.of("coal", "charcoal"))));
        assertTrue(resolver().resolve(Ingredient.EMPTY).isEmpty());
    }

    @Test
    void canCollectAllRejectsMissingAndSelfReferences() {
        IngredientResolver resolver = resolver();
        Recipe sticks = new Recipe("minecraft:stick", Station.CRAFTING,
                RecipeShape.Shaped.of(List.of("#", "#"), Map.of('#', Ingredient.anyOf("#planks", ALL_PLANKS))),
                new ItemAmount("stick", 4), Recipe.Source.GAME);
        assertTrue(resolver.canCollectAll(sticks));

        Recipe star = new Recipe("x:beacon", Station.CRAFTING,
                new RecipeShape.Shapeless(List.of(Ingredient.of("nether_star"))), new ItemAmount("beacon", 1), Recipe.Source.GAME);
        assertFalse(resolver.canCollectAll(star));

        Recipe self = new Recipe("x:planks_loop", Station.CRAFTING,
                new RecipeShape.Shapeless(List.of(Ingredient.of("oak_planks"))), new ItemAmount("oak_planks", 1), Recipe.Source.GAME);
        assertFalse(resolver.canCollectAll(self));
    }

    @Test
    void mainRecipeWinsWhenInventoryIsEmpty() {
        RecipeRegistry registry = new RecipeRegistry();
        Recipe bamboo = new Recipe("minecraft:stick_from_bamboo_item", Station.CRAFTING,
                RecipeShape.Shaped.of(List.of("#", "#"), Map.of('#', Ingredient.of("bamboo"))), new ItemAmount("stick", 1), Recipe.Source.GAME);
        Recipe planks = new Recipe("minecraft:stick", Station.CRAFTING,
                RecipeShape.Shaped.of(List.of("#", "#"), Map.of('#', Ingredient.anyOf("#planks", ALL_PLANKS))), new ItemAmount("stick", 4), Recipe.Source.GAME);
        registry.addAll(List.of(bamboo, planks));

        assertEquals("minecraft:stick", registry.choose("stick", Map.of()).orElseThrow().id());
        // With bamboo in the inventory the bamboo recipe is possible right now
        assertEquals("minecraft:stick_from_bamboo_item", registry.choose("stick", Map.of("minecraft:bamboo", 2)).orElseThrow().id());
        // Filter: only recipes we can collect for
        assertEquals("minecraft:stick", registry.choose("stick", Map.of("minecraft:bamboo", 2),
                r -> r.id().equals("minecraft:stick")).orElseThrow().id());
        assertTrue(registry.choose("stick", Map.of(), r -> false).isEmpty());
    }

    @Test
    void itemInInventoryWinsOverCollectingAnother() {
        // Suspicious stew: #small_flowers has no matching catalogue entry. Without inventory the first
        // obtainable flower would be chosen (allium) and the bot walks off to find one.
        IngredientResolver resolver = new IngredientResolver(Map.of("flower", Set.of("minecraft:allium", "minecraft:sunflower")),
                Set.of("minecraft:allium", "minecraft:dandelion"));
        Ingredient smallFlowers = Ingredient.anyOf("#small_flowers", List.of("allium", "dandelion", "poppy"));
        assertEquals(Optional.of(IngredientResolver.Resolution.single("minecraft:allium")), resolver.resolve(smallFlowers));
        assertEquals(Optional.of(IngredientResolver.Resolution.single("minecraft:dandelion")),
                resolver.resolve(smallFlowers, Map.of("minecraft:dandelion", 1)));
        // An item we have but cannot collect is fine too
        assertEquals(Optional.of(IngredientResolver.Resolution.single("minecraft:poppy")),
                resolver.resolve(Ingredient.of("poppy"), Map.of("minecraft:poppy", 2)));
        // An exact catalogue match still wins (collects any of them)
        assertEquals(Optional.of(IngredientResolver.Resolution.catalogue("planks")),
                resolver().resolve(Ingredient.anyOf("#planks", ALL_PLANKS), Map.of("minecraft:birch_planks", 5)));
    }
}
