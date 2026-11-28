package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.CraftTarget;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.RegistryCrafting;
import net.minecraft.item.Item;

/**
 * "Any bed / any stairs": collect any of the variants' material (any wool, any planks), then craft the variant
 * we have most material for. The recipe of one representative variant (from the registry) tells how much of the
 * "same" material one craft needs; the actual craft uses the registry recipe of the chosen variant.
 */
public abstract class CraftWithMatchingMaterialsTask extends ResourceTask {

    private final ItemTarget target;
    private final CraftTarget recipe;

    private final ItemTarget sameResourceTarget;
    private final int sameResourceRequiredCount;
    private final int sameResourcePerRecipe;

    /**
     * @param matching representative recipe + "same material" mask; null if the registry has none (the task then
     *                 does nothing and warns)
     */
    public CraftWithMatchingMaterialsTask(ItemTarget target, RegistryCrafting.MatchingRecipe matching) {
        super(target);
        this.target = target;
        this.recipe = matching == null ? null : matching.recipe();
        boolean[] sameMask = matching == null ? new boolean[0] : matching.sameMask();
        int sameResourceRequiredCount = 0;
        ItemTarget sameResourceTarget = null;
        if (recipe == null) {
            sameResourceTarget = null;
        } else if (recipe.getSlotCount() != sameMask.length) {
            Debug.logError("Invalid CraftWithMatchingMaterialsTask constructor parameters: Recipe size must equal \"sameMask\" size.");
        }
        for (int i = 0; recipe != null && i < recipe.getSlotCount(); ++i) {
            if (sameMask[i]) {
                sameResourceRequiredCount++;
                sameResourceTarget = recipe.getSlot(i);
            }
        }
        this.sameResourceTarget = sameResourceTarget;

        // cant this be just replaced with `Math.ceil((double) target.getTargetCount() / recipe.outputCount())` ?
        int outputPerCraft = recipe == null ? 1 : recipe.outputCount();
        int craftsNeeded = (int) (1 + Math.floor((double) target.getTargetCount() / outputPerCraft - 0.001));
        sameResourcePerRecipe = sameResourceRequiredCount;
        this.sameResourceRequiredCount = sameResourceRequiredCount * craftsNeeded;
    }

    @Override
    protected void onResourceStart(AltoClef mod) {

    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        if (recipe == null || sameResourceTarget == null) {
            setDebugState("No recipe for " + target + " in this version");
            return null;
        }

        /*
         * 0) Figure out the "same resource" item target
         * 1) Get the "same resource" item matches array
         * 2) Figure out how many of the "same resource" we need
         * 3) Get the most frequent occurrence of said material
         * 4) If the most frequent occurrence is NOT met, return TaskCatalogue.getItemTask("same resource" item target)
         * 5) If the most frequent occurrence IS met, run CraftInTable with a custom recipe that only has the frequent material.`
         */

        // For each "same" item: How many items can we craft with it?
        // For instance, if we have 7 red wool, we can craft 2 beds
        // sameFullCraftsPermitted[Items.RED_WOOL] = 2;
        int canCraftTotal = 0;
        int majorityCraftCount = 0;
        Item majorityCraftItem = null;
        for (Item sameCheck : sameResourceTarget.getMatches()) {
            int count = getExpectedTotalCountOfSameItem(mod, sameCheck);
            int canCraft = (count / sameResourcePerRecipe) * recipe.outputCount();
            canCraftTotal += canCraft;
            if (canCraft > majorityCraftCount) {
                majorityCraftCount = canCraft;
                majorityCraftItem = sameCheck;
            }
        }

        // If we already have some of our target, we need less "same" materials.
        int currentTargetCount = mod.getItemStorage().getItemCount(target);
        int currentTargetsRequired = target.getTargetCount() - currentTargetCount;

        if (canCraftTotal >= currentTargetsRequired) {
            // We have enough of the same resource!!!
            // Handle crafting normally.

            // We may need to convert our raw materials into our "matching" materials.
            int trueCanCraftTotal = 0;
            for (Item sameCheck : sameResourceTarget.getMatches()) {
                int trueCount = mod.getItemStorage().getItemCount(sameCheck);
                int trueCanCraft = (trueCount / sameResourcePerRecipe) * recipe.outputCount();
                trueCanCraftTotal += trueCanCraft;
            }
            if (trueCanCraftTotal < currentTargetsRequired) {
                return getSpecificSameResourceTask(mod, sameResourceTarget.getMatches());
            }

            int toCraftTotal = majorityCraftCount;
            toCraftTotal = Math.min(toCraftTotal, target.getTargetCount());
            Item output = getSpecificItemCorrespondingToMajorityResource(majorityCraftItem);

            toCraftTotal = Math.min(target.getTargetCount(),toCraftTotal+mod.getItemStorage().getItemCount(output));

            // The chosen variant's own recipe (e.g. birch stairs from birch planks), so the recipe book crafts it.
            return RegistryCrafting.craftTarget(output, toCraftTotal, RegistryCrafting.uses(majorityCraftItem))
                    .map(RegistryCrafting::craftTask).orElse(null);
        }
        // Collect SAME resources first!!!
        return getAllSameResourcesTask(mod);
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {

    }


    protected Task getAllSameResourcesTask(AltoClef mod) {
        ItemTarget infinityVersion = new ItemTarget(sameResourceTarget, 999999);
        return TaskCatalogue.getItemTask(infinityVersion);
    }

    // Virtual
    protected int getExpectedTotalCountOfSameItem(AltoClef mod, Item sameItem) {
        return mod.getItemStorage().getItemCount(sameItem);
    }

    // Virtual
    // this should be implemented, if 'getExpectedTotalCountOfSameItem' is overwritten
    protected Task getSpecificSameResourceTask(AltoClef mod, Item[] toGet) {
        Debug.logError("Uh oh!!! getSpecificSameResourceTask should be implemented!!!! Now we're stuck.");
        return null;
    }

    protected abstract Item getSpecificItemCorrespondingToMajorityResource(Item majority);
}
