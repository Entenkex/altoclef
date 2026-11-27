package adris.altoclef.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Reads and writes settings profiles (YAML files).
 * <p>
 * Layout inside the base folder (normally {@code .minecraft/altoclef/}):
 * <pre>
 * profiles.yml             active: default
 * profiles/default.yml     the default profile
 * profiles/&lt;name&gt;.yml      more profiles
 * settings_reference.yml   generated list of all settings with defaults and limits (read only)
 * </pre>
 * A profile only contains the values that differ from the defaults and may extend another profile:
 * <pre>
 * extends: default
 * settings:
 *   core:
 *     commandPrefix: "#"
 *   beatMinecraft:
 *     targetEyes: 12
 * </pre>
 * Does not depend on Minecraft.
 */
public final class ProfileStore {

    public static final String DEFAULT_PROFILE = "default";
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private final Path baseDir;
    private final ObjectMapper yaml;

    public ProfileStore(Path baseDir, ObjectMapper yamlMapper) {
        this.baseDir = baseDir;
        this.yaml = yamlMapper;
    }

    public Path getProfilesDir() {
        return baseDir.resolve("profiles");
    }

    public Path getProfileFile(String name) {
        requireValidName(name);
        return getProfilesDir().resolve(name + ".yml");
    }

    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    // ------------------------------------------------------------------ active profile

    public String getActiveProfile() {
        Path file = baseDir.resolve("profiles.yml");
        if (Files.exists(file)) {
            try {
                JsonNode active = yaml.readTree(file.toFile()).path("active");
                if (active.isTextual() && isValidName(active.asText())) {
                    return active.asText();
                }
            } catch (IOException ignored) {
                // fall through to default
            }
        }
        return DEFAULT_PROFILE;
    }

    public void setActiveProfile(String name) throws IOException {
        requireValidName(name);
        Files.createDirectories(baseDir);
        Files.writeString(baseDir.resolve("profiles.yml"),
                "# Which file in profiles/ is used. Change it here or in-game.\nactive: " + name + "\n",
                StandardCharsets.UTF_8);
    }

