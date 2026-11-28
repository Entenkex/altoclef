package adris.altoclef.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Flight recorder, dump limits, thrashing detection, run summary, log directory and the file sink.
 */
class DiagnosticsPartsTest {

    @Test
    void flightRecorderKeepsTheNewestSnapshots() {
        FlightRecorder recorder = new FlightRecorder(3);
        for (int i = 0; i < 5; i++) {
            recorder.record(Map.of("tick", i));
        }
        assertEquals(List.of(2, 3, 4), recorder.snapshots().stream().map(s -> s.get("tick")).toList());

        recorder.setCapacity(2);
        assertEquals(List.of(3, 4), recorder.snapshots().stream().map(s -> s.get("tick")).toList());
    }

    @Test
    void dumpLimiterHasCooldownPerTriggerAndASessionMaximum() {
        DumpLimiter limiter = new DumpLimiter(60_000, 3);

        assertTrue(limiter.tryAcquire("death", 0));
        assertFalse(limiter.tryAcquire("death", 30_000), "cooldown per trigger");
        assertTrue(limiter.tryAcquire("thrashing", 30_000), "other trigger has its own cooldown");
        assertTrue(limiter.tryAcquire("death", 61_000));
        assertFalse(limiter.tryAcquire(DumpLimiter.MANUAL, 62_000), "session maximum reached");

        limiter.resetSession();
        assertTrue(limiter.tryAcquire(DumpLimiter.MANUAL, 62_000));
        assertTrue(limiter.tryAcquire(DumpLimiter.MANUAL, 62_001), "manual dumps ignore the cooldown");
    }

    @Test
    void thrashDetectorReportsAPairOncePerWindow() {
        ThrashDetector detector = new ThrashDetector(4, 10_000);

        assertEquals(0, detector.onSwitch("User Tasks", "Mob Defense", 0));
        assertEquals(0, detector.onSwitch("Mob Defense", "User Tasks", 1000));
        assertEquals(0, detector.onSwitch("Food", "User Tasks", 1500), "other pair doesn't count");
        assertEquals(0, detector.onSwitch("User Tasks", "Mob Defense", 2000));
        assertEquals(4, detector.onSwitch("Mob Defense", "User Tasks", 3000));
        assertEquals(0, detector.onSwitch("User Tasks", "Mob Defense", 4000), "already reported in this window");
    }

    @Test
    void thrashDetectorForgetsSwitchesOutsideTheWindow() {
        ThrashDetector detector = new ThrashDetector(3, 1000);
        detector.onSwitch("A", "B", 0);
        detector.onSwitch("B", "A", 500);
        assertEquals(0, detector.onSwitch("A", "B", 2000), "the first two are older than the window");
    }

    @Test
    void runSummaryCountsTimeSwitchesAndDeaths() {
        RunSummary summary = new RunSummary();
        summary.start("get diamond");
        for (int i = 0; i < 60; i++) summary.tick("User Tasks");
        for (int i = 0; i < 20; i++) summary.tick("Mob Defense");
        summary.switched("User Tasks", "Mob Defense");
        summary.switched("Mob Defense", "User Tasks");
        summary.switched("User Tasks", "Food");
        summary.death("Steve was slain by Zombie");

        Map<String, Object> map = summary.toMap();
        assertEquals(4.0, map.get("seconds"));
        assertEquals(Map.of("User Tasks", 3.0, "Mob Defense", 1.0), map.get("secondsPerChain"));
        assertEquals(3, map.get("switches"));
        assertEquals(2, ((Map<?, ?>) map.get("switchPairs")).get("Mob Defense <-> User Tasks"));

        List<String> chat = summary.chatLines();
        assertEquals("Time 4s: User Tasks 75%, Mob Defense 25%", chat.get(0));
        assertEquals("Switches: 3, most: Mob Defense <-> User Tasks (2)", chat.get(1));
        assertEquals("Moved 0 blocks (net 0, max 0), in water 0s, unstuck 0x", chat.get(2));
        assertEquals("Deaths: 1 (last: Steve was slain by Zombie)", chat.get(3));
        assertEquals(Map.of("Mob Defense", 1, "User Tasks", 1, "Food", 1), map.get("chainEntries"));
    }

