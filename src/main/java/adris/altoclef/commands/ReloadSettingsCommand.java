package adris.altoclef.commands;

import adris.altoclef.settings.SettingsService;
import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.util.helpers.ConfigHelper;

public class ReloadSettingsCommand extends Command {
    public ReloadSettingsCommand() {
        super("reload_settings", "Reloads the settings profile and butler whitelist/blacklist.");
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) {
        SettingsService.load();
        ConfigHelper.reloadAllConfigs();
        mod.log("Reload successful!");
        finish();
    }
}