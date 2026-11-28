package adris.altoclef.tasks.container;

import adris.altoclef.AltoClef;
import adris.altoclef.tasks.slot.MoveItemToSlotFromInventoryTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.slots.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Uses the heat left in a furnace once the actual smelt target is done: further smeltable items from the inventory
 * (raw meat, raw iron, ...) go in as long as the burning fuel covers them, so the bot does not leave with raw food
 * that it later has to cook separately. No extra fuel is put in for them.
 * <p>
 * One instance per smelt task: {@link #tick} runs in the container sub task, {@link #wantsToContinue} keeps the task
 * from finishing while extras are cooking.
 */
public class ExtraSmelting {

    /** Smelt tasks that may still be busy with extras after their target is met (the parent should not switch away). */
    public interface Holder {
        boolean isSmeltingExtras();
    }

    public static final Item[] FURNACE_ITEMS = Stream.concat(Arrays.stream(new Item[]{Items.RAW_IRON, Items.RAW_GOLD}),
            Arrays.stream(ItemHelper.RAW_FOODS)).toArray(Item[]::new);
    public static final Item[] SMOKER_ITEMS = ItemHelper.RAW_FOODS;
    public static final Item[] BLAST_FURNACE_ITEMS = new Item[]{Items.RAW_IRON, Items.RAW_GOLD};

    /** Cook time per item in ticks: the furnace takes 200, smoker and blast furnace twice as fast. */
    public static final int FURNACE_COOK_TICKS = 200;
    public static final int FAST_COOK_TICKS = 100;

    private final Item[] candidates;
    private final int cookTicksPerItem;
    private boolean active;

    public ExtraSmelting(Item[] candidates, int cookTicksPerItem) {
        this.candidates = candidates;
        this.cookTicksPerItem = cookTicksPerItem;
    }

    /**
     * How many more items the remaining burn time cooks. {@code fuelLeft} is the remaining burn time in units of
     * 200 ticks, as {@code StorageHelper.getFurnaceFuel()} reports it (negative if no furnace screen is open).
     */
    static int itemsForHeat(double fuelLeft, int cookTicksPerItem) {
        if (fuelLeft <= 0) return 0;
        return (int) Math.floor(fuelLeft * 200.0 / cookTicksPerItem + 1e-6);
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Whether the task should keep running for extras: they are cooking already, or the open furnace is empty, still
     * hot enough for one more item, and the inventory holds something to put in.
     */
    public boolean wantsToContinue(AltoClef mod, ItemStack materialSlotStack, double fuelLeft, ItemTarget targetMaterials) {
        if (active) return true;
        if (fuelLeft < 0 || !materialSlotStack.isEmpty()) return false;
        return itemsForHeat(fuelLeft, cookTicksPerItem) >= 1 && findCandidate(mod, targetMaterials) != null;
    }

    /**
     * Call in the container sub task after the output was collected. Afterwards {@link #isActive()} says whether
     * extras are being handled (then return the result, {@code null} = wait), otherwise the caller goes on as usual.
     *
     * @param targetDone nothing of the actual target is left to put in (and none of it is in the material slot)
     */
    public Task tick(AltoClef mod, boolean targetDone, ItemStack materialSlotStack, Slot materialSlot, double fuelLeft,
                     double cookProgress, ItemTarget targetMaterials) {
        if (!targetDone) {
            active = false;
            return null;
        }
        if (!materialSlotStack.isEmpty()) {
            if (!isCandidate(materialSlotStack.getItem())) {
                active = false;
                return null;
            }
            active = true;
            if (fuelLeft <= 0 && cookProgress <= 0) {
                // The heat ran out with extras left over: take them back instead of waiting forever.
                mod.getSlotHandler().clickSlot(materialSlot, 0, SlotActionType.QUICK_MOVE);
            }
            return null;
        }
        int capacity = itemsForHeat(fuelLeft, cookTicksPerItem);
        Item item = capacity >= 1 ? findCandidate(mod, targetMaterials) : null;
        if (item == null) {
            active = false;
            return null;
        }
        active = true;
        int count = Math.min(capacity, mod.getItemStorage().getItemCountInventoryOnly(item));
        return new MoveItemToSlotFromInventoryTask(new ItemTarget(item, count), materialSlot);
    }

    private Item findCandidate(AltoClef mod, ItemTarget targetMaterials) {
        for (Item item : candidates) {
            if (targetMaterials.matches(item)) continue;
            if (mod.getItemStorage().getItemCountInventoryOnly(item) > 0) return item;
        }
        return null;
    }

    private boolean isCandidate(Item item) {
        for (Item candidate : candidates) {
            if (candidate == item) return true;
        }
        return false;
    }
}
