package adris.altoclef.tasks.resources.wood;

import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.resources.CraftWithMatchingPlanksTask;
import adris.altoclef.util.CraftingRecipe;
import adris.altoclef.multiversion.versionedfields.Items;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.RegistryCrafting;
import adris.altoclef.util.helpers.ItemHelper;
import net.minecraft.item.Item;

public class CollectWoodenDoorTask extends CraftWithMatchingPlanksTask {

    public CollectWoodenDoorTask(Item[] targets, ItemTarget planks, int count) {
        super(targets, woodItems -> woodItems.door,
                // Recipe and "same material" slots from the recipe registry; the hand-written grid is only a last resort.
                RegistryCrafting.matchingRecipe(Items.OAK_DOOR, Items.OAK_PLANKS, planks)
                        .orElseGet(() -> new RegistryCrafting.MatchingRecipe(createRecipe(planks), new boolean[]{true, true, false, true, true, false, true, true, false})), count);
    }

    public CollectWoodenDoorTask(Item target, String plankCatalogueName, int count) {
        this(new Item[]{target}, new ItemTarget(plankCatalogueName, 1), count);
    }

    public CollectWoodenDoorTask(int count) {
        this(ItemHelper.WOOD_DOOR, TaskCatalogue.getItemTarget("planks", 1), count);
    }

    private static CraftingRecipe createRecipe(ItemTarget planks) {
        ItemTarget p = planks;
        ItemTarget o = null;
        return CraftingRecipe.newShapedRecipe(new ItemTarget[]{p, p, o, p, p, o, p, p, o}, 3);
    }
}
