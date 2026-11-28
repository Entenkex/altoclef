package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.util.RegistryCrafting;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasks.container.CraftInTableTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.CraftTarget;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;

// TODO: This can technically be removed, as it's a mine task followed by a collect task.
public class CollectHayBlockTask extends ResourceTask {

    private final int count;

    public CollectHayBlockTask(int count) {
        super(Items.HAY_BLOCK, count);
        this.count = count;
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

        if (mod.getBlockScanner().anyFound(Blocks.HAY_BLOCK)) {
            return new MineAndCollectTask(Items.HAY_BLOCK, count, new Block[]{Blocks.HAY_BLOCK}, MiningRequirement.HAND);
        }

        return RegistryCrafting.craftTaskUsing(Items.HAY_BLOCK, count, Items.WHEAT);
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {

    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectHayBlockTask;
    }

    @Override
    protected String toDebugStringName() {
        return "Collecting " + count + " hay blocks.";
    }
}
