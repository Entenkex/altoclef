package adris.altoclef.recipes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Loads the local fallback recipes shipped with the mod:
 * <pre>
 * assets/altoclef/recipes/fallback_recipes.json   {"recipes": {"altoclef:stick": { vanilla recipe JSON }, ...}}
 * assets/altoclef/recipes/fallback_tags.json      {"tags": {"minecraft:planks": ["minecraft:oak_planks", ...]}}
 * </pre>
 * Generated from vanilla data by {@code scripts/gen_fallback_recipes.py}. Does not depend on Minecraft.
 */
public final class FallbackRecipes {

    public static final String RECIPES_RESOURCE = "/assets/altoclef/recipes/fallback_recipes.json";
    public static final String TAGS_RESOURCE = "/assets/altoclef/recipes/fallback_tags.json";

    private FallbackRecipes() {
    }

    /**
     * Reads the files from the class path (the mod jar).
     */
    public static List<Recipe> loadFromClasspath(List<String> warnings) {
        try (InputStream recipes = FallbackRecipes.class.getResourceAsStream(RECIPES_RESOURCE);
             InputStream tags = FallbackRecipes.class.getResourceAsStream(TAGS_RESOURCE)) {
            if (recipes == null || tags == null) {
                warnings.add("Fallback recipes not found in the mod jar");
                return List.of();
            }
            return load(recipes, tags, warnings);
        } catch (IOException e) {
            warnings.add("Could not read fallback recipes: " + e.getMessage());
            return List.of();
        }
    }

    public static List<Recipe> load(InputStream recipesJson, InputStream tagsJson, List<String> warnings) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        TagResolver tags = TagResolver.fromJson(mapper.readTree(tagsJson).path("tags"));
        JsonNode recipes = mapper.readTree(recipesJson).path("recipes");
        VanillaRecipeParser parser = new VanillaRecipeParser(tags);

        List<Recipe> result = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = recipes.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            try {
                parser.parse(entry.getKey(), entry.getValue(), Recipe.Source.FALLBACK).ifPresent(result::add);
            } catch (IllegalArgumentException e) {
                warnings.add("Fallback recipe " + entry.getKey() + ": " + e.getMessage());
            }
        }
        return result;
    }
}
