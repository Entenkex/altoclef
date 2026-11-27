package adris.altoclef.recipes;

/**
 * An item id with a count, e.g. the output of a recipe.
 */
public record ItemAmount(String item, int count) {

    public ItemAmount {
        item = ItemIds.normalize(item);
        if (count < 1) throw new IllegalArgumentException("Count must be at least 1: " + item + " x " + count);
    }

    @Override
    public String toString() {
        return count == 1 ? item : item + " x" + count;
    }
}
