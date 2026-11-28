package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.multiversion.versionedfields.Items;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.RegistryCrafting;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.helpers.ItemHelper;
import net.minecraft.block.Block;
import net.minecraft.item.Item;

public class CollectBedTask extends CraftWithMatchingWoolTask {

    public static final Block[] BEDS = ItemHelper.itemsToBlocks(ItemHelper.BED);

    /**
     * Beds closer than this are mined instead of crafted.
     */
    private static final double NEARBY_BED_RANGE = 32;

    private final ItemTarget visualBedTarget;

    public CollectBedTask(Item[] beds, ItemTarget wool, int count) {
        // Top 3 are wool, must be the same.
        // Recipe and "same wool" slots from the recipe registry.
        super(new ItemTarget(beds, count), colorfulItems -> colorfulItems.wool, colorfulItems -> colorfulItems.bed,
                RegistryCrafting.matchingRecipe(Items.WHITE_BED, Items.WHITE_WOOL, wool)
                        .orElse(null));
        visualBedTarget = new ItemTarget(beds, count);
    }

    public CollectBedTask(Item bed, String woolCatalogueName, int count) {
        this(new Item[]{bed}, new ItemTarget(woolCatalogueName, 1), count);
    }

    public CollectBedTask(int count) {
        this(ItemHelper.BED, TaskCatalogue.getItemTarget("wool", 1), count);
    }


    @Override
    protected boolean shouldAvoidPickingUp(AltoClef mod) {
        return false;
    }


    @Override
    protected Task onResourceTick(AltoClef mod) {
        // Break beds from the world if that is faster: only nearby ones, or any if we can't craft one ourselves
        // (before: any bed the scanner had ever seen, so the bot walked ~150 blocks with 3 wool in its pocket).
        boolean bedNearby = mod.getBlockScanner().getNearestWithinRange(mod.getPlayer().getPos(), NEARBY_BED_RANGE, BEDS).isPresent();
        if (bedNearby || (!hasWoolForABed(mod) && mod.getBlockScanner().anyFound(BEDS))) {
            // Failure + blacklisting is encapsulated within THIS task
            return new MineAndCollectTask(new ItemTarget(ItemHelper.BED, 1), BEDS, MiningRequirement.HAND);
        }
        return super.onResourceTick(mod);
    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        if (other instanceof CollectBedTask task) {
            return task.visualBedTarget.equals(visualBedTarget);
        }
        return false;
    }

    @Override
    protected String toDebugStringName() {
        return "Crafting bed: " + visualBedTarget;
    }

    /**
     * True if the inventory has 3 wool of one colour (a bed needs 3 of the same colour).
     */
    private static boolean hasWoolForABed(AltoClef mod) {
        for (Item wool : ItemHelper.WOOL) {
            if (mod.getItemStorage().getItemCount(wool) >= 3) return true;
        }
        return false;
    }
}
