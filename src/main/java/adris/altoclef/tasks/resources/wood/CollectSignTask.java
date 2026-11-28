package adris.altoclef.tasks.resources.wood;

import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.resources.CraftWithMatchingPlanksTask;
import adris.altoclef.multiversion.versionedfields.Items;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.RegistryCrafting;
import adris.altoclef.util.helpers.ItemHelper;
import net.minecraft.item.Item;

public class CollectSignTask extends CraftWithMatchingPlanksTask {

    public CollectSignTask(Item[] targets, ItemTarget planks, int count) {
        // Top 6 are planks, must be the same.
        super(targets, woodItems -> woodItems.sign,
                // Recipe and "same material" slots from the recipe registry.
                RegistryCrafting.matchingRecipe(Items.OAK_SIGN, Items.OAK_PLANKS, planks)
                        .orElse(null), count);
    }

    public CollectSignTask(Item target, String plankCatalogueName, int count) {
        this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
    }

    public CollectSignTask(int count) {
        this(ItemHelper.WOOD_SIGN, TaskCatalogue.getItemTarget("planks", 1), count);
    }


}
