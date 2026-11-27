package adris.altoclef.recipes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads recipes in the vanilla data pack JSON format, old and new spellings:
 * <pre>
 * {"type": "minecraft:crafting_shaped", "pattern": ["##", "##"], "key": {"#": {"item": "minecraft:oak_planks"}},
 *  "result": {"item": "minecraft:crafting_table"}}          ≤1.20.4 ("item"), ≥1.20.5 "id", ≥1.21.2 plain strings
 * {"type": "minecraft:smelting", "ingredient": {"tag": "minecraft:logs"}, "result": "minecraft:charcoal", "cookingtime": 200}
 * </pre>
 * Ingredients may be {@code "minecraft:x"}, {@code "#minecraft:tag"}, {@code {"item": ...}}, {@code {"tag": ...}}
 * or a list of these (alternatives). Other recipe types (special, smithing, stonecutting) are skipped.
 * Does not depend on Minecraft.
 */
public final class VanillaRecipeParser {

    private static final Map<String, Station> COOKING = Map.of(
            "minecraft:smelting", Station.FURNACE,
            "minecraft:blasting", Station.BLAST_FURNACE,
            "minecraft:smoking", Station.SMOKER,
            "minecraft:campfire_cooking", Station.CAMPFIRE);

    private static final Map<Station, Integer> DEFAULT_COOK_TICKS = Map.of(
            Station.FURNACE, 200, Station.BLAST_FURNACE, 100, Station.SMOKER, 100, Station.CAMPFIRE, 600);

    private final TagResolver tags;
    private final ObjectMapper json = new ObjectMapper();

    public VanillaRecipeParser(TagResolver tags) {
        this.tags = tags;
    }

    /**
     * @return the recipe, or empty for recipe types the model does not cover
     * @throws IllegalArgumentException for broken recipes (unknown tag, missing fields, ...)
     */
    public Optional<Recipe> parse(String id, JsonNode root, Recipe.Source source) {
        String type = ItemIds.normalize(text(root, "type"));
        RecipeShape shape;
        Station station;
        switch (type) {
            case "minecraft:crafting_shaped" -> {
                station = Station.CRAFTING;
                List<String> pattern = new ArrayList<>();
                JsonNode patternNode = require(root, "pattern");
                patternNode.forEach(row -> pattern.add(row.asText()));
                Map<Character, Ingredient> key = new HashMap<>();
                require(root, "key").fields().forEachRemaining(e -> {
                    if (e.getKey().length() != 1) throw new IllegalArgumentException("Key must be one character: \"" + e.getKey() + "\"");
                    key.put(e.getKey().charAt(0), ingredient(e.getValue()));
                });
                shape = RecipeShape.Shaped.of(trim(pattern), key);
            }
            case "minecraft:crafting_shapeless" -> {
                station = Station.CRAFTING;
                List<Ingredient> ingredients = new ArrayList<>();
                require(root, "ingredients").forEach(node -> ingredients.add(ingredient(node)));
                shape = new RecipeShape.Shapeless(ingredients);
            }
            default -> {
                station = COOKING.get(type);
                if (station == null) return Optional.empty();
                int ticks = root.path("cookingtime").asInt(DEFAULT_COOK_TICKS.get(station));
                shape = new RecipeShape.Cooking(ingredient(require(root, "ingredient")), ticks);
            }
        }
        return Optional.of(new Recipe(id, station, shape, result(require(root, "result")), source));
    }

    public Optional<Recipe> parse(String id, String jsonText, Recipe.Source source) {
        try {
            return parse(id, json.readTree(jsonText), source);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage());
        }
    }

    /**
     * Parses many files at once. Broken files are skipped and reported in {@code warnings}.
     *
     * @param files recipe id → JSON text
     */
    public List<Recipe> parseAll(Map<String, String> files, Recipe.Source source, List<String> warnings) {
        List<Recipe> result = new ArrayList<>();
        for (Map.Entry<String, String> file : files.entrySet()) {
            try {
                parse(file.getKey(), file.getValue(), source).ifPresent(result::add);
            } catch (IllegalArgumentException e) {
                warnings.add("Recipe " + file.getKey() + ": " + e.getMessage());
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ parts

    Ingredient ingredient(JsonNode node) {
        if (node.isArray()) {
            Set<String> items = new LinkedHashSet<>();
            List<String> labels = new ArrayList<>();
            for (JsonNode alternative : node) {
                Ingredient part = ingredient(alternative);
                items.addAll(part.items());
                labels.add(part.label());
            }
            if (items.isEmpty()) throw new IllegalArgumentException("Empty ingredient list");
            return Ingredient.anyOf(String.join("|", labels), items);
        }
        if (node.isTextual()) {
            String value = node.asText();
            return value.startsWith("#") ? tag(value.substring(1)) : Ingredient.of(value);
        }
        if (node.has("item")) return Ingredient.of(node.get("item").asText());
        if (node.has("tag")) return tag(node.get("tag").asText());
        throw new IllegalArgumentException("Unknown ingredient: " + node);
    }

    private Ingredient tag(String tag) {
        String id = ItemIds.normalize(tag);
        Set<String> items = tags.resolve(id);
        if (items.isEmpty()) throw new IllegalArgumentException("Unknown tag #" + id);
        return Ingredient.anyOf("#" + id, items);
    }

    private static ItemAmount result(JsonNode node) {
        if (node.isTextual()) return new ItemAmount(node.asText(), 1);
        String item = node.has("id") ? node.get("id").asText() : text(node, "item");
        return new ItemAmount(item, node.path("count").asInt(1));
    }

    /**
     * Vanilla allows patterns with empty outer rows/columns; they don't matter for placing.
     */
    private static List<String> trim(List<String> pattern) {
        int width = pattern.stream().mapToInt(String::length).max().orElse(0);
        List<String> rows = new ArrayList<>();
        for (String row : pattern) rows.add(String.format("%-" + Math.max(width, 1) + "s", row));
        while (!rows.isEmpty() && rows.get(0).isBlank()) rows.remove(0);
        while (!rows.isEmpty() && rows.get(rows.size() - 1).isBlank()) rows.remove(rows.size() - 1);
        if (rows.isEmpty()) throw new IllegalArgumentException("Pattern is empty");
        int left = width, right = 0;
        for (String row : rows) {
            for (int x = 0; x < row.length(); x++) {
                if (row.charAt(x) != ' ') {
                    left = Math.min(left, x);
                    right = Math.max(right, x + 1);
                }
            }
        }
        List<String> result = new ArrayList<>();
        for (String row : rows) result.add(row.substring(left, right));
        return result;
    }

    private static JsonNode require(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) throw new IllegalArgumentException("Missing \"" + field + "\"");
        return value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = require(node, field);
        if (!value.isTextual()) throw new IllegalArgumentException("\"" + field + "\" must be text");
        return value.asText();
    }
}