    public List<String> listProfiles() throws IOException {
        if (!Files.isDirectory(getProfilesDir())) return List.of();
        try (var files = Files.list(getProfilesDir())) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".yml"))
                    .map(n -> n.substring(0, n.length() - 4))
                    .filter(ProfileStore::isValidName)
                    .sorted()
                    .toList();
        }
    }

    public boolean exists(String name) {
        return isValidName(name) && Files.exists(getProfileFile(name));
    }

    /**
     * Creates the folder, {@code profiles.yml} and an empty default profile if they are missing.
     *
     * @return true if the default profile was created now (first start).
     */
    public boolean ensureDefaults() throws IOException {
        Files.createDirectories(getProfilesDir());
        if (!Files.exists(baseDir.resolve("profiles.yml"))) {
            setActiveProfile(DEFAULT_PROFILE);
        }
        if (!exists(DEFAULT_PROFILE)) {
            writeProfile(DEFAULT_PROFILE, null, yaml.createObjectNode());
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ reading

    /**
     * Loads a profile including everything it extends.
     *
     * @param warnings receives problems (missing files, parse errors, cycles).
     * @return the merged {@code settings} tree.
     */
    public ObjectNode resolve(String name, List<String> warnings) {
        List<ObjectNode> chain = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        String current = name;
        while (current != null) {
            if (!visited.add(current)) {
                warnings.add("Profile cycle: " + String.join(" -> ", visited) + " -> " + current + ", ignoring the rest");
                break;
            }
            if (!isValidName(current)) {
                warnings.add("Invalid profile name \"" + current + "\"");
                break;
            }
            Path file = getProfileFile(current);
            if (!Files.exists(file)) {
                warnings.add("Profile \"" + current + "\" not found (" + file + ")");
                break;
            }
            JsonNode root;
            try {
                root = yaml.readTree(file.toFile());
            } catch (IOException e) {
                warnings.add("Could not read profile \"" + current + "\": " + shortError(e));
                break;
            }
            if (root == null || root.isMissingNode() || root.isNull()) {
                root = yaml.createObjectNode();
            }
            if (!root.isObject()) {
                warnings.add("Profile \"" + current + "\" must be a YAML map");
                break;
            }
            JsonNode settings = root.path("settings");
            if (settings.isObject()) {
                chain.add((ObjectNode) settings);
            } else if (!settings.isMissingNode() && !settings.isNull()) {
                warnings.add("Profile \"" + current + "\": 'settings' must be a map");
            }
            JsonNode parent = root.path("extends");
            current = parent.isTextual() ? parent.asText() : null;
        }

        // Base profile first, the requested profile wins.
        ObjectNode merged = yaml.createObjectNode();
        for (int i = chain.size() - 1; i >= 0; i--) {
            mergeInto(merged, chain.get(i));
        }
        return merged;
    }

    /**
     * One-line description of a read/parse error, e.g. "expected the node content, but found '<stream end>' (line 2, column 1)".
     */
    static String shortError(IOException e) {
        if (e instanceof com.fasterxml.jackson.core.JsonProcessingException parse) {
            String message = parse.getOriginalMessage();
            String[] lines = message == null ? new String[0] : message.split("\\R");
            // SnakeYAML: "while parsing ...\n in 'reader', line ...\nexpected ..." -> the "expected/found" line is the useful one
            String reason = lines.length == 0 ? "invalid YAML" : lines[0].strip();
            for (String line : lines) {
                String l = line.strip();
                if (l.startsWith("expected") || l.startsWith("found") || l.startsWith("mapping values")) {
                    reason = l;
                    break;
                }
            }
            var location = parse.getLocation();
            if (location != null && location.getLineNr() > 0) {
                reason += " (line " + location.getLineNr() + ", column " + location.getColumnNr() + ")";
            }
            return reason;
        }
        return e.getMessage();
    }

    /**
     * Deep merge: maps are merged key by key, everything else (values, lists) is replaced.
     */
    public static void mergeInto(ObjectNode target, ObjectNode overrides) {
        Iterator<Map.Entry<String, JsonNode>> fields = overrides.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode existing = target.get(field.getKey());
            if (existing instanceof ObjectNode existingObject && field.getValue() instanceof ObjectNode overrideObject) {
                mergeInto(existingObject, overrideObject);
            } else {
                target.set(field.getKey(), field.getValue().deepCopy());
            }
        }
    }

    // ------------------------------------------------------------------ editing single values

    /**
     * Sets one value in a profile file (without the profiles it extends). {@code null} removes the value,
     * so the profile falls back to what it extends / the default. Other values and "extends" are kept.
     * Creates the profile if it doesn't exist yet.
     */
    public void setValue(String name, String key, JsonNode value) throws IOException {
        Path file = getProfileFile(name);
        ObjectNode root = yaml.createObjectNode();
        if (Files.exists(file)) {
            JsonNode existing = yaml.readTree(file.toFile());
            if (existing != null && existing.isObject()) root = (ObjectNode) existing;
        }
        JsonNode settingsNode = root.get("settings");
        ObjectNode settings = settingsNode instanceof ObjectNode o ? o : yaml.createObjectNode();

        // A key may be written flat ("core.autoEat") or nested; drop both forms first.
        settings.remove(key);
        removeNested(settings, key.split("\\."), 0);
        if (value != null) {
            SettingsManager.putNested(settings, key, value);
        }
        JsonNode parent = root.get("extends");
        writeProfile(name, parent != null && parent.isTextual() ? parent.asText() : null, settings);
    }

    /**
     * Removes the value at the nested path and empty maps left behind.
     */
    private static void removeNested(ObjectNode node, String[] parts, int index) {
        if (index == parts.length - 1) {
            node.remove(parts[index]);
            return;
        }
        JsonNode child = node.get(parts[index]);
        if (child instanceof ObjectNode childObject) {
            removeNested(childObject, parts, index + 1);
            if (childObject.isEmpty()) node.remove(parts[index]);
        }
    }

    /**
     * The profile this one extends, or null.
     */
    public String getParent(String name) {
        try {
            JsonNode parent = yaml.readTree(getProfileFile(name).toFile()).path("extends");
            return parent.isTextual() ? parent.asText() : null;
        } catch (IOException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ writing

    public void writeProfile(String name, String extendsProfile, ObjectNode settings) throws IOException {
        ObjectNode root = yaml.createObjectNode();
        if (extendsProfile != null) root.put("extends", extendsProfile);
        root.set("settings", settings);
        String header = """
                # AltoClef settings profile "%s".
                # Only list values that differ from the defaults.
                # All settings with defaults and limits: ../settings_reference.yml
                # Optional: "extends: <other profile>" to build on another profile.
                """.formatted(name);
        Files.createDirectories(getProfilesDir());
        Files.writeString(getProfileFile(name), header + yaml.writeValueAsString(root), StandardCharsets.UTF_8);
    }

    /**
     * Writes {@code settings_reference.yml}: every registered setting with description, default and limits.
     * Regenerated on every load, so it always matches the current version.
     */
    public void writeReference(SettingsManager manager) throws IOException {
        StringBuilder out = new StringBuilder("""
                # GENERATED FILE - changes here are overwritten.
                # All AltoClef settings with their default values and limits.
                # To change a value, copy it into a profile in profiles/ (e.g. profiles/default.yml) under "settings:".
                # Values above max are set to max, below min to min; invalid values use the default.

                """);
        List<Setting<?>> sorted = new ArrayList<>(manager.getAll());
        sorted.sort(Comparator.comparing(Setting::getKey));
        for (Setting<?> setting : sorted) {
            for (String line : setting.getDescription().split("\\R")) {
                if (!line.isBlank()) out.append("# ").append(line.strip()).append('\n');
            }
            String limits = setting.getMin().map(m -> "min " + m).orElse("")
                    + (setting.getMin().isPresent() && setting.getMax().isPresent() ? ", " : "")
                    + setting.getMax().map(m -> "max " + m).orElse("");
            if (!limits.isEmpty()) out.append("# (").append(limits).append(")\n");
            ObjectNode single = yaml.createObjectNode();
            single.set(setting.getKey(), manager.defaultToNode(setting));
            String body = yaml.writeValueAsString(single);
            if (body.startsWith("---")) body = body.substring(body.indexOf('\n') + 1);
            out.append(body).append('\n');
        }
        Files.createDirectories(baseDir);
        Files.writeString(baseDir.resolve("settings_reference.yml"), out.toString(), StandardCharsets.UTF_8);
    }

    private static void requireValidName(String name) {
        if (!isValidName(name)) {
            throw new IllegalArgumentException("Invalid profile name \"" + name + "\" (allowed: letters, digits, _ and -)");
        }
    }
}
