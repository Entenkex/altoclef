package adris.altoclef.diagnostics;

import adris.altoclef.settings.Setting;
import adris.altoclef.settings.SettingsManager;
import adris.altoclef.settings.SettingsService;

/**
 * Settings of the decision log and flight recorder (key prefix {@code debug.}), see docs/task-architecture.md 2.6.
 */
public final class DiagnosticsConfig {

    private static final String P = "debug.";
    private static DiagnosticsConfig instance;

    public final Setting<Boolean> decisionLog;
    public final Setting<Boolean> logChainSwitches;
    public final Setting<Boolean> logTasks;
    public final Setting<Boolean> logEvents;
    public final Setting<Integer> maxLogLinesPerSecond;
    public final Setting<Integer> keepSessions;
    public final Setting<Integer> flightRecorderSeconds;
    public final Setting<Boolean> dumpOnDeath;
    public final Setting<Boolean> dumpOnThrashing;
    public final Setting<Boolean> dumpOnTaskLoop;
    public final Setting<Integer> maxDumpsPerSession;
    public final Setting<Integer> thrashSwitches;
    public final Setting<Integer> thrashWindowSeconds;
    public final Setting<Boolean> summaryInChat;
    public final Setting<Integer> inventorySnapshotSeconds;

    private DiagnosticsConfig(SettingsManager m) {
        decisionLog = Setting.bool(P + "decisionLog", true)
                .description("""
                        Write the decision log to altoclef/logs/<session>.jsonl (one JSON object per line):
                        chain switches with all priorities, task start/stop, deaths, commands. Used to find out
                        why the bot did something; see docs/task-architecture.md.""").register(m);
        logChainSwitches = Setting.bool(P + "logChainSwitches", true)
                .description("Decision log: every switch between task chains, with the priorities of all chains.").register(m);
        logTasks = Setting.bool(P + "logTasks", true)
                .description("Decision log: task start / stop / interrupt.").register(m);
        logEvents = Setting.bool(P + "logEvents", true)
                .description("Decision log: deaths, dimension changes, user commands, user task start / finish.").register(m);
        maxLogLinesPerSecond = Setting.integer(P + "maxLogLinesPerSecond", 50).range(1, 10_000)
                .description("At most this many decision log lines per second; the rest is counted as \"suppressed\".").register(m);
        keepSessions = Setting.integer(P + "keepSessions", 10).range(1, 1000)
                .description("How many sessions (log + dumps) to keep in altoclef/logs/. Older ones are deleted on world join.").register(m);
        flightRecorderSeconds = Setting.integer(P + "flightRecorderSeconds", 30).range(1, 600)
                .description("Length of the flight recorder (per-tick snapshots kept in memory and written as a dump).").register(m);
        dumpOnDeath = Setting.bool(P + "dumpOnDeath", true)
                .description("Write a flight recorder dump when the player dies.").register(m);
        dumpOnThrashing = Setting.bool(P + "dumpOnThrashing", true)
                .description("Write a flight recorder dump when the bot keeps switching between the same two chains.").register(m);
        dumpOnTaskLoop = Setting.bool(P + "dumpOnTaskLoop", true)
                .description("Write a flight recorder dump when the task path repeats itself (planning loop, e.g. tool → nether → tool).").register(m);
        maxDumpsPerSession = Setting.integer(P + "maxDumpsPerSession", 20).range(0, 1000)
                .description("At most this many dumps per session (automatic dumps also at most one per trigger and minute).").register(m);
        thrashSwitches = Setting.integer(P + "thrashSwitches", 6).range(2, 1000)
                .description("Thrashing = at least this many switches between the same two chains ...").register(m);
        thrashWindowSeconds = Setting.integer(P + "thrashWindowSeconds", 10).range(1, 600)
                .description("... within this many seconds.").register(m);
        inventorySnapshotSeconds = Setting.integer(P + "inventorySnapshotSeconds", 60).range(5, 3600)
                .description("Decision log: write the whole inventory (with durability and what was thrown away) every this many seconds.").register(m);
        summaryInChat = Setting.bool(P + "summaryInChat", true)
                .description("Show a short run summary in chat when a user task finishes or the player dies.").register(m);
    }

    public static synchronized DiagnosticsConfig get() {
        if (instance == null) {
            instance = new DiagnosticsConfig(SettingsService.manager());
        }
        return instance;
    }
}
