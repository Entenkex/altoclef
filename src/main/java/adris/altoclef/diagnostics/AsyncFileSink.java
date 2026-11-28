package adris.altoclef.diagnostics;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Writes log lines to a file on a background thread, so the game tick never waits for the disk.
 * <p>
 * {@link #flush()} and {@link #close()} are queued like lines, so everything written before them ends up in the file.
 * {@link #close()} waits up to two seconds for the writer to finish. Write errors disable the sink (reported once on
 * stderr) instead of throwing into the game.
 */
public final class AsyncFileSink implements DecisionLog.Sink {

    private static final String FLUSH = "\u0000flush";
    private static final String CLOSE = "\u0000close";

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final Thread thread;
    private final Path file;
    private volatile boolean closed;

    public AsyncFileSink(Path file) {
        this.file = file;
        thread = new Thread(this::run, "altoclef-decision-log");
        thread.setDaemon(true);
        thread.start();
    }

    public Path file() {
        return file;
    }

    @Override
    public void write(String line) {
        if (!closed) queue.add(line);
    }

    @Override
    public void flush() {
        if (!closed) queue.add(FLUSH);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        queue.add(CLOSE);
        try {
            thread.join(TimeUnit.SECONDS.toMillis(2));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void run() {
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                while (true) {
                    String line = queue.take();
                    if (line == CLOSE) {
                        out.flush();
                        return;
                    }
                    if (line == FLUSH) {
                        out.flush();
                        continue;
                    }
                    out.write(line);
                    out.newLine();
                    // Write whatever has queued up, then flush once (cheap, and a crash loses little).
                    if (queue.isEmpty()) out.flush();
                }
            }
        } catch (IOException e) {
            System.err.println("ALTO CLEF: decision log disabled, cannot write " + file + ": " + e.getMessage());
            closed = true;
            queue.clear();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
