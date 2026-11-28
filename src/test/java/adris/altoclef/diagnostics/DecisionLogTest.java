package adris.altoclef.diagnostics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DecisionLogTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> lines = new ArrayList<>();
    private long now;
    private DecisionLog log;

    @BeforeEach
    void setUp() {
        now = 0;
        DecisionLog.Sink sink = new DecisionLog.Sink() {
            @Override
            public void write(String line) {
                lines.add(line);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        log = new DecisionLog(sink, () -> now, 3);
    }

    private JsonNode line(int i) throws Exception {
        return mapper.readTree(lines.get(i));
    }

    @Test
    void writesTypeMessageContextThenFieldsAsOneJsonLine() throws Exception {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("tick", 42);
        context.put("pos", List.of(1.5, 64.0, -3.0));
        Map<String, Object> fields = Map.of("to", "Mob Defense");

        log.event("switch", "User Tasks -> Mob Defense", context, fields);

        assertEquals(1, lines.size());
        assertFalse(lines.get(0).contains("\n"));
        JsonNode node = line(0);
        assertEquals("switch", node.get("type").asText());
        assertEquals("User Tasks -> Mob Defense", node.get("msg").asText());
        assertEquals(42, node.get("tick").asInt());
        assertEquals(-3.0, node.get("pos").get(2).asDouble());
        assertEquals("Mob Defense", node.get("to").asText());
        // Field order: type, msg, context, fields (readable with plain grep / less).
        assertTrue(lines.get(0).startsWith("{\"type\":\"switch\",\"msg\":"));
    }

    @Test
    void rateLimitDropsLinesAndReportsThemOnce() throws Exception {
        for (int i = 0; i < 5; i++) {
            log.event("task", "line " + i, Map.of(), Map.of());
        }
        assertEquals(3, lines.size());
        assertEquals(2, log.pendingSuppressed());

        now = 1000; // next second
        log.event("task", "after", Map.of(), Map.of());

        assertEquals(5, lines.size());
        assertEquals("suppressed", line(3).get("type").asText());
        assertEquals(2, line(3).get("count").asInt());
        assertEquals("after", line(4).get("msg").asText());
        assertEquals(0, log.pendingSuppressed());
    }

    @Test
    void importantEventsAreNeverDropped() throws Exception {
        for (int i = 0; i < 3; i++) {
            log.event("task", "fill", Map.of(), Map.of());
        }
        assertFalse(log.event("task", "dropped", Map.of(), Map.of()));
        assertTrue(log.event("death", "died", Map.of(), Map.of()));
        assertTrue(log.event("summary", "summary", Map.of(), Map.of()));
        assertFalse(log.event("switch", "dropped", Map.of(), Map.of()));
        // The suppressed note is written before the next allowed line.
        assertEquals("suppressed", line(3).get("type").asText());
        assertEquals("death", line(4).get("type").asText());
        assertEquals("summary", line(5).get("type").asText());
    }
}
