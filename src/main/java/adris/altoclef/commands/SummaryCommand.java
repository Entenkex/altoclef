package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.diagnostics.Diagnostics;

import java.util.Optional;

/**
 * Summary of the current run (time per chain, switches, deaths), also written to the decision log.
 */
public class SummaryCommand extends Command {

    public SummaryCommand() {
        super("summary", "Summary of the current run: time per task chain, switches, deaths");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) {
        Optional<Diagnostics> diagnostics = Diagnostics.get();
        if (diagnostics.isEmpty()) {
            mod.log("Decision log is not initialized.");
        } else {
            diagnostics.get().writeSummary("@summary");
            String file = diagnostics.get().currentLogFile().map(f -> " (log: " + f + ")").orElse("");
            Debug.logLines("Run summary" + file + ":", diagnostics.get().summaryLines());
        }
        finish();
    }
}
