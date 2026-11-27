package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.args.SuggestingStringArg;
import adris.altoclef.commandsystem.exception.CommandException;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

/**
 * Baritone-style shortcuts for {@code set modified}, {@code set reset} and {@code set toggle}.
 */
public final class SettingShortcutCommands {

    private SettingShortcutCommands() {
    }

    /**
     * {@code modified [page]}: settings that differ from the defaults.
     */
    public static class Modified extends SettingsCommandBase {
        public Modified() {
            super(List.of("modified"), "List settings that differ from the defaults: modified [page]",
                    new SuggestingStringArg("page", "", Stream::empty));
        }

        @Override
        protected void call(AltoClef mod, ArgParser parser) throws CommandException {
            modified(mod, parser.get(String.class));
            finish();
        }
    }

    /**
     * {@code reset <name|all>}: remove values from the active profile.
     */
    public static class Reset extends SettingsCommandBase {
        public Reset() {
            super(List.of("reset"), "Reset a setting (or all) in the active profile: reset <name|all>",
                    new SuggestingStringArg("name", "", () -> Stream.concat(Stream.of("all"),
                            editor().nonDefault().stream().map(editor()::displayName))));
        }

        @Override
        protected void call(AltoClef mod, ArgParser parser) throws CommandException {
            String name = parser.get(String.class);
            try {
                if (name.isEmpty()) logUserError("Usage: " + prefix(mod) + "reset <name|all>");
                else reset(mod, name);
            } catch (IllegalArgumentException e) {
                logUserError(e.getMessage());
            } catch (IOException e) {
                logUserError("Could not save the profile: " + e.getMessage());
            }
            finish();
        }
    }

    /**
     * {@code toggle <name>}: flip a true/false setting.
     */
    public static class Toggle extends SettingsCommandBase {
        public Toggle() {
            super(List.of("toggle"), "Flip a true/false setting: toggle <name>",
                    new SuggestingStringArg("name", "", () -> editor().booleanNames().stream()));
        }

        @Override
        protected void call(AltoClef mod, ArgParser parser) throws CommandException {
            String name = parser.get(String.class);
            try {
                if (name.isEmpty()) logUserError("Usage: " + prefix(mod) + "toggle <name>");
                else toggle(name);
            } catch (IllegalArgumentException e) {
                logUserError(e.getMessage());
            } catch (IOException e) {
                logUserError("Could not save the profile: " + e.getMessage());
            }
            finish();
        }
    }
}
