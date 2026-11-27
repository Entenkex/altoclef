package adris.altoclef.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.function.Consumer;

/**
 * Central place for all settings.
 * <p>
 * Every part of the mod (core, BeatMinecraft, butler, single tasks, ...) registers its {@link Setting}s here,
 * using a key prefix like {@code core.}, {@code beatMinecraft.} or {@code task.collectFood.}.
 * The manager fills them from the active profile's values.
 * <p>
 * Settings may be registered at any time: one that registers after the profile was loaded
 * (e.g. a task that is created later) gets its profile value immediately.
 * <p>
 * Profile values are looked up either nested ({@code core: {commandPrefix: "@"}}) or as a flat key
 * ({@code core.commandPrefix: "@"}).
 * <p>
 * Does not depend on Minecraft; the {@link ObjectMapper} passed in decides which types can be read.
 */
public final class SettingsManager {

    private final ObjectMapper mapper;
    private final Consumer<String> warningSink;
    private final Map<String, Setting<?>> settings = new LinkedHashMap<>();
    private final List<Runnable> reloadListeners = new ArrayList<>();
    private ObjectNode values;

    public SettingsManager(ObjectMapper mapper, Consumer<String> warningSink) {
        this.mapper = mapper;
        this.warningSink = warningSink;
        this.values = mapper.createObjectNode();
    }

    public ObjectMapper getMapper() {
        return mapper;
    }

    // ------------------------------------------------------------------ registration

    /**
     * Registers a setting and fills it from the currently loaded profile values.
     *
     * @throws IllegalArgumentException if another setting already uses the key.
     */
    public synchronized <T> Setting<T> register(Setting<T> setting) {
        Setting<?> existing = settings.putIfAbsent(setting.getKey(), setting);
        if (existing != null) {
            throw new IllegalArgumentException("Setting key registered twice: " + setting.getKey());
        }
        apply(setting).forEach(this::warnSafely);
        return setting;
    }

    public Collection<Setting<?>> getAll() {
        return Collections.unmodifiableCollection(settings.values());
    }

    public Optional<Setting<?>> find(String key) {
        return Optional.ofNullable(settings.get(key));
    }

    /**
     * Adds a listener that runs after every {@link #load}. Use it for side effects of settings
     * (e.g. passing values on to baritone).
     */
    public synchronized void addReloadListener(Runnable listener) {
        reloadListeners.add(listener);
    }

    // ------------------------------------------------------------------ loading

