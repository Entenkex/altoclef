package adris.altoclef.diagnostics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * File layout of {@code altoclef/logs/}:
 * <pre>
 * 2026-11-28_09-30-12.jsonl                 decision log of one session (world join → leave)
 * 2026-11-28_09-30-12-t48213-death.json     flight recorder dump of that session
 * </pre>
 * Session ids sort chronologically as strings, so pruning keeps the newest ones by name.
 */
public final class LogDirectory {

    private static final DateTimeFormatter ID_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final String LOG_SUFFIX = ".jsonl";

    private final Path dir;

    public LogDirectory(Path dir) {
        this.dir = dir;
    }

    public Path dir() {
        return dir;
    }

    public static String sessionId(LocalDateTime time) {
        return ID_FORMAT.format(time);
    }

    /**
     * A session id that doesn't collide with an existing log (two joins in the same second get "-2", "-3", …).
     */
    public String newSessionId(LocalDateTime time) {
        String base = sessionId(time);
        String id = base;
        for (int i = 2; Files.exists(sessionLog(id)); i++) {
            id = base + "-" + i;
        }
        return id;
    }

    public Path sessionLog(String sessionId) {
        return dir.resolve(sessionId + LOG_SUFFIX);
    }

    public Path dumpFile(String sessionId, long tick, String trigger) {
        return dir.resolve(sessionId + "-t" + tick + "-" + sanitize(trigger) + ".json");
    }

    /**
     * Deletes all but the newest {@code keep} sessions, together with their dumps.
     *
     * @return the deleted session ids
     */
    public List<String> prune(int keep) throws IOException {
        List<String> deleted = new ArrayList<>();
        if (!Files.isDirectory(dir)) return deleted;
        List<String> sessions;
        try (Stream<Path> files = Files.list(dir)) {
            sessions = files.map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(LOG_SUFFIX))
                    .map(name -> name.substring(0, name.length() - LOG_SUFFIX.length()))
                    .sorted()
                    .toList();
        }
        int toDelete = sessions.size() - Math.max(keep, 0);
        for (int i = 0; i < toDelete; i++) {
            String id = sessions.get(i);
            Files.deleteIfExists(sessionLog(id));
            try (Stream<Path> files = Files.list(dir)) {
                for (Path dump : files.filter(p -> isDumpOf(p.getFileName().toString(), id)).toList()) {
                    Files.deleteIfExists(dump);
                }
            }
            deleted.add(id);
        }
        return deleted;
    }

    private static boolean isDumpOf(String fileName, String sessionId) {
        // "<id>-t<digits>-…" — the "-t<digit>" check keeps "<id>-2.jsonl" sessions from matching "<id>".
        String prefix = sessionId + "-t";
        return fileName.endsWith(".json") && fileName.startsWith(prefix)
                && fileName.length() > prefix.length() && Character.isDigit(fileName.charAt(prefix.length()));
    }

    private static String sanitize(String trigger) {
        return trigger.replaceAll("[^A-Za-z0-9_]", "_");
    }
}
