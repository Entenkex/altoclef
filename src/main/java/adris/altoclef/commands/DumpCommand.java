package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.diagnostics.Diagnostics;
import adris.altoclef.diagnostics.DumpLimiter;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Writes the flight recorder (the last seconds, per tick) to {@code altoclef/logs/}, see docs/task-architecture.md 2.6.
 */
public class DumpCommand extends Command {

    public DumpCommand() {
        super("dump", "Write the last seconds of bot decisions (flight recorder) to altoclef/logs");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) {
        Optional<Diagnostics> diagnostics = Diagnostics.get();
        if (diagnostics.isEmpty() || diagnostics.get().currentLogFile().isEmpty()) {
            mod.log("Decision log is off (debug.decisionLog) or not in a world, nothing to dump.");
        } else {
            Optional<Path> file = diagnostics.get().dump(DumpLimiter.MANUAL, "@dump");
            mod.log(file.map(f -> "Dump written: " + f)
                    .orElse("No dump written: limit of dumps per session reached (debug.maxDumpsPerSession)."));
        }
        finish();
    }
}
