package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.RestOfLineArg;
import adris.altoclef.commandsystem.args.SuggestingStringArg;
import adris.altoclef.commandsystem.exception.CommandException;
import adris.altoclef.settings.Setting;
import adris.altoclef.settings.SettingsEditor;
import adris.altoclef.settings.SettingsService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Shows and changes settings of the active profile.
 * <pre>
 * setting                         changed settings of the active profile
 * setting core                    all settings starting with "core."
 * setting core.autoEat            value, default, limits, description
 * setting core.autoEat false      set and save into the active profile
 * setting core.autoEat reset      remove from the profile (back to inherited / default)
 * </pre>
 */
public class SettingCommand extends Command {

    private static final String RESET = "reset";

    public SettingCommand() {
        super("setting", "Show or change settings: setting [key] [value|reset]",
                new SuggestingStringArg("key", "", SettingCommand::keySuggestions),
                new RestOfLineArg("value", "", () -> Stream.of(RESET, "true", "false")));
    }

    private static Stream<String> keySuggestions() {
        List<String> keys = SettingsService.editor().keys();
        Stream<String> groups = keys.stream().map(k -> k.substring(0, k.indexOf('.') < 0 ? k.length() : k.indexOf('.'))).distinct();
        return Stream.concat(groups, keys.stream());
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        String key = parser.get(String.class);
        String value = parser.get(String.class);
        SettingsEditor editor = SettingsService.editor();

        try {
            if (key.isEmpty()) {
                showChanged(editor);
            } else if (value.isEmpty()) {
                show(editor, key);
            } else if (value.equalsIgnoreCase(RESET)) {
                SettingsEditor.Change change = editor.reset(key);
                log(KEY + change.key() + DIM + " = " + VALUE + change.value() + DIM + " (removed from profile \"" + editor.getActiveProfile() + "\")");
            } else {
                SettingsEditor.Change change = editor.set(key, value);
                log(KEY + change.key() + DIM + " = " + VALUE + change.value() + DIM + " (saved in profile \"" + editor.getActiveProfile() + "\")");
                change.warnings().forEach(this::logUserError);
            }
        } catch (IllegalArgumentException e) {
            logUserError(e.getMessage());
        } catch (IOException e) {
            logUserError("Could not save the profile: " + e.getMessage());
        }
        finish();
    }

    // Chat colors (section sign codes)
    private static final String KEY = "\u00A7f";
    private static final String VALUE = "\u00A7b";
    private static final String CHANGED = "\u00A7e";
    private static final String DIM = "\u00A77";
    private static final String HEADER = "\u00A7a";

    private void showChanged(SettingsEditor editor) {
        List<Setting<?>> changed = editor.nonDefault();
        List<String> lines = new ArrayList<>();
        for (Setting<?> setting : changed) {
            lines.add("  " + KEY + setting.getKey() + DIM + " = " + CHANGED + shorten(editor.format(setting)));
        }
        lines.add(DIM + "setting <key|group> [value|reset]  -  groups: " + String.join(", ", groups(editor)));
        logLines(HEADER + "Profile \"" + editor.getActiveProfile() + "\": " + changed.size() + " of "
                + editor.keys().size() + " settings changed", lines);
    }

    private void show(SettingsEditor editor, String key) {
        var exact = editor.find(key);
        if (exact.isPresent()) {
            List<String> description = editor.describe(exact.get());
            List<String> lines = new ArrayList<>();
            for (int i = 1; i < description.size(); i++) {
                lines.add(DIM + "  " + description.get(i));
            }
            String head = description.get(0);
            int eq = head.indexOf(" = ");
            logLines(KEY + head.substring(0, eq) + DIM + " = " + VALUE + head.substring(eq + 3), lines);
            return;
        }
        List<Setting<?>> group = editor.findByPrefix(key);
        if (!group.isEmpty()) {
            String prefix = key.endsWith(".") ? key : key + ".";
            List<String> lines = new ArrayList<>();
            int changed = 0;
            for (Setting<?> setting : group) {
                boolean isDefault = setting.isDefault();
                if (!isDefault) changed++;
                lines.add("  " + KEY + setting.getKey().substring(prefix.length()) + DIM + " = "
                        + (isDefault ? VALUE : CHANGED) + shorten(editor.format(setting)) + (isDefault ? "" : " *"));
            }
            lines.add(DIM + CHANGED + "*" + DIM + " = changed in this profile. Full value and help: setting " + prefix + "<name>");
            logLines(HEADER + prefix.substring(0, prefix.length() - 1) + DIM + " (" + group.size() + " settings, "
                    + changed + " changed)", lines);
            return;
        }
        String lower = key.toLowerCase();
        List<String> similar = editor.keys().stream().filter(k -> k.toLowerCase().contains(lower)).limit(10).toList();
        logUserError("Unknown setting: " + key + (similar.isEmpty() ? "" : ". Did you mean: " + String.join(", ", similar)));
    }

    private static List<String> groups(SettingsEditor editor) {
        return editor.keys().stream().map(k -> k.substring(0, k.indexOf('.') < 0 ? k.length() : k.indexOf('.'))).distinct().toList();
    }

    /**
     * Keeps group listings readable: long values (e.g. item lists) are cut off.
     */
    private static String shorten(String value) {
        int max = 60;
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
