package adris.altoclef.diagnostics;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps a bug in a loop from filling the disk with dumps: at most one dump per trigger per cooldown and at most
 * {@code maxPerSession} dumps per session. Manual dumps ({@code @dump}) ignore the cooldown but still count.
 */
public final class DumpLimiter {

    public static final String MANUAL = "manual";

    private final Map<String, Long> lastDump = new HashMap<>();
    private long cooldownMs;
    private int maxPerSession;
    private int dumps;

    public DumpLimiter(long cooldownMs, int maxPerSession) {
        this.cooldownMs = cooldownMs;
        this.maxPerSession = maxPerSession;
    }

    public synchronized void configure(long cooldownMs, int maxPerSession) {
        this.cooldownMs = cooldownMs;
        this.maxPerSession = maxPerSession;
    }

    /**
     * @return whether a dump for this trigger may be written now (and counts it if so)
     */
    public synchronized boolean tryAcquire(String trigger, long nowMs) {
        if (dumps >= maxPerSession) return false;
        if (!MANUAL.equals(trigger)) {
            Long last = lastDump.get(trigger);
            if (last != null && nowMs - last < cooldownMs) return false;
        }
        lastDump.put(trigger, nowMs);
        dumps++;
        return true;
    }

    public synchronized int dumpsThisSession() {
        return dumps;
    }

    public synchronized void resetSession() {
        lastDump.clear();
        dumps = 0;
    }
}
