package adris.altoclef.recipes;

/**
 * Item ids as used by the recipe model: {@code namespace:path}, lower case.
 */
public final class ItemIds {

    private ItemIds() {
    }

    /**
     * {@code "Oak_Planks"} → {@code "minecraft:oak_planks"}; ids with a namespace are kept (lower-cased).
     */
    public static String normalize(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Empty item id");
        String lower = id.strip().toLowerCase();
        return lower.indexOf(':') < 0 ? "minecraft:" + lower : lower;
    }
}
