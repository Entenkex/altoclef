package adris.altoclef.diagnostics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ring buffer of the last N per-tick snapshots (docs/task-architecture.md 2.6). Kept in memory only and written as a
 * dump when something goes wrong (death, thrashing, {@code @dump}).
 */
public final class FlightRecorder {

    private final ArrayDeque<Map<String, Object>> snapshots = new ArrayDeque<>();
    private int capacity;

    public FlightRecorder(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public synchronized void setCapacity(int capacity) {
        this.capacity = Math.max(1, capacity);
        trim();
    }

    public synchronized int capacity() {
        return capacity;
    }

    public synchronized void record(Map<String, Object> snapshot) {
        snapshots.addLast(snapshot);
        trim();
    }

    /**
     * Oldest first.
     */
    public synchronized List<Map<String, Object>> snapshots() {
        return new ArrayList<>(snapshots);
    }

    public synchronized int size() {
        return snapshots.size();
    }

    public synchronized void clear() {
        snapshots.clear();
    }

    private void trim() {
        while (snapshots.size() > capacity) {
            snapshots.removeFirst();
        }
    }
}
