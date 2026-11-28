package adris.altoclef.recipes;

/**
 * Where a recipe is made.
 */
public enum Station {
    /** Crafting grid: 2x2 in the inventory if the recipe fits, otherwise a crafting table. */
    CRAFTING,
    FURNACE,
    SMOKER,
    BLAST_FURNACE,
    CAMPFIRE;

    public boolean isCooking() {
        return this != CRAFTING;
    }
}
