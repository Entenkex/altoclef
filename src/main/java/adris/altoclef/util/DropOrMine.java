package adris.altoclef.util;

/**
 * Pick up an item that is lying around, or mine the next block? (used by {@code MineOrCollectTask})
 * <p>
 * Before, only the distance counted ({@code dropSq + 10 <= blockSq}, squared): in a gravel patch the next gravel was
 * always closer than the flint lying a few blocks away, so the bot mined the whole patch while enough flint was on the
 * ground (baseline run 3, 2026-11-28). Pure, unit tested.
 */
public final class DropOrMine {

    /**
     * Drops this much further away than the closest block are still preferred (blocks, not squared).
     */
    public static final double DROP_BONUS = 4;
    /**
     * Within this distance, drops that cover what is still needed are always picked up first.
     */
    public static final double ENOUGH_ON_GROUND_RANGE = 32;

    private DropOrMine() {
    }

    /**
     * @param dropDistance   distance to the closest matching drop (infinite if none)
     * @param blockDistance  distance to the closest block to mine (infinite if none)
     * @param dropsNearby    matching items lying within {@link #ENOUGH_ON_GROUND_RANGE}
     * @param stillNeeded    items still missing for the target
     * @return true: go for the drop
     */
    public static boolean preferDrop(double dropDistance, double blockDistance, int dropsNearby, int stillNeeded) {
        if (Double.isInfinite(dropDistance)) return false;
        if (Double.isInfinite(blockDistance)) return true;
        if (dropsNearby >= Math.max(stillNeeded, 1) && dropDistance <= ENOUGH_ON_GROUND_RANGE) return true;
        return dropDistance <= blockDistance + DROP_BONUS;
    }
}
