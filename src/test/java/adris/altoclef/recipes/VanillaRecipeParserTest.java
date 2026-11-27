package adris.altoclef.recipes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VanillaRecipeParserTest {

    private static final TagResolver TAGS = tag -> switch (tag) {
        case "minecraft:planks" -> Set.of("minecraft:oak_planks", "minecraft:birch_planks");
        case "minecraft:logs_that_burn" -> Set.of("minecraft:oak_log");
        default -> Set.of();
    };

    private final VanillaRecipeParser parser = new VanillaRecipeParser(TAGS);

    private Recipe parse(String json) {
        return parser.parse("test:recipe", json, Recipe.Source.FALLBACK).orElseThrow();
    }

    @Test
    void shapedOldFormat() {
        // ≤ 1.20.4: {"item": ...} / {"tag": ...}, result {"item", "count"}
        Recipe recipe = parse("""
                {"type": "minecraft:crafting_shaped",
                 "pattern": ["#", "#"],
                 "key": {"#": {"tag": "minecraft:planks"}},
                 "result": {"item": "minecraft:stick", "count": 4}}
                """);
        assertEquals(Station.CRAFTING, recipe.station());
        assertEquals(new ItemAmount("stick", 4), recipe.output());
        RecipeShape.Shaped shape = (RecipeShape.Shaped) recipe.shape();
        assertEquals(1, shape.width());
        assertEquals(2, shape.height());
        assertEquals("#minecraft:planks", shape.cell(0, 0).label());
        assertTrue(shape.cell(0, 1).matches("birch_planks"));
        assertEquals(Recipe.Source.FALLBACK, recipe.source());
    }

    @Test
    void shapedNewFormats() {
        // 1.20.5+: result "id"; 1.21.2+: plain strings and "#tag"
        Recipe v1205 = parse("""
                {"type": "minecraft:crafting_shaped", "pattern": ["##", "##"],
                 "key": {"#": {"item": "minecraft:oak_planks"}}, "result": {"id": "minecraft:crafting_table"}}
                """);
        assertEquals(new ItemAmount("crafting_table", 1), v1205.output());

        Recipe v1212 = parse("""
                {"type": "minecraft:crafting_shaped", "pattern": ["XXX", " # ", " # "],
                 "key": {"#": "minecraft:stick", "X": "#minecraft:planks"},
                 "result": {"id": "minecraft:wooden_pickaxe", "count": 1}}
                """);
        RecipeShape.Shaped shape = (RecipeShape.Shaped) v1212.shape();
        assertEquals(3, shape.width());
        assertTrue(shape.cell(1, 2).matches("stick"));
        assertTrue(shape.cell(0, 1).isEmpty());
    }

    @Test
    void emptyOuterRowsAndColumnsAreTrimmed() {
        Recipe recipe = parse("""
                {"type": "crafting_shaped", "pattern": ["   ", " # ", " # "],
                 "key": {"#": "minecraft:stick"}, "result": "minecraft:stick"}
                """);
        RecipeShape.Shaped shape = (RecipeShape.Shaped) recipe.shape();
        assertEquals(1, shape.width());
        assertEquals(2, shape.height());
    }

    @Test
    void shapelessWithAlternativesList() {
        Recipe recipe = parse("""
                {"type": "minecraft:crafting_shapeless",
                 "ingredients": [{"item": "minecraft:bone"}, [{"item": "minecraft:coal"}, {"item": "minecraft:charcoal"}]],
                 "result": {"item": "minecraft:bone_meal", "count": 3}}
                """);
        List<Ingredient> ingredients = recipe.shape().ingredients();
        assertEquals(2, ingredients.size());
        assertEquals(Set.of("minecraft:charcoal", "minecraft:coal"), ingredients.get(1).items());
        assertTrue(recipe.fitsInventory());
    }

    @Test
    void cookingTypesAndDefaults() {
        Recipe smelting = parse("""
                {"type": "minecraft:smelting", "ingredient": {"tag": "minecraft:logs_that_burn"},
                 "result": "minecraft:charcoal", "experience": 0.15, "cookingtime": 200}
                """);
        assertEquals(Station.FURNACE, smelting.station());
        assertEquals(200, ((RecipeShape.Cooking) smelting.shape()).cookTicks());
        assertEquals(new ItemAmount("charcoal", 1), smelting.output());

        Recipe blasting = parse("""
                {"type": "minecraft:blasting", "ingredient": "minecraft:raw_iron", "result": {"id": "minecraft:iron_ingot"}}
                """);
        assertEquals(Station.BLAST_FURNACE, blasting.station());
        assertEquals(100, ((RecipeShape.Cooking) blasting.shape()).cookTicks());

        assertEquals(Station.SMOKER, parse("""
                {"type": "minecraft:smoking", "ingredient": "minecraft:beef", "result": "minecraft:cooked_beef"}
                """).station());
        assertEquals(600, ((RecipeShape.Cooking) parse("""
                {"type": "minecraft:campfire_cooking", "ingredient": "minecraft:beef", "result": "minecraft:cooked_beef"}
                """).shape()).cookTicks());
    }

    @Test
    void otherTypesAreSkipped() {
        assertTrue(parser.parse("x", "{\"type\": \"minecraft:crafting_special_firework_rocket\"}", Recipe.Source.GAME).isEmpty());
        assertTrue(parser.parse("x", "{\"type\": \"minecraft:smithing_transform\"}", Recipe.Source.GAME).isEmpty());
    }

    @Test
    void brokenRecipesAreReportedAndSkipped() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("altoclef:ok", "{\"type\": \"minecraft:smelting\", \"ingredient\": \"minecraft:sand\", \"result\": \"minecraft:glass\"}");
        files.put("altoclef:unknown_tag", "{\"type\": \"minecraft:smelting\", \"ingredient\": \"#minecraft:nope\", \"result\": \"minecraft:glass\"}");
        files.put("altoclef:no_result", "{\"type\": \"minecraft:smelting\", \"ingredient\": \"minecraft:sand\"}");
        files.put("altoclef:bad_key", "{\"type\": \"minecraft:crafting_shaped\", \"pattern\": [\"#\"], \"key\": {}, \"result\": \"minecraft:stick\"}");
        files.put("altoclef:not_json", "{");

        List<String> warnings = new ArrayList<>();
        List<Recipe> recipes = parser.parseAll(files, Recipe.Source.FALLBACK, warnings);

        assertEquals(1, recipes.size());
        assertEquals("altoclef:ok", recipes.get(0).id());
        assertEquals(4, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("Unknown tag #minecraft:nope"), warnings.get(0));
        assertTrue(warnings.get(1).contains("Missing \"result\""), warnings.get(1));
    }

    @Test
    void localTagFileWithNestedTags() throws Exception {
        TagResolver local = TagResolver.fromJson(new ObjectMapper().readTree("""
                {"minecraft:oak_logs": ["minecraft:oak_log", "minecraft:oak_wood"],
                 "minecraft:logs": ["#minecraft:oak_logs", "minecraft:birch_log"],
                 "minecraft:loop": ["#minecraft:loop", "minecraft:stick"]}
                """));
        assertEquals(Set.of("minecraft:oak_log", "minecraft:oak_wood", "minecraft:birch_log"), local.resolve("minecraft:logs"));
        assertEquals(Set.of("minecraft:stick"), local.resolve("loop"));
        assertTrue(local.resolve("minecraft:unknown").isEmpty());

        TagResolver combined = TAGS.orElse(local);
        assertEquals(TAGS.resolve("minecraft:planks"), combined.resolve("minecraft:planks"));
        assertEquals(local.resolve("minecraft:logs"), combined.resolve("minecraft:logs"));
    }
}
