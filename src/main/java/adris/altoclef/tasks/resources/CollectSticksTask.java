package adris.altoclef.tasks.resources;

import adris.altoclef.AltoClef;
import adris.altoclef.util.RegistryCrafting;
import adris.altoclef.tasks.CraftInInventoryTask;
import adris.altoclef.tasks.ResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.trackers.storage.ItemStorageTracker;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.CraftTarget;
import adris.altoclef.util.helpers.ItemHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

public class CollectSticksTask extends ResourceTask {

    private final int _targetCount;

    public CollectSticksTask(int targetCount) {
        super(Items.STICK, targetCount);
        _targetCount = targetCount;
    }

    @Override
    protected boolean shouldAvoidPickingUp(AltoClef mod) {
        return false;
    }

    @Override
    protected void onResourceStart(AltoClef mod) {
        mod.getBehaviour().push();
    }

    @Override
    protected double getPickupRange(AltoClef mod) {
        ItemStorageTracker storage = mod.getItemStorage();
        if (storage.getItemCount(ItemHelper.PLANKS)*4+storage.getItemCount(ItemHelper.LOG)*4*4 > _targetCount) return 10;

        return 35;
    }

    @Override
    protected Task onResourceTick(AltoClef mod) {
        // try to craft sticks from bamboo
        if (mod.getItemStorage().getItemCount(Items.BAMBOO) >= 2) {
            return RegistryCrafting.craftTaskUsing(Items.STICK, Math.min(mod.getItemStorage().getItemCount(Items.BAMBOO) / 2, _targetCount), Items.BAMBOO);
        }

        Optional<BlockPos> nearestBush = mod.getBlockScanner().getNearestBlock(Blocks.DEAD_BUSH);
        // If there's a dead bush within range, go get it
        if (nearestBush.isPresent() && nearestBush.get().isWithinDistance(mod.getPlayer().getPos(), 20)) {
            ResourceTask task = new MineAndCollectTask(Items.DEAD_BUSH, 1, new Block[]{Blocks.DEAD_BUSH}, MiningRequirement.HAND);
            task.setAllowContainers(false);

            return task;
        }
        // else craft from wood
        return RegistryCrafting.craftTaskUsing(Items.STICK, _targetCount, Items.OAK_PLANKS);
    }

    @Override
    protected void onResourceStop(AltoClef mod, Task interruptTask) {
        mod.getBehaviour().pop();
    }

    @Override
    protected boolean isEqualResource(ResourceTask other) {
        return other instanceof CollectSticksTask;
    }

    @Override
    protected String toDebugStringName() {
        return "Crafting " + _targetCount + " sticks";
    }
}
