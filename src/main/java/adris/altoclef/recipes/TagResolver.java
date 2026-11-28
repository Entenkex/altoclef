package adris.altoclef.recipes;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns an item tag ({@code minecraft:planks}) into its items. In game this comes from the game's tags;
 * {@link #fromJson} is the local fallback.
 */
@FunctionalInterface
public interface TagResolver {

    /**
     * @param tag tag id without {@code #}, e.g. {@code minecraft:planks}
     * @return the item ids, empty if the tag is unknown
     */
    Set<String> resolve(String tag);

    TagResolver NONE = tag -> Set.of();

    /**
     * Uses {@code first}, and {@code second} for tags {@code first} does not know.
     */
    default TagResolver orElse(TagResolver second) {
        return tag -> {
            Set<String> items = resolve(tag);
            return items.isEmpty() ? second.resolve(tag) : items;
        };
    }

    /**
     * Local tag list: {@code {"minecraft:planks": ["minecraft:oak_planks", ...], "minecraft:logs": ["#minecraft:oak_logs", ...]}}.
     * Entries starting with {@code #} include another tag of the same file.
     */
    static TagResolver fromJson(JsonNode root) {
        Map<String, List<String>> raw = new HashMap<>();
        root.fields().forEachRemaining(e -> {
            List<String> values = new ArrayList<>();
            e.getValue().forEach(v -> values.add(v.asText()));
            raw.put(ItemIds.normalize(e.getKey()), values);
        });
        Map<String, Set<String>> cache = new HashMap<>();
        return new TagResolver() {
            @Override
            public Set<String> resolve(String tag) {
                return expand(ItemIds.normalize(tag), new LinkedHashSet<>());
            }

            private Set<String> expand(String tag, Set<String> visiting) {
                Set<String> cached = cache.get(tag);
                if (cached != null) return cached;
                List<String> values = raw.get(tag);
                if (values == null || !visiting.add(tag)) return Set.of();
                Set<String> items = new LinkedHashSet<>();
                for (String value : values) {
                    if (value.startsWith("#")) items.addAll(expand(ItemIds.normalize(value.substring(1)), visiting));
                    else items.add(ItemIds.normalize(value));
                }
                visiting.remove(tag);
                Set<String> result = Collections.unmodifiableSet(items);
                cache.put(tag, result);
                return result;
            }
        };
    }
}
