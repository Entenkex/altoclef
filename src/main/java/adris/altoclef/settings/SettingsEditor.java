package adris.altoclef.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Logic behind the in-game commands {@code @setting} and {@code @profile} (and later the GUI):
 * reading, changing and saving single values and switching profiles.
 * <p>
 * Changes are written into the active profile file and then reloaded, so the file stays the single
 * source of truth. Does not depend on Minecraft.
 */
public final class SettingsEditor {

    private final SettingsManager manager;
    private final ProfileStore store;
    private final Runnable reload;

    /**
     * @param reload reloads the active profile into the manager (normally {@link SettingsService#load()}).
     */
    public SettingsEditor(SettingsManager manager, ProfileStore store, Runnable reload) {
        this.manager = manager;
        this.store = store;
        this.reload = reload;
    }

    /**
     * Result of a change: the value that is now active and warnings (e.g. clamped to max).
     */
    public record Change(String key, String value, List<String> warnings) {
    }

    // ------------------------------------------------------------------ values

    /**
     * Turns the text typed in chat into a value node.
     * Text settings take the text as is (surrounding quotes are removed, so leading/trailing spaces are possible).
     * Everything else is parsed as YAML: {@code 5}, {@code true}, {@code SMART}, {@code [coal, charcoal]},
     * {@code "1, 64, 2"}.
     */
    public JsonNode parseValue(Setting<?> setting, String raw) {
        ObjectMapper mapper = manager.getMapper();
        String text = raw.strip();
        if (setting.getType() == String.class) {
            return mapper.getNodeFactory().textNode(unquote(text));
        }
        try {
            JsonNode node = mapper.readTree(text);
            if (node != null && !node.isMissingNode()) return node;
        } catch (JsonProcessingException ignored) {
            // fall through: treat as plain text, the setting decides whether it is valid
        }
        return mapper.getNodeFactory().textNode(unquote(text));
    }

    /**
     * Sets a value in the active profile file and reloads.
     */
    public Change set(String key, String raw) throws IOException {
        Setting<?> setting = require(key);
        JsonNode node = parseValue(setting, raw);

        SettingsManager.Preview preview = manager.preview(key, node);
        if (preview.rejected()) {
            // Wrong type or failed check: don't write anything.
            throw new IllegalArgumentException(preview.warning().replaceFirst(", using default .*$", "") + " - not saved");
        }
        List<String> warnings = new ArrayList<>();
        if (preview.warning() != null) warnings.add(preview.warning());

        // Store what is actually used (e.g. the clamped value), not the raw input.
        store.setValue(store.getActiveProfile(), key, preview.storedValue());
        reload.run();
        return new Change(key, format(setting), warnings);
    }

    /**
     * Removes the value from the active profile, so it falls back to the profile it extends / the default.
     */
    public Change reset(String key) throws IOException {
        Setting<?> setting = require(key);
        store.setValue(store.getActiveProfile(), key, null);
        reload.run();
        return new Change(key, format(setting), List.of());
    }

    // ------------------------------------------------------------------ profiles

    public String getActiveProfile() {
        return store.getActiveProfile();
    }

    public List<String> listProfiles() throws IOException {
        return store.listProfiles();
    }

    public void switchProfile(String name) throws IOException {
        if (!ProfileStore.isValidName(name)) {
            throw new IllegalArgumentException("Invalid profile name \"" + name + "\" (letters, digits, _ and - only)");
        }
        if (!store.exists(name)) {
            throw new IllegalArgumentException("Profile \"" + name + "\" does not exist. Create it with: profile new " + name);
        }
        store.setActiveProfile(name);
        reload.run();
    }

    /**
     * Creates an empty profile that extends {@code parent} and switches to it.
     */
    public void createProfile(String name, String parent) throws IOException {
        if (!ProfileStore.isValidName(name)) {
            throw new IllegalArgumentException("Invalid profile name \"" + name + "\" (letters, digits, _ and - only)");
        }
        if (store.exists(name)) {
            throw new IllegalArgumentException("Profile \"" + name + "\" already exists");
        }
        if (parent != null && !store.exists(parent)) {
            throw new IllegalArgumentException("Profile \"" + parent + "\" does not exist");
        }
        store.writeProfile(name, parent, manager.getMapper().createObjectNode());
        switchProfile(name);
    }

    // ------------------------------------------------------------------ display

    public Optional<Setting<?>> find(String key) {
        return manager.find(key);
    }

    /**
     * All settings whose key starts with {@code prefix} (e.g. "core" or "beatMinecraft.").
     */
    public List<Setting<?>> findByPrefix(String prefix) {
        String normalized = prefix.endsWith(".") ? prefix : prefix + ".";
        List<Setting<?>> result = new ArrayList<>();
        for (Setting<?> setting : manager.getAll()) {
            if (setting.getKey().startsWith(normalized)) result.add(setting);
        }
        result.sort((a, b) -> a.getKey().compareTo(b.getKey()));
        return result;
    }

    public List<Setting<?>> nonDefault() {
        List<Setting<?>> result = new ArrayList<>();
        for (Setting<?> setting : manager.getAll()) {
            if (!setting.isDefault()) result.add(setting);
        }
        result.sort((a, b) -> a.getKey().compareTo(b.getKey()));
        return result;
    }

    public List<String> keys() {
        return manager.getAll().stream().map(Setting::getKey).sorted().toList();
    }

    /**
     * Current value as short text (same notation as in the profile files).
     */
    public String format(Setting<?> setting) {
        return formatNode(manager.toNode(setting));
    }

    public String formatDefault(Setting<?> setting) {
        return formatNode(manager.defaultToNode(setting));
    }

    /**
     * Multi-line description: value, default, limits and help text.
     */
    public List<String> describe(Setting<?> setting) {
        List<String> lines = new ArrayList<>();
        lines.add(setting.getKey() + " = " + format(setting) + (setting.isDefault() ? " (default)" : ""));
        StringBuilder meta = new StringBuilder("default: ").append(formatDefault(setting));
        setting.getMin().ifPresent(min -> meta.append(", min: ").append(min));
        setting.getMax().ifPresent(max -> meta.append(", max: ").append(max));
        lines.add(meta.toString());
        for (String line : setting.getDescription().split("\\R")) {
            if (!line.isBlank()) lines.add(line.strip());
        }
        return lines;
    }

    private String formatNode(JsonNode node) {
        if (node == null || node.isNull()) return "null";
        if (node.isTextual()) return "\"" + node.asText() + "\"";
        return node.toString();
    }

    private Setting<?> require(String key) {
        return manager.find(key).orElseThrow(() -> new IllegalArgumentException("Unknown setting: " + key));
    }

    private static String unquote(String text) {
        if (text.length() >= 2 && ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'")))) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }
}
