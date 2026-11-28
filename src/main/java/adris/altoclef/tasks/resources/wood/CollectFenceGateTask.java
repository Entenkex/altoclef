package adris.altoclef.tasks.resources.wood;

import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.resources.CraftWithMatchingPlanksTask;
import adris.altoclef.multiversion.versionedfields.Items;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.RegistryCrafting;
import adris.altoclef.util.helpers.ItemHelper;
import net.minecraft.item.Item;

public class CollectFenceGateTask extends CraftWithMatchingPlanksTask {

    public CollectFenceGateTask(Item[] targets, ItemTarget planks, int count) {
        super(targets, woodItems -> woodItems.fenceGate,
                // Recipe and "same material" slots from the recipe registry.
                RegistryCrafting.matchingRecipe(Items.OAK_FENCE_GATE, Items.OAK_PLANKS, planks)
                        .orElse(null), count);
    }

    public CollectFenceGateTask(Item target, String plankCatalogueName, int count) {
        this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
    }

    public CollectFenceGateTask(int count) {
        this(ItemHelper.WOOD_FENCE_GATE, TaskCatalogue.getItemTarget("planks", 1), count);
    }

}
