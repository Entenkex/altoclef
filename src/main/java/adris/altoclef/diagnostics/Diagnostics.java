package adris.altoclef.diagnostics;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.eventbus.EventBus;
import adris.altoclef.eventbus.events.TaskFinishedEvent;
import adris.altoclef.mixins.DeathScreenAccessor;
import adris.altoclef.settings.SettingsService;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.tasksystem.TaskChain;
import adris.altoclef.tasksystem.TaskSystemListener;
import adris.altoclef.util.helpers.WorldHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decision log, flight recorder and run summary in the game (docs/task-architecture.md 2.6, roadmap step 0).
 * <p>
 * A session lasts from world join to leave and gets its own {@code altoclef/logs/<session>.jsonl}.
 * The pure parts ({@link DecisionLog}, {@link FlightRecorder}, {@link ThrashDetector}, {@link RunSummary}, …) are
 * unit tested; this class only collects the game state for them.
 */
public final class Diagnostics implements TaskSystemListener {

    private static final Path LOG_DIR = Paths.get("altoclef", "logs");
    private static final long DUMP_COOLDOWN_MS = 60_000;
    private static Diagnostics instance;

    private final DiagnosticsConfig config = DiagnosticsConfig.get();
    private final LogDirectory directory = new LogDirectory(LOG_DIR);
    private final FlightRecorder recorder = new FlightRecorder(30 * 20);
    private final DumpLimiter dumpLimiter = new DumpLimiter(DUMP_COOLDOWN_MS, 20);
    private final ThrashDetector thrashDetector = new ThrashDetector(6, 10_000);
    private final RunSummary summary = new RunSummary();
    private final ObjectMapper dumpMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private DecisionLog log;
    private String sessionId;
    private long sessionStartMs;
    private long tick;
    private boolean wasInGame;
    private boolean wasOnDeathScreen;
    private String lastDimension;
    private boolean summaryRunning;
    private String lastUserTask = "";
    private int lastCheckedPathSize;
    private int loopEndsAt = -1;

    private Diagnostics() {
    }

