package adris.altoclef.butler;

import adris.altoclef.settings.Setting;
import adris.altoclef.settings.SettingsManager;
import adris.altoclef.settings.SettingsService;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;

public class ButlerConfig {

    private static final String P = "butler.";
    private static final ButlerConfig _instance = new ButlerConfig(SettingsService.manager());

    /**
     * If true, will use blacklist for rejecting users from using your player as a butler
     */
    public final Setting<Boolean> useButlerBlacklist;
    /**
     * If true, will use whitelist to only accept users from said whitelist.
     */
    public final Setting<Boolean> useButlerWhitelist;
    /**
     * Custom whisper formats the butler watches for, see the setting description.
     */
    public final Setting<List<String>> whisperFormats;
    public final Setting<Boolean> whisperFormatDebug;
    public final Setting<Boolean> sendAuthorizationResponse;
    public final Setting<String> failedAuthorizationResposne;
    public final Setting<Boolean> requirePrefixMsg;

    private ButlerConfig(SettingsManager m) {
        useButlerBlacklist = Setting.bool(P + "useButlerBlacklist", true)
                .description("Reject users on the blacklist from using your player as a butler.").register(m);
        useButlerWhitelist = Setting.bool(P + "useButlerWhitelist", false)
                .description("Only accept users on the whitelist.").register(m);
        whisperFormats = Setting.of(P + "whisperFormats", new TypeReference<List<String>>() {
                }, List.of("{from} {to} {message}"))
                .description("""
                        Whisper formats the butler accepts commands from (servers use different messaging plugins).
                        {from}: sender, {to}: receiver (ignored if not your username), {message}: the message.
                        WARNING: don't make this too lenient, only whispers should match, or others may control the bot.""").register(m);
        whisperFormatDebug = Setting.bool(P + "whisperFormatDebug", true)
                .description("Print which whispers were parsed and which failed (helps setting up whisperFormats).").register(m);
        sendAuthorizationResponse = Setting.bool(P + "sendAuthorizationResponse", true)
                .description("Answer unauthorized users with failedAuthorizationResposne. Disable to stay undercover.").register(m);
        failedAuthorizationResposne = Setting.string(P + "failedAuthorizationResposne", "Sorry {from} but you are not authorized!")
                .description("Answer to unauthorized users; {from} is their username.").register(m);
        requirePrefixMsg = Setting.bool(P + "requirePrefixMsg", false)
                .description("Require the command prefix in whispers, so normal messages are not taken as commands.").register(m);
    }

    public static ButlerConfig getInstance() {
        return _instance;
    }
}
