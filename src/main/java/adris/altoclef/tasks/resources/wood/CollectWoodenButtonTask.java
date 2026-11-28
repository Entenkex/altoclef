package adris.altoclef.tasks.resources.wood;

import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.resources.CraftWithMatchingPlanksTask;
import adris.altoclef.multiversion.versionedfields.Items;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.RegistryCrafting;
import adris.altoclef.util.helpers.ItemHelper;
import net.minecraft.item.Item;

public class CollectWoodenButtonTask extends CraftWithMatchingPlanksTask {

    public CollectWoodenButtonTask(Item[] targets, ItemTarget planks, int count) {
        super(targets, woodItems -> woodItems.button,
                // Recipe and "same material" slots from the recipe registry.
                RegistryCrafting.matchingRecipe(Items.OAK_BUTTON, Items.OAK_PLANKS, planks)
                        .orElse(null), count);
    }

    public CollectWoodenButtonTask(Item target, String plankCatalogueName, int count) {
        this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
    }

    public CollectWoodenButtonTask(int count) {
        this(ItemHelper.WOOD_BUTTON, TaskCatalogue.getItemTarget("planks", 1), count);
    }


}
