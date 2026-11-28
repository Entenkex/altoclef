package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.util.RegistryCrafting;
import adris.altoclef.tasks.CraftInInventoryTask;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.CraftTarget;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;

public class CollectNetherBricksTask extends ResourceTask {

    private final int _count;

    public CollectNetherBricksTask(int count) {
        super(Items.NETHER_BRICKS, count);
        _count = count;
    }

    @Override
    protected boolean shouldAvoidPickingUp(AltoClef mod) {
        return false;
    }

    @Override
    protected void onResourceStart(AltoClef mod) {

    }

    @Override
    protected Task onResourceTick(AltoClef mod) {

        /*
         * If we find nether bricks, mine them.
         *
         * Otherwise craft them from the "nether_brick" item.
         */

        if (mod.getBlockScanner().anyFound(Blocks.NETHER_BRICKS)) {
            return new MineAndCollectTask(Items.NETHER_BRICKS, _count, new Block[]{Blocks.NETHER_BRICKS}, MiningRequirement.WOOD);
        }

        // Output is the block (before: the nether_brick item, which can't be crafted from itself).
        return RegistryCrafting.craftTaskUsing(Items.NETHER_BRICKS, _count, Items.NETHER_BRICK);
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectNetherBricksTask;
    }

    @Override
    protected String toDebugStringName() {
        return "Collecting " + _count + " nether bricks.";
    }
}
