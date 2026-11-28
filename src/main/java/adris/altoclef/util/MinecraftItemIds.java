package adris.altoclef.util;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Converts between the item id strings of the recipe model ({@code minecraft:oak_planks}) and Minecraft items,
 * at the boundary between {@code adris.altoclef.recipes} (Minecraft-free) and the tasks.
 */
public final class MinecraftItemIds {

    private MinecraftItemIds() {
    }

    /**
     * @return empty if the item does not exist in this Minecraft version
     */
    public static Optional<Item> item(String id) {
        Identifier identifier = Identifier.of(id);
        if (!Registries.ITEM.containsId(identifier)) return Optional.empty();
        return Optional.of(Registries.ITEM.get(identifier));
    }

    public static String id(Item item) {
        return Registries.ITEM.getId(item).toString();
    }

    /**
     * The items that exist in this version, in the order of the ids.
     */
    public static Item[] items(Set<String> ids) {
        List<Item> items = new ArrayList<>();
        for (String id : ids) item(id).ifPresent(items::add);
        return items.toArray(new Item[0]);
    }
}