    @Test
    void runSummaryMeasuresMovementAndWater() {
        RunSummary summary = new RunSummary();
        summary.start("explore");
        // 30 blocks east, 30 back, then 40 north: walked 100, ends 40 from the start, was 40 away at most.
        for (int x = 0; x <= 30; x++) summary.position(x, 0, false);
        for (int x = 29; x >= 0; x--) summary.position(x, 0, x < 10);
        for (int z = 1; z <= 40; z++) summary.position(0, z, false);
        // Teleport (respawn, portal) is not walked distance.
        summary.position(1000, 1000, false);
        summary.position(1000, 1001, false);
        for (int i = 0; i < 25; i++) summary.switched("User Tasks", i % 2 == 0 ? "Unstuck Chain" : "User Tasks");

        Map<String, Object> map = summary.toMap();
        assertEquals(101.0, map.get("distanceWalked"));
        assertEquals(1414.9, map.get("netDistance")); // ends at (1000, 1001)
        assertEquals(0.5, map.get("waterSeconds")); // 10 ticks
        assertTrue(summary.chatLines().get(2).endsWith("unstuck 13x"), summary.chatLines().get(2));
    }

    @Test
    void durationFormatting() {
        assertEquals("59s", RunSummary.formatDuration(59));
        assertEquals("2m 5s", RunSummary.formatDuration(125));
        assertEquals("1h 0m 1s", RunSummary.formatDuration(3601));
    }

    @Test
    void logDirectoryPrunesOldSessionsWithTheirDumps(@TempDir Path dir) throws Exception {
        LogDirectory logs = new LogDirectory(dir);
        for (String id : List.of("2026-11-27_10-00-00", "2026-11-28_09-00-00", "2026-11-28_09-00-00-2")) {
            Files.writeString(logs.sessionLog(id), "{}\n");
            Files.writeString(logs.dumpFile(id, 100, "death"), "{}");
        }
        Files.writeString(dir.resolve("unrelated.txt"), "keep me");

        List<String> deleted = logs.prune(1);

        assertEquals(List.of("2026-11-27_10-00-00", "2026-11-28_09-00-00"), deleted);
        assertTrue(Files.exists(logs.sessionLog("2026-11-28_09-00-00-2")));
        assertTrue(Files.exists(logs.dumpFile("2026-11-28_09-00-00-2", 100, "death")),
                "a dump of '<id>-2' must not be taken for a dump of '<id>'");
        assertFalse(Files.exists(logs.dumpFile("2026-11-28_09-00-00", 100, "death")));
        assertTrue(Files.exists(dir.resolve("unrelated.txt")));
    }

    @Test
    void newSessionIdAvoidsCollisions(@TempDir Path dir) throws Exception {
        LogDirectory logs = new LogDirectory(dir);
        LocalDateTime time = LocalDateTime.of(2026, 11, 28, 9, 30, 12);
        assertEquals("2026-11-28_09-30-12", logs.newSessionId(time));
        Files.writeString(logs.sessionLog("2026-11-28_09-30-12"), "");
        assertEquals("2026-11-28_09-30-12-2", logs.newSessionId(time));
        assertEquals("2026-11-28_09-30-12-t5-thrashing.json", logs.dumpFile("2026-11-28_09-30-12", 5, "thrashing").getFileName().toString());
    }

    @Test
    void asyncFileSinkWritesEverythingBeforeClose(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sub").resolve("log.jsonl");
        AsyncFileSink sink = new AsyncFileSink(file);
        for (int i = 0; i < 100; i++) sink.write("{\"i\":" + i + "}");
        sink.close();
        sink.write("after close is ignored");

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(100, lines.size());
        assertEquals("{\"i\":99}", lines.get(99));
    }
}