    /**
     * Replaces the profile values: every setting is reset to its default and then set from {@code newValues}.
     *
     * @return warnings for values that were clamped, invalid or unknown.
     */
    public List<String> load(ObjectNode newValues) {
        List<String> warnings = new ArrayList<>();
        List<Runnable> listeners;
        synchronized (this) {
            values = newValues == null ? mapper.createObjectNode() : newValues.deepCopy();
            for (Setting<?> setting : settings.values()) {
                warnings.addAll(apply(setting));
            }
            listeners = new ArrayList<>(reloadListeners);
        }
        // A failing warning output or listener must never abort loading the other settings / listeners.
        warnings.forEach(this::warnSafely);
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                warnSafely("Settings reload listener failed: " + e);
            }
        }
        return warnings;
    }

    /**
     * Sets one value at runtime (e.g. from a command). The value is parsed like a profile value.
     *
     * @return a warning if the value was clamped or invalid.
     */
    public synchronized Optional<String> set(String key, JsonNode value) {
        Setting<?> setting = settings.get(key);
        if (setting == null) {
            return Optional.of("Unknown setting: " + key);
        }
        List<String> warnings = applyNode(setting, value);
        return warnings.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", warnings));
    }

    /**
     * What setting {@code key} to {@code value} would result in, without changing anything.
     */
    public record Preview(JsonNode storedValue, String warning, boolean rejected) {
    }

    public synchronized Preview preview(String key, JsonNode value) {
        Setting<?> setting = settings.get(key);
        if (setting == null) {
            return new Preview(null, "Unknown setting: " + key, true);
        }
        return previewTyped(setting, value);
    }

    private <T> Preview previewTyped(Setting<T> setting, JsonNode node) {
        T parsed;
        try {
            parsed = mapper.convertValue(node, mapper.constructType(setting.getType()));
        } catch (IllegalArgumentException e) {
            return new Preview(null, setting.getKey() + ": invalid value " + node + " (" + explain(setting, e) + ")", true);
        }
        Setting.Resolved<T> resolved = setting.resolve(parsed);
        return new Preview(mapper.valueToTree(resolved.value()), resolved.warning(), resolved.rejected());
    }

    /**
     * Keys in the loaded profile that no registered setting uses (typos, removed settings,
     * or settings of a part that has not registered yet).
     */
    public synchronized List<String> getUnknownKeys() {
        List<String> unknown = new ArrayList<>();
        collectUnknown("", values, unknown);
        return unknown;
    }

    // ------------------------------------------------------------------ export

    public JsonNode toNode(Setting<?> setting) {
        return mapper.valueToTree(setting.get());
    }

    public JsonNode defaultToNode(Setting<?> setting) {
        return mapper.valueToTree(setting.getDefault());
    }

    /**
     * All settings whose value differs from the default, as nested tree (like a profile file).
     */
    public synchronized ObjectNode nonDefaultValues() {
        ObjectNode root = mapper.createObjectNode();
        for (Setting<?> setting : settings.values()) {
            if (!setting.isDefault()) {
                putNested(root, setting.getKey(), toNode(setting));
            }
        }
        return root;
    }

    public static void putNested(ObjectNode root, String key, JsonNode value) {
        String[] parts = key.split("\\.");
        ObjectNode current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonNode child = current.get(parts[i]);
            if (!(child instanceof ObjectNode)) {
                child = current.putObject(parts[i]);
            }
            current = (ObjectNode) child;
        }
        current.set(parts[parts.length - 1], value);
    }

    // ------------------------------------------------------------------ internals

    private void warnSafely(String warning) {
        try {
            warningSink.accept(warning);
        } catch (RuntimeException e) {
            System.err.println("[AltoClef settings] " + warning + " (warning output failed: " + e + ")");
        }
    }

    private List<String> apply(Setting<?> setting) {
        setting.reset();
        JsonNode node = lookup(setting.getKey());
        if (node == null) return List.of();
        return applyNode(setting, node);
    }

    private <T> List<String> applyNode(Setting<T> setting, JsonNode node) {
        T parsed;
        try {
            parsed = mapper.convertValue(node, mapper.constructType(setting.getType()));
        } catch (IllegalArgumentException e) {
            setting.reset();
            return List.of(setting.getKey() + ": invalid value " + node + " (" + explain(setting, e) + "), using default " + setting.getDefault());
        }
        return setting.set(parsed).map(List::of).orElse(List.of());
    }

    private JsonNode lookup(String key) {
        JsonNode flat = values.get(key);
        if (flat != null) return flat;
        JsonNode current = values;
        for (String part : key.split("\\.")) {
            if (current == null || !current.isObject()) return null;
            current = current.get(part);
        }
        return current;
    }

    private void collectUnknown(String path, JsonNode node, List<String> unknown) {
        if (!path.isEmpty() && settings.containsKey(path)) return;
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                collectUnknown(path.isEmpty() ? field.getKey() : path + "." + field.getKey(), field.getValue(), unknown);
            }
        } else {
            unknown.add(path);
        }
    }

    /**
     * Short, user-readable reason why a value could not be read, e.g. "expected a whole number".
     */
    static String explain(Setting<?> setting, Throwable e) {
        java.lang.reflect.Type type = setting.getType();
        if (type == Integer.class || type == Long.class || type == Short.class) return "expected a whole number";
        if (type == Float.class || type == Double.class) return "expected a number";
        if (type == Boolean.class) return "expected true or false";
        if (type instanceof Class<?> cls && cls.isEnum()) {
            StringBuilder names = new StringBuilder();
            for (Object constant : cls.getEnumConstants()) {
                if (names.length() > 0) names.append(", ");
                names.append(((Enum<?>) constant).name());
            }
            return "expected one of " + names;
        }
        // Jackson: 'Cannot deserialize value of type `X` from String "value": reason' -> '"value": reason'
        String message = rootMessage(e);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("from \\w+ (\".*?\"): (.*)$").matcher(message);
        if (m.find()) return m.group(1) + ": " + m.group(2);
        return message;
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        if (message == null) return cause.getClass().getSimpleName();
        int newline = message.indexOf('\n');
        return newline >= 0 ? message.substring(0, newline) : message;
    }
}
