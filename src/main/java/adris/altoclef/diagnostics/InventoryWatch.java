package adris.altoclef.diagnostics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Notices when a tool, weapon or armor piece disappears from the inventory and guesses why.
 * <p>
 * Fed once per tick with the damageable items in the inventory (incl. armor, offhand and cursor): item id → count and
 * the lowest remaining durability among them. A drop in count is reported as
 * <ul>
 *     <li>{@code bot_drop} if the bot threw that item away shortly before,</li>
 *     <li>{@code broke} if one of them had (almost) no durability left,</li>
 *     <li>{@code unknown} otherwise (player pressed Q, stored in a chest, …).</li>
 * </ul>
 * Pure: no Minecraft classes.
 */
public final class InventoryWatch {

    /**
     * @param minRemaining lowest remaining durability of the stacks of this item
     */
    public record Tracked(int count, int minRemaining) {
    }

    public record Loss(String item, int before, int after, String reason) {
    }

    static final int BROKE_THRESHOLD = 3;

    private Map<String, Tracked> previous;

    /**
     * @param now             damageable items in the inventory this tick
     * @param droppedByBot    items the bot threw away within the last second
     * @return what disappeared since the last call (empty on the first call)
     */
    public List<Loss> update(Map<String, Tracked> now, Set<String> droppedByBot) {
        List<Loss> losses = new ArrayList<>();
        if (previous != null) {
            for (Map.Entry<String, Tracked> entry : previous.entrySet()) {
                String item = entry.getKey();
                int before = entry.getValue().count();
                int after = now.containsKey(item) ? now.get(item).count() : 0;
                if (after >= before) continue;
                String reason;
                if (droppedByBot.contains(item)) reason = "bot_drop";
                else if (entry.getValue().minRemaining() <= BROKE_THRESHOLD) reason = "broke";
                else reason = "unknown";
                losses.add(new Loss(item, before, after, reason));
            }
        }
        previous = new HashMap<>(now);
        return losses;
    }

    /**
     * Forget the last state (death, world change): the next update only sets a new baseline.
     */
    public void reset() {
        previous = null;
    }
}
