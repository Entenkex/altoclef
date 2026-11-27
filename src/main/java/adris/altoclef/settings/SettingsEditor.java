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
    public Change set(String name, String raw) throws IOException {
        Setting<?> setting = require(name);
        String key = setting.getKey();
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
    public Change reset(String name) throws IOException {
        Setting<?> setting = require(name);
        store.setValue(store.getActiveProfile(), setting.getKey(), null);
        reload.run();
        return new Change(setting.getKey(), format(setting), List.of());
    }

    /**
     * Flips a true/false setting and saves it.
     */
    public Change toggle(String name) throws IOException {
        Setting<?> setting = require(name);
        if (setting.getType() != Boolean.class) {
            throw new IllegalArgumentException(setting.getKey() + " is not a true/false setting");
        }
        return set(setting.getKey(), String.valueOf(!((Boolean) setting.get())));
    }

    /**
     * Removes all values from the active profile file (the profile it extends stays untouched).
     *
     * @return how many values were removed
     */
    public int resetAll() throws IOException {
        String profile = store.getActiveProfile();
        List<String> ignored = new ArrayList<>();
        int count = countLeaves(store.resolveOwn(profile, ignored));
        store.writeProfile(profile, store.getParent(profile), manager.getMapper().createObjectNode());
        reload.run();
        return count;
    }

    private static int countLeaves(JsonNode node) {
        if (node == null || !node.isObject()) return node == null ? 0 : 1;
        int count = 0;
        for (JsonNode child : node) count += countLeaves(child);
        return count;
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

    /**
     * Finds a setting by full key ({@code core.autoEat}) or short name ({@code autoEat}), ignoring case.
     *
     * @throws IllegalArgumentException if a short name matches settings in more than one group
     */
    public Optional<Setting<?>> find(String name) {
        Optional<Setting<?>> exact = manager.find(name);
        if (exact.isPresent()) return exact;
        List<Setting<?>> matches = new ArrayList<>();
        for (Setting<?> setting : manager.getAll()) {
            if (setting.getKey().equalsIgnoreCase(name)) return Optional.of(setting);
            if (shortOf(setting.getKey()).equalsIgnoreCase(name)) matches.add(setting);
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("\"" + name + "\" is ambiguous, use the full name: "
                    + String.join(", ", matches.stream().map(Setting::getKey).sorted().toList()));
        }
        return matches.stream().findFirst();
    }

    /**
     * Name to show and type in chat: the short name if it is unique, else the full key.
     */
    public String displayName(Setting<?> setting) {
        String shortName = shortOf(setting.getKey());
        long same = manager.getAll().stream().filter(s -> shortOf(s.getKey()).equalsIgnoreCase(shortName)).count();
        return same == 1 ? shortName : setting.getKey();
    }

    /**
     * All names that can be typed: unique short names and full keys, sorted.
     */
    public List<String> names() {
        List<String> names = new ArrayList<>();
        for (Setting<?> setting : manager.getAll()) {
            String display = displayName(setting);
            names.add(display);
            if (!display.equals(setting.getKey())) names.add(setting.getKey());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /**
     * Names of all true/false settings (for tab completion of toggle).
     */
    public List<String> booleanNames() {
        return manager.getAll().stream().filter(s -> s.getType() == Boolean.class)
                .map(this::displayName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public List<String> groups() {
        return keys().stream().map(k -> k.indexOf('.') < 0 ? k : k.substring(0, k.indexOf('.'))).distinct().toList();
    }

    /**
     * Settings whose name or description contains {@code text}, ignoring case.
     */
    public List<Setting<?>> search(String text) {
        String lower = text.toLowerCase();
        List<Setting<?>> result = new ArrayList<>();
        for (Setting<?> setting : manager.getAll()) {
            if (setting.getKey().toLowerCase().contains(lower)) result.add(setting);
        }
        result.sort((a, b) -> a.getKey().compareTo(b.getKey()));
        return result;
    }

    public List<Setting<?>> all() {
        List<Setting<?>> result = new ArrayList<>(manager.getAll());
        result.sort((a, b) -> a.getKey().compareTo(b.getKey()));
        return result;
    }

    private static String shortOf(String key) {
        int dot = key.lastIndexOf('.');
        return dot < 0 ? key : key.substring(dot + 1);
    }

    /**
     * All settings whose key starts with {@code prefix} (e.g. "core" or "beatMinecraft.").
     */
    public List<Setting<?>> findByPrefix(String prefix) {
        String normalized = (prefix.endsWith(".") ? prefix : prefix + ".").toLowerCase();
        List<Setting<?>> result = new ArrayList<>();
        for (Setting<?> setting : manager.getAll()) {
            if (setting.getKey().toLowerCase().startsWith(normalized)) result.add(setting);
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

    private Setting<?> require(String name) {
        return find(name).orElseThrow(() -> new IllegalArgumentException("Unknown setting: " + name));
    }

    private static String unquote(String text) {
        if (text.length() >= 2 && ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'")))) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }
}
