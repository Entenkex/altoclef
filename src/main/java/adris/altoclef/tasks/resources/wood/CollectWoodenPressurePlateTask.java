package adris.altoclef.tasks.resources.wood;

import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.resources.CraftWithMatchingPlanksTask;
import adris.altoclef.multiversion.versionedfields.Items;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.RegistryCrafting;
import adris.altoclef.util.helpers.ItemHelper;
import net.minecraft.item.Item;

public class CollectWoodenPressurePlateTask extends CraftWithMatchingPlanksTask {

    public CollectWoodenPressurePlateTask(Item[] targets, ItemTarget planks, int count) {
        super(targets, woodItems -> woodItems.pressurePlate,
                // Recipe and "same material" slots from the recipe registry.
                RegistryCrafting.matchingRecipe(Items.OAK_PRESSURE_PLATE, Items.OAK_PLANKS, planks)
                        .orElse(null), count);
    }

    public CollectWoodenPressurePlateTask(Item target, String plankCatalogueName, int count) {
        this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
    }

    public CollectWoodenPressurePlateTask(int count) {
        this(ItemHelper.WOOD_PRESSURE_PLATE, TaskCatalogue.getItemTarget("planks", 1), count);
    }


}
