package adris.altoclef.diagnostics;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Detects flipping between the same two candidates: {@code threshold} or more switches between A and B (either
 * direction) within {@code windowMs}. Reports each pair at most once per window. It fixes nothing; it tells us where
 * margins / priorities are wrong (docs/task-architecture.md 2.6).
 */
public final class ThrashDetector {

    private final Map<String, ArrayDeque<Long>> switches = new HashMap<>();
    private final Map<String, Long> lastReport = new HashMap<>();
    private int threshold;
    private long windowMs;

    public ThrashDetector(int threshold, long windowMs) {
        this.threshold = threshold;
        this.windowMs = windowMs;
    }

    public synchronized void configure(int threshold, long windowMs) {
        this.threshold = threshold;
        this.windowMs = windowMs;
    }

    /**
     * Records a switch {@code from → to}.
     *
     * @return the number of switches between the pair in the window if this switch makes it thrashing (and it wasn't
     * reported in the current window yet), otherwise 0
     */
    public synchronized int onSwitch(String from, String to, long nowMs) {
        if (from == null || to == null || from.equals(to)) return 0;
        String key = pairKey(from, to);
        ArrayDeque<Long> times = switches.computeIfAbsent(key, k -> new ArrayDeque<>());
        times.addLast(nowMs);
        while (!times.isEmpty() && nowMs - times.peekFirst() > windowMs) {
            times.removeFirst();
        }
        if (times.size() < threshold) return 0;
        Long last = lastReport.get(key);
        if (last != null && nowMs - last < windowMs) return 0;
        lastReport.put(key, nowMs);
        return times.size();
    }

    public synchronized void reset() {
        switches.clear();
        lastReport.clear();
    }

    static String pairKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + " <-> " + b : b + " <-> " + a;
    }
}
