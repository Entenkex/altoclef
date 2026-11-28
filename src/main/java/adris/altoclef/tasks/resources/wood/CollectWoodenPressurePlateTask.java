package adris.altoclef.tasks.resources.wood;

import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.resources.CraftWithMatchingPlanksTask;
import adris.altoclef.util.CraftingRecipe;
import adris.altoclef.multiversion.versionedfields.Items;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.RegistryCrafting;
import adris.altoclef.util.helpers.ItemHelper;
import net.minecraft.item.Item;

public class CollectWoodenPressurePlateTask extends CraftWithMatchingPlanksTask {

    public CollectWoodenPressurePlateTask(Item[] targets, ItemTarget planks, int count) {
        super(targets, woodItems -> woodItems.pressurePlate,
                // Recipe and "same material" slots from the recipe registry; the hand-written grid is only a last resort.
                RegistryCrafting.matchingRecipe(Items.OAK_PRESSURE_PLATE, Items.OAK_PLANKS, planks)
                        .orElseGet(() -> new RegistryCrafting.MatchingRecipe(createRecipe(planks), new boolean[]{true, true, false, false})), count);
    }

    public CollectWoodenPressurePlateTask(Item target, String plankCatalogueName, int count) {
        this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
    }

    public CollectWoodenPressurePlateTask(int count) {
        this(ItemHelper.WOOD_PRESSURE_PLATE, TaskCatalogue.getItemTarget("planks", 1), count);
    }


    private static CraftingRecipe createRecipe(ItemTarget planks) {
        ItemTarget p = planks;
        return CraftingRecipe.newShapedRecipe(new ItemTarget[]{p, p, null, null}, 1);
    }
}