    /**
     * Called once after the settings are loaded.
     */
    public static void init() {
        if (instance != null) return;
        instance = new Diagnostics();
        TaskSystemListener.set(instance);
        EventBus.subscribe(TaskFinishedEvent.class, evt -> instance.onUserTaskFinished(evt));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                instance.endSession("shutdown");
            } catch (RuntimeException ignored) {
                // The game is going down; losing the last summary line is fine.
            }
        }, "altoclef-decision-log-shutdown"));
    }

    public static Optional<Diagnostics> get() {
        return Optional.ofNullable(instance);
    }

    // ------------------------------------------------------------------ game hooks

    /**
     * Every client tick, also while the bot is idle.
     */
    public static void clientTick() {
        if (instance != null) instance.onClientTick();
    }

    /**
     * A command line the user (or the idle/death command) ran.
     */
    public static void command(String line) {
        if (instance != null && instance.config.logEvents.get()) {
            instance.event("command", line, Map.of("line", line));
        }
    }

    /**
     * A user task was set ({@code idle} = the idle command's task).
     */
    public static void userTaskStarted(Task task, boolean idle) {
        if (instance == null) return;
        if (!idle) {
            instance.lastUserTask = String.valueOf(task);
            instance.summary.start(String.valueOf(task));
            instance.summaryRunning = true;
        }
        if (instance.config.logEvents.get()) {
            instance.event("user_task", (idle ? "Idle task: " : "User task: ") + task, Map.of("task", String.valueOf(task), "idle", idle));
        }
    }

    private void onClientTick() {
        boolean inGame = AltoClef.inGame();
        if (inGame && !wasInGame) startSession();
        if (!inGame && wasInGame) endSession("left world");
        wasInGame = inGame;
        if (!inGame || log == null) return;
        tick++;
        applySettings();

        String dimension = WorldHelper.getCurrentDimension().name();
        if (lastDimension != null && !lastDimension.equals(dimension) && config.logEvents.get()) {
            event("dimension", lastDimension + " -> " + dimension, Map.of("from", lastDimension, "to", dimension));
        }
        lastDimension = dimension;

        boolean onDeathScreen = MinecraftClient.getInstance().currentScreen instanceof DeathScreen;
        if (onDeathScreen && !wasOnDeathScreen) onDeath();
        wasOnDeathScreen = onDeathScreen;
    }

    private void onDeath() {
        String cause = "unknown";
        if (MinecraftClient.getInstance().currentScreen instanceof DeathScreenAccessor accessor) {
            Text message = accessor.getMessage();
            if (message != null) cause = message.getString();
        }
        summary.death(cause);
        event("death", "Died: " + cause, Map.of("cause", cause));
        if (config.dumpOnDeath.get()) dump("death", cause);
        writeSummary("death");
        if (config.summaryInChat.get()) Debug.logLines("Died (" + cause + "). Run summary:", summary.chatLines());
    }

    private void onUserTaskFinished(TaskFinishedEvent evt) {
        if (log == null) return;
        if (config.logEvents.get()) {
            // A cancelled task (@stop) arrives without the task; fall back to the last user task we saw start.
            String task = evt.lastTaskRan != null ? String.valueOf(evt.lastTaskRan) : lastUserTask;
            event("user_task_finished", "Finished " + task + " in " + RunSummary.formatDuration((long) evt.durationSeconds),
                    Map.of("task", task, "seconds", evt.durationSeconds));
        }
        if (summaryRunning) {
            writeSummary("user task finished");
            if (config.summaryInChat.get()) Debug.logLines("Run summary:", summary.chatLines());
            summaryRunning = false;
        }
    }

    // ------------------------------------------------------------------ task system

    @Override
    public void runnerTicked(List<ChainPriority> chains, TaskChain previous, TaskChain chosen) {
        if (log == null) return;
        String chosenName = chosen == null ? null : chosen.getName();
        summary.tick(chosenName);

        Map<String, Object> priorities = new LinkedHashMap<>();
        for (ChainPriority c : chains) {
            priorities.put(c.chain(), c.active() ? finite(c.priority()) : null);
        }

        Map<String, Object> snapshot = context();
        snapshot.put("chain", chosenName);
        snapshot.put("priorities", priorities);
        snapshot.put("path", chosen == null ? List.of() : taskPath(chosen));
        recorder.record(snapshot);
        if (chosen != null) checkTaskLoop(chosen.getTasks());

        if (previous != chosen) {
            String from = previous == null ? null : previous.getName();
            summary.switched(from, chosenName);
            if (config.logChainSwitches.get()) {
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("from", from);
                fields.put("to", chosenName);
                fields.put("reason", switchReason(chains, previous, chosen));
                fields.put("priorities", priorities);
                fields.put("path", chosen == null ? List.of() : taskPath(chosen));
                event("switch", (from == null ? "(none)" : from) + " -> " + (chosenName == null ? "(none)" : chosenName), fields);
            }
            int count = thrashDetector.onSwitch(from, chosenName, nowMs());
            if (count > 0) onThrashing(from, chosenName, count);
        }
    }

    @Override
    public void taskStarted(Task task, TaskChain chain) {
        if (log != null && config.logTasks.get()) {
            event("task_start", String.valueOf(task), Map.of("task", String.valueOf(task), "chain", chain == null ? "" : chain.getName()));
        }
    }

    @Override
    public void taskStopped(Task task, Task interruptedBy) {
        if (log != null && config.logTasks.get()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("task", String.valueOf(task));
            fields.put("by", interruptedBy == null ? null : String.valueOf(interruptedBy));
            event("task_stop", task + (interruptedBy == null ? "" : " (replaced by " + interruptedBy + ")"), fields);
        }
    }

    @Override
    public void taskInterrupted(Task task, Task interruptedBy) {
        if (log != null && config.logTasks.get()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("task", String.valueOf(task));
            fields.put("by", interruptedBy == null ? null : String.valueOf(interruptedBy));
            event("task_interrupt", String.valueOf(task), fields);
        }
    }

    /**
     * Planning loops (a segment of the task path that repeats itself) end in a stack overflow when the tree is
     * stopped; report them as soon as they appear. Only checked when the path got longer, and only the first 300
     * tasks (a loop shows up long before that).
     */
    private void checkTaskLoop(List<Task> path) {
        int size = path.size();
        if (loopEndsAt >= 0 && size < loopEndsAt) loopEndsAt = -1; // the loop was unwound
        if (size <= lastCheckedPathSize || loopEndsAt >= 0) {
            lastCheckedPathSize = Math.min(lastCheckedPathSize, size);
            return;
        }
        lastCheckedPathSize = size;
        List<Task> checked = size > 300 ? path.subList(0, 300) : path;
        Optional<TaskPathLoops.Loop> loop = TaskPathLoops.find(checked);
        if (loop.isEmpty()) return;
        TaskPathLoops.Loop l = loop.get();
        loopEndsAt = l.start() + 2 * l.length();
        List<String> segment = new ArrayList<>();
        for (Task task : checked.subList(l.start(), l.start() + l.length())) segment.add(String.valueOf(task));
        String msg = "Task loop: " + l.length() + " tasks repeat at depth " + l.start() + " (path depth " + size + ")";
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("start", l.start());
        fields.put("length", l.length());
        fields.put("depth", size);
        fields.put("segment", segment);
        event("task_loop", msg, fields);
        Debug.logWarning(msg + ", starting with " + segment.get(0) + " (see altoclef/logs)");
        if (config.dumpOnTaskLoop.get()) dump("task_loop", msg);
    }

    private void onThrashing(String a, String b, int count) {
        String msg = a + " <-> " + b + ": " + count + " switches in " + config.thrashWindowSeconds.get() + " s";
        event("thrashing", msg, Map.of("pair", ThrashDetector.pairKey(a, b), "switches", count));
        Debug.logWarning("Thrashing between chains " + msg + " (see altoclef/logs)");
        if (config.dumpOnThrashing.get()) dump("thrashing", msg);
    }

    /**
     * Why the old runner switched (the new scheduler will report its own reasons).
     */
    static String switchReason(List<ChainPriority> chains, TaskChain previous, TaskChain chosen) {
        if (chosen == null) return "nothing_active";
        if (previous == null) return "start";
        for (ChainPriority c : chains) {
            if (c.chain().equals(previous.getName())) {
                return c.active() && c.priority() != Float.NEGATIVE_INFINITY ? "higher_priority" : "previous_inactive";
            }
        }
        return "higher_priority";
    }

    private static List<String> taskPath(TaskChain chain) {
        List<String> path = new ArrayList<>();
        for (Task task : chain.getTasks()) path.add(String.valueOf(task));
        return path;
    }

    // ------------------------------------------------------------------ dumps and summary

    /**
     * Writes the flight recorder to a dump file.
     *
     * @return the file, empty if the limit was reached or there is no session
     */
    public Optional<Path> dump(String trigger, String detail) {
        if (log == null || !dumpLimiter.tryAcquire(trigger, nowMs())) return Optional.empty();
        Path file = directory.dumpFile(sessionId, tick, trigger);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("trigger", trigger);
        content.put("detail", detail);
        content.put("session", sessionId);
        content.putAll(context());
        content.put("summary", summary.toMap());
        content.put("snapshots", recorder.snapshots());
        try {
            Files.createDirectories(file.getParent());
            dumpMapper.writeValue(file.toFile(), content);
        } catch (IOException e) {
            Debug.logWarning("Could not write dump " + file + ": " + e.getMessage());
            return Optional.empty();
        }
        summary.dumped();
        event("dump", trigger + ": " + file.getFileName(), Map.of("trigger", trigger, "file", file.getFileName().toString()));
        return Optional.of(file);
    }

    public List<String> summaryLines() {
        return summary.chatLines();
    }

    public void writeSummary(String reason) {
        if (log == null) return;
        summary.suppressed(log.pendingSuppressed());
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("reason", reason);
        fields.putAll(summary.toMap());
        event("summary", "Summary (" + reason + ")", fields);
        log.flush();
    }

    public Optional<Path> currentLogFile() {
        return log == null ? Optional.empty() : Optional.of(directory.sessionLog(sessionId));
    }

    // ------------------------------------------------------------------ session

    private synchronized void startSession() {
        if (!config.decisionLog.get()) return;
        try {
            directory.prune(Math.max(config.keepSessions.get() - 1, 0));
        } catch (IOException e) {
            Debug.logWarning("Could not clean up old logs in " + LOG_DIR + ": " + e.getMessage());
        }
        sessionId = directory.newSessionId(LocalDateTime.now());
        sessionStartMs = System.currentTimeMillis();
        tick = 0;
        lastDimension = null;
        wasOnDeathScreen = false;
        recorder.clear();
        lastCheckedPathSize = 0;
        loopEndsAt = -1;
        dumpLimiter.resetSession();
        thrashDetector.reset();
        summary.start("session " + sessionId);
        summaryRunning = false;
        log = new DecisionLog(new AsyncFileSink(directory.sessionLog(sessionId)), System::currentTimeMillis, config.maxLogLinesPerSecond.get());
        applySettings();

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("session", sessionId);
        fields.put("mod", modVersion("altoclef"));
        fields.put("minecraft", modVersion("minecraft"));
        fields.put("profile", SettingsService.profiles().getActiveProfile());
        fields.put("modifiedSettings", SettingsService.manager().nonDefaultValues());
        ServerInfo server = MinecraftClient.getInstance().getCurrentServerEntry();
        fields.put("server", server == null ? "singleplayer" : server.address);
        event("session_start", "Session " + sessionId, fields);
    }

    private synchronized void endSession(String reason) {
        if (log == null) return;
        writeSummary(reason);
        event("session_end", reason, Map.of("reason", reason));
        log.close();
        log = null;
    }

    private void applySettings() {
        recorder.setCapacity(config.flightRecorderSeconds.get() * 20);
        dumpLimiter.configure(DUMP_COOLDOWN_MS, config.maxDumpsPerSession.get());
        thrashDetector.configure(config.thrashSwitches.get(), config.thrashWindowSeconds.get() * 1000L);
        if (log != null) log.setMaxLinesPerSecond(config.maxLogLinesPerSecond.get());
    }

    // ------------------------------------------------------------------ helpers

    private void event(String type, String msg, Map<String, ?> fields) {
        if (log != null) log.event(type, msg, context(), fields);
    }

    /**
     * Fields every line carries: tick, time since session start, position, dimension, health, food.
     */
    private Map<String, Object> context() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("tick", tick);
        ctx.put("t", nowMs() - sessionStartMs);
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            ctx.put("pos", List.of(round(player.getX()), round(player.getY()), round(player.getZ())));
            ctx.put("dim", WorldHelper.getCurrentDimension().name());
            ctx.put("hp", round(player.getHealth()));
            ctx.put("food", player.getHungerManager().getFoodLevel());
        }
        return ctx;
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    /**
     * JSON has no infinity: -inf ("doesn't want to run") is written as null.
     */
    private static Object finite(float v) {
        return Float.isFinite(v) ? (Object) (double) v : null;
    }

    private static String modVersion(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private long nowMs() {
        return System.currentTimeMillis();
    }
}
