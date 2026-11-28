package adris.altoclef.diagnostics;

import java.util.List;
import java.util.Optional;

/**
 * Finds planning loops in the active task path: a segment that directly repeats itself, e.g.
 * {@code … NETHER → portal → flint and steel → iron → stone pickaxe → blackstone → NETHER → portal → …}.
 * <p>
 * The same task twice in a path is not enough: {@code iron pickaxe → stone pickaxe → wooden pickaxe} nests several
 * "craft in crafting table" tasks on purpose, with different targets in between. A loop repeats a whole segment
 * (compared with {@code equals}, i.e. the tasks' {@code isEqual}).
 */
public final class TaskPathLoops {

    /**
     * Where the first directly repeated segment starts and how long it is.
     */
    public record Loop(int start, int length) {
    }

    private TaskPathLoops() {
    }

    /**
     * @param path root first
     * @return the earliest, shortest segment {@code path[start, start+length)} that equals
     * {@code path[start+length, start+2*length)}
     */
    public static <T> Optional<Loop> find(List<T> path) {
        int n = path.size();
        for (int start = 0; start < n; start++) {
            for (int length = 1; start + 2 * length <= n; length++) {
                if (repeats(path, start, length)) return Optional.of(new Loop(start, length));
            }
        }
        return Optional.empty();
    }

    private static <T> boolean repeats(List<T> path, int start, int length) {
        for (int k = 0; k < length; k++) {
            if (!path.get(start + k).equals(path.get(start + length + k))) return false;
        }
        return true;
    }
}
