package adris.altoclef.tasks.resources.wood;

import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.resources.CraftWithMatchingPlanksTask;
import adris.altoclef.multiversion.versionedfields.Items;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.RegistryCrafting;
import adris.altoclef.util.helpers.ItemHelper;
import net.minecraft.item.Item;

public class CollectWoodenDoorTask extends CraftWithMatchingPlanksTask {

    public CollectWoodenDoorTask(Item[] targets, ItemTarget planks, int count) {
        super(targets, woodItems -> woodItems.door,
                // Recipe and "same material" slots from the recipe registry.
                RegistryCrafting.matchingRecipe(Items.OAK_DOOR, Items.OAK_PLANKS, planks)
                        .orElse(null), count);
    }

    public CollectWoodenDoorTask(Item target, String plankCatalogueName, int count) {
        this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
    }

    public CollectWoodenDoorTask(int count) {
        this(ItemHelper.WOOD_DOOR, TaskCatalogue.getItemTarget("planks", 1), count);
    }

}
