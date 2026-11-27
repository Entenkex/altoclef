package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.args.RestOfLineArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.settings.Setting;
import adris.altoclef.settings.SettingsEditor;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Shows and changes settings of the active profile, similar to Baritone's {@code #set}.
 * Names can be short ({@code autoEat}) or full ({@code core.autoEat}), case does not matter.
 * <pre>
 * set                       all settings, page 1
 * set list [page|text]      all settings (page), or those whose name contains the text
 * set modified [page]       settings that differ from the defaults
 * set &lt;group&gt;               all settings of a group, e.g. "set core"
 * set &lt;name&gt;                value, default, limits, description
 * set &lt;name&gt; &lt;value&gt;        set and save into the active profile
 * set reset &lt;name|all&gt;      remove from the profile (back to inherited / default)
 * set toggle &lt;name&gt;         flip a true/false setting
 * </pre>
 * {@code modified}, {@code reset} and {@code toggle} also exist as commands of their own.
 */
public class SettingCommand extends SettingsCommandBase {

    private static final List<String> SUBCOMMANDS = List.of("list", "modified", "reset", "toggle");

    private final RememberingArg first;

    public SettingCommand() {
        this(new RememberingArg("name", SettingCommand::firstSuggestions));
    }

    private SettingCommand(RememberingArg first) {
        super(List.of("set", "setting"), "Show or change settings (like Baritone): set [name [value]] | set list|modified|reset|toggle ...",
                first, new RestOfLineArg("value", "", () -> secondSuggestions(first.last())));
        this.first = first;
    }

    private static Stream<String> firstSuggestions() {
        return Stream.of(SUBCOMMANDS.stream(), editor().groups().stream(), nameSuggestions()).flatMap(s -> s);
    }

    private static Stream<String> secondSuggestions(String first) {
        SettingsEditor editor = editor();
        switch (first.toLowerCase()) {
            case "reset":
                return Stream.concat(Stream.of("all"), editor.nonDefault().stream().map(editor::displayName));
            case "toggle":
                return editor.booleanNames().stream();
            case "list":
                return editor.groups().stream();
            case "modified":
                return Stream.empty();
            default:
                try {
                    Optional<Setting<?>> setting = editor.find(first);
                    if (setting.isEmpty()) return Stream.empty();
                    if (setting.get().getType() == Boolean.class) return Stream.of("true", "false", "reset");
                    return Stream.of(editor.format(setting.get()), "reset");
                } catch (IllegalArgumentException ambiguous) {
                    return Stream.empty();
                }
        }
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String name = parser.get(String.class);
        String value = parser.get(String.class);
        SettingsEditor editor = editor();

        try {
            switch (name.toLowerCase()) {
                case "", "list" -> list(mod, value);
                case "modified" -> modified(mod, value);
                case "reset" -> {
                    if (value.isEmpty()) logUserError("Usage: " + prefix(mod) + "set reset <name|all>");
                    else reset(mod, value);
                }
                case "toggle" -> {
                    if (value.isEmpty()) logUserError("Usage: " + prefix(mod) + "set toggle <name>");
                    else toggle(value);
                }
                default -> {
                    if (value.isEmpty()) show(mod, name);
                    else if (value.equalsIgnoreCase("reset")) reset(mod, name);
                    else set(name, value);
                }
            }
        } catch (IllegalArgumentException e) {
            logUserError(e.getMessage());
        } catch (IOException e) {
            logUserError("Could not save the profile: " + e.getMessage());
        }
        finish();
    }

    private void list(AltoClef mod, String arg) {
        SettingsEditor editor = editor();
        int page = parsePage(arg);
        if (page > 0) {
            listPage(mod, "Settings", editor.all(), page, "set list");
            return;
        }
        // "list core" or "list core 2": filter by text, optional page at the end
        String filter = arg;
        int filterPage = 1;
        int space = arg.lastIndexOf(' ');
        if (space > 0 && parsePage(arg.substring(space + 1)) > 0) {
            filter = arg.substring(0, space).strip();
            filterPage = parsePage(arg.substring(space + 1));
        }
        List<Setting<?>> found = editor.search(filter);
        if (found.isEmpty()) {
            logUserError("No setting contains \"" + filter + "\"");
            return;
        }
        listPage(mod, "Settings containing \"" + filter + "\"", found, filterPage, "set list " + filter);
    }
}
