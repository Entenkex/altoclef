package adris.altoclef.diagnostics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * The decision log (docs/task-architecture.md 2.6): one JSON object per line, written only when something happens.
 * <p>
 * Every line has {@code type}, {@code msg} (short, human readable), the context fields (tick, time, position, …)
 * and the event's own fields. To keep a flickering decision from flooding the file, at most
 * {@code maxLinesPerSecond} lines are written per second; the dropped ones are reported as one {@code suppressed}
 * line. Important types (session start, death, summary, dump, thrashing) are never dropped.
 * <p>
 * Pure: no Minecraft classes, the clock and the sink are passed in.
 */
public final class DecisionLog {

    /**
     * Where the lines go (a file in the game, a list in tests).
     */
    public interface Sink {
        void write(String line);

        void flush();

        void close();
    }

    private static final Set<String> NEVER_DROPPED = Set.of("session_start", "session_end", "death", "summary", "dump", "thrashing", "task_loop");

    private final ObjectMapper mapper = new ObjectMapper();
    private final Sink sink;
    private final LongSupplier clockMs;
    private int maxLinesPerSecond;

    private boolean windowStarted;
    private long windowStart;
    private int linesInWindow;
    private int suppressed;

    public DecisionLog(Sink sink, LongSupplier clockMs, int maxLinesPerSecond) {
        this.sink = sink;
        this.clockMs = clockMs;
        this.maxLinesPerSecond = maxLinesPerSecond;
    }

    public void setMaxLinesPerSecond(int maxLinesPerSecond) {
        this.maxLinesPerSecond = maxLinesPerSecond;
    }

    /**
     * @param context common fields (tick, t, pos, …), written first; may be empty
     * @param fields  the event's own fields; may be empty
     * @return whether the line was written (false = suppressed by the rate limit)
     */
    public synchronized boolean event(String type, String msg, Map<String, ?> context, Map<String, ?> fields) {
        long now = clockMs.getAsLong();
        if (!windowStarted || now - windowStart >= 1000) {
            windowStarted = true;
            windowStart = now;
            linesInWindow = 0;
        }
        boolean important = NEVER_DROPPED.contains(type);
        if (!important && linesInWindow >= maxLinesPerSecond) {
            suppressed++;
            return false;
        }
        if (suppressed > 0) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("count", suppressed);
            sink.write(toLine("suppressed", suppressed + " lines dropped (rate limit)", context, info));
            suppressed = 0;
            linesInWindow++;
        }
        sink.write(toLine(type, msg, context, fields));
        linesInWindow++;
        return true;
    }

    public int pendingSuppressed() {
        return suppressed;
    }

    public void flush() {
        sink.flush();
    }

    public void close() {
        sink.close();
    }

    String toLine(String type, String msg, Map<String, ?> context, Map<String, ?> fields) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("type", type);
        line.put("msg", msg);
        line.putAll(context);
        line.putAll(fields);
        try {
            return mapper.writeValueAsString(line);
        } catch (JsonProcessingException e) {
            // Never let logging break the bot; keep at least type and message.
            return "{\"type\":\"" + type + "\",\"msg\":\"unserializable event: " + e.getOriginalMessage().replace('"', '\'') + "\"}";
        }
    }
}
