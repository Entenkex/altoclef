package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.Arg;
import adris.altoclef.commandsystem.args.SuggestingStringArg;
import adris.altoclef.settings.Setting;
import adris.altoclef.settings.SettingsEditor;
import adris.altoclef.settings.SettingsService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Shared chat output for the Baritone-style settings commands
 * ({@code set}, {@code modified}, {@code reset}, {@code toggle}).
 * Settings can be named by short name ({@code autoEat}) or full key ({@code core.autoEat}), ignoring case.
 */
abstract class SettingsCommandBase extends Command {

    static final int PAGE_SIZE = 12;

    // Chat colors (section sign codes)
    static final String KEY = "§f";
    static final String VALUE = "§b";
    static final String CHANGED = "§e";
    static final String DIM = "§7";
    static final String HEADER = "§a";

    SettingsCommandBase(List<String> names, String description, Arg<?>... args) {
        super(names, description, args);
    }

    static SettingsEditor editor() {
        return SettingsService.editor();
    }

    static String prefix(AltoClef mod) {
        return mod.getModSettings().getCommandPrefix();
    }

    // ------------------------------------------------------------------ actions

    void set(String name, String value) throws IOException {
        SettingsEditor.Change change = editor().set(name, value);
        logChange(change, "saved in profile");
        change.warnings().forEach(this::logUserError);
    }

    void reset(AltoClef mod, String name) throws IOException {
        SettingsEditor editor = editor();
        if (name.equalsIgnoreCase("all")) {
            int count = editor.resetAll();
            log("Removed " + count + " value(s) from profile \"" + editor.getActiveProfile() + "\"."
                    + (count == 0 ? "" : " Settings now come from the profile it extends / the defaults."));
            return;
        }
        logChange(editor.reset(name), "removed from profile");
    }

    void toggle(String name) throws IOException {
        logChange(editor().toggle(name), "saved in profile");
    }

    private void logChange(SettingsEditor.Change change, String what) {
        SettingsEditor editor = editor();
        String display = editor.find(change.key()).map(editor::displayName).orElse(change.key());
        log(KEY + display + DIM + " = " + VALUE + change.value() + DIM + " (" + what + " \"" + editor.getActiveProfile() + "\")");
    }

    // ------------------------------------------------------------------ display

    /**
     * Shows one setting in detail, or all settings of a group ({@code core}).
     */
    void show(AltoClef mod, String name) {
        SettingsEditor editor = editor();
        Optional<Setting<?>> exact = editor.find(name);
        if (exact.isPresent()) {
            Setting<?> setting = exact.get();
            List<String> description = editor.describe(setting);
            List<String> lines = new ArrayList<>();
            if (!editor.displayName(setting).equals(setting.getKey())) {
                lines.add(DIM + "  full name: " + setting.getKey());
            }
            for (int i = 1; i < description.size(); i++) {
                lines.add(DIM + "  " + description.get(i));
            }
            String head = description.get(0);
            String rest = head.substring(head.indexOf(" = ") + 3);
            logLines(KEY + editor.displayName(setting) + DIM + " = " + VALUE + rest, lines);
            return;
        }
        List<Setting<?>> group = editor.findByPrefix(name);
        if (!group.isEmpty()) {
            listPage(mod, name + " (group)", group, 1, "set " + name);
            return;
        }
        String lower = name.toLowerCase();
        List<String> similar = editor.names().stream().filter(k -> k.toLowerCase().contains(lower)).limit(10).toList();
        logUserError("Unknown setting: " + name + (similar.isEmpty() ? "" : ". Did you mean: " + String.join(", ", similar)));
    }

    /**
     * One page of settings with their values. Changed ones are yellow with a star.
     *
     * @param pageCommand command (without prefix and page number) that shows other pages
     */
    void listPage(AltoClef mod, String title, List<Setting<?>> settings, int page, String pageCommand) {
        SettingsEditor editor = editor();
        int pages = Math.max(1, (settings.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page < 1 || page > pages) {
            logUserError("Page " + page + " does not exist (1-" + pages + ")");
            return;
        }
        List<String> lines = new ArrayList<>();
        int changed = 0;
        for (Setting<?> setting : settings) {
            if (!setting.isDefault()) changed++;
        }
        int from = (page - 1) * PAGE_SIZE;
        for (Setting<?> setting : settings.subList(from, Math.min(settings.size(), from + PAGE_SIZE))) {
            boolean isDefault = setting.isDefault();
            lines.add("  " + KEY + editor.displayName(setting) + DIM + " = "
                    + (isDefault ? VALUE : CHANGED) + shorten(editor.format(setting)) + (isDefault ? "" : " *"));
        }
        String p = prefix(mod);
        StringBuilder footer = new StringBuilder(DIM);
        if (pages > 1) {
            footer.append("Page ").append(page).append("/").append(pages);
            if (page < pages) footer.append(" - next: ").append(p).append(pageCommand).append(" ").append(page + 1);
            footer.append(". ");
        }
        footer.append("Details: ").append(p).append("set <name>");
        lines.add(footer.toString());
        logLines(HEADER + title + DIM + " - " + settings.size() + " settings, " + changed + " changed"
                + DIM + " (profile \"" + editor.getActiveProfile() + "\")", lines);
    }

    /**
     * {@code modified [page]}: settings that differ from the defaults.
     */
    void modified(AltoClef mod, String arg) {
        SettingsEditor editor = editor();
        int page = parsePage(arg);
        if (page < 0) {
            logUserError("Not a page number: " + arg);
            return;
        }
        List<Setting<?>> changed = editor.nonDefault();
        if (changed.isEmpty()) {
            log("No settings changed in profile \"" + editor.getActiveProfile() + "\" - everything is at its default.");
            return;
        }
        listPage(mod, "Modified settings", changed, page, "modified");
    }

    static int parsePage(String text) {
        if (text.isEmpty()) return 1;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Keeps listings readable: long values (e.g. item lists) are cut off.
     */
    static String shorten(String value) {
        int max = 60;
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    // ------------------------------------------------------------------ tab completion

    static Stream<String> nameSuggestions() {
        return editor().names().stream();
    }

    /**
     * Word argument that remembers what it parsed last, so the next argument can suggest
     * values that fit (tab completion parses the arguments left to right).
     */
    static final class RememberingArg extends SuggestingStringArg {
        private String last = "";

        RememberingArg(String name, Supplier<Stream<String>> suggestions) {
            super(name, "", suggestions);
        }

        String last() {
            return last;
        }

        @Override
        protected StringParser<String> getParser() {
            StringParser<String> parser = super.getParser();
            return reader -> {
                last = "";
                String value = parser.parse(reader);
                last = value;
                return value;
            };
        }
    }
}
