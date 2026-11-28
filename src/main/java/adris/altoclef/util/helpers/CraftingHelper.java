package adris.altoclef.util.helpers;

import adris.altoclef.AltoClef;
import adris.altoclef.recipes.CraftableFromInventory;
import adris.altoclef.util.MinecraftItemIds;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class CraftingHelper {

    private CraftingHelper() {
    }

    /**
     * Checks if the bot can make the item just from its inventory by crafting, possibly in several steps
     * (e.g. log → planks → sticks). Uses the recipe registry ({@link CraftableFromInventory}).
     */
    public static boolean canCraftItemNow(AltoClef mod, Item item) {
        if (mod.getRecipeRegistryTracker() == null) return false;
        Map<String, Integer> inventory = new HashMap<>();
        for (ItemStack stack : mod.getItemStorage().getItemStacksPlayerInventory(true)) {
            if (!stack.isEmpty()) inventory.merge(MinecraftItemIds.id(stack.getItem()), stack.getCount(), Integer::sum);
        }
        return CraftableFromInventory.canCraft(mod.getRecipeRegistryTracker().getRegistry(), MinecraftItemIds.id(item), inventory);
    }
}
