package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.SuggestingStringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.settings.SettingsEditor;
import adris.altoclef.settings.SettingsService;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

/**
 * Lists, switches and creates settings profiles.
 * <pre>
 * profile              active profile and all profiles
 * profile speedrun     switch to profiles/speedrun.yml
 * profile new pvp      create profiles/pvp.yml (extends the active profile) and switch to it
 * </pre>
 */
public class ProfileCommand extends Command {

    private static final String NEW = "new";

    public ProfileCommand() {
        super("profile", "Show, switch or create settings profiles: profile [name | new <name>]",
                new SuggestingStringArg("name", "", ProfileCommand::profileSuggestions),
                new SuggestingStringArg("newName", "", Stream::empty));
    }

    private static Stream<String> profileSuggestions() {
        try {
            return Stream.concat(Stream.of(NEW), SettingsService.editor().listProfiles().stream());
        } catch (IOException e) {
            return Stream.of(NEW);
        }
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String name = parser.get(String.class);
        String newName = parser.get(String.class);
        SettingsEditor editor = SettingsService.editor();

        try {
            if (name.isEmpty()) {
                List<String> profiles = editor.listProfiles();
                String active = editor.getActiveProfile();
                List<String> lines = new java.util.ArrayList<>();
                for (String profile : profiles) {
                    lines.add(profile.equals(active) ? "  \u00A7e> " + profile + " \u00A77(active)" : "  \u00A7f" + profile);
                }
                lines.add("\u00A77profile <name> to switch, profile new <name> to create one");
                logLines("\u00A7aProfiles", lines);
            } else if (name.equalsIgnoreCase(NEW)) {
                if (newName.isEmpty()) {
                    logUserError("Usage: profile new <name>");
                } else {
                    String parent = editor.getActiveProfile();
                    editor.createProfile(newName, parent);
                    log("Created profile \"" + newName + "\" (extends \"" + parent + "\") and switched to it.");
                }
            } else {
                editor.switchProfile(name);
                log("Switched to profile \"" + name + "\".");
            }
        } catch (IllegalArgumentException e) {
            logUserError(e.getMessage());
        } catch (IOException e) {
            logUserError("Could not change profiles: " + e.getMessage());
        }
        finish();
    }
}
