package adris.altoclef.settings;

import adris.altoclef.Debug;
import adris.altoclef.util.serialization.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Global access to the settings system of the running mod: the {@link SettingsManager} with
 * Minecraft types (items, block positions) and the profiles in {@code .minecraft/altoclef/}.
 * <p>
 * Parts of the mod register their settings with {@code Setting.xxx(...).register(SettingsService.manager())}.
 */
public final class SettingsService {

    private static final Path BASE_DIR = Paths.get("altoclef");

    /**
     * Old JSON config files and the key prefix their fields get. Imported once into the default profile
     * when it is created, the old files are left untouched.
     */
    private static final Map<String, String> LEGACY_FILES = Map.of(
            "altoclef_settings.json", "core",
            "configs/beat_minecraft.json", "beatMinecraft",
            "configs/butler.json", "butler",
            "configs/food_chain_settings.json", "foodChain",
            "configs/mlg_clutch_settings.json", "mlgClutch"
    );

    private static final SettingsManager MANAGER = new SettingsManager(createMapper(), SettingsService::report);
    private static final ProfileStore PROFILES = new ProfileStore(BASE_DIR, MANAGER.getMapper());
    // Changes from commands reload quietly: warnings that were already shown are not repeated.
    private static final SettingsEditor EDITOR = new SettingsEditor(MANAGER, PROFILES, () -> load(true));

    private static Set<String> shownWarnings = new HashSet<>();
    private static Set<String> currentWarnings = null;
    private static boolean onlyNewWarnings = false;

    private static volatile boolean lastLoadFailed = false;

    private SettingsService() {
    }

    /**
     * True if the last {@link #load()} had problems with the profile files (missing, unreadable, cycles).
     */
    public static boolean lastLoadFailed() {
        return lastLoadFailed;
    }

    public static SettingsManager manager() {
        return MANAGER;
    }

    public static ProfileStore profiles() {
        return PROFILES;
    }

    /**
     * Change values / switch profiles (used by the in-game commands).
     */
    public static SettingsEditor editor() {
        return EDITOR;
    }

    public static ObjectMapper createMapper() {
        SimpleModule mcTypes = new SimpleModule("altoclef-minecraft");
        mcTypes.addSerializer(BlockPos.class, new BlockPosSerializer());
        mcTypes.addDeserializer(BlockPos.class, new BlockPosDeserializer());
        mcTypes.addSerializer(ChunkPos.class, new ChunkPosSerializer());
        mcTypes.addDeserializer(ChunkPos.class, new ChunkPosDeserializer());
        mcTypes.addSerializer(Vec3d.class, new Vec3dSerializer());
        mcTypes.addDeserializer(Vec3d.class, new Vec3dDeserializer());
        return SettingsYaml.createMapper(mcTypes, ItemIdCodec.module());
    }

    /**
     * Loads (or reloads) the active profile into all registered settings.
     * On first start the default profile is created and old JSON settings are imported.
     */
    public static void load() {
        load(false);
    }

    /**
     * @param onlyNewWarnings true: warnings already shown by the previous load are only written to the log,
     *                        not repeated in chat (used after every change from a command).
     */
    public static synchronized void load(boolean onlyNewWarnings) {
        SettingsService.onlyNewWarnings = onlyNewWarnings;
        currentWarnings = new HashSet<>();
        try {
            loadInternal();
        } finally {
            shownWarnings = currentWarnings;
            currentWarnings = null;
            SettingsService.onlyNewWarnings = false;
        }
    }

    private static void report(String warning) {
        if (currentWarnings != null) currentWarnings.add(warning);
        if (onlyNewWarnings && shownWarnings.contains(warning)) {
            Debug.logInternal("Settings (already shown): " + warning);
            return;
        }
        Debug.logWarning(warning);
    }

    private static void loadInternal() {
        List<String> warnings = new ArrayList<>();
        try {
            if (PROFILES.ensureDefaults()) {
                importLegacy(warnings);
            }
        } catch (IOException e) {
            Debug.logError("Could not create settings profiles in " + BASE_DIR.toAbsolutePath(), e);
        }

        String active = PROFILES.getActiveProfile();
        ObjectNode values = PROFILES.resolve(active, warnings);
        lastLoadFailed = !warnings.isEmpty();
        warnings.forEach(w -> report("Settings: " + w));
        MANAGER.load(values);

        List<String> unknown = MANAGER.getUnknownKeys();
        if (!unknown.isEmpty()) {
            // Usually a typo; settings of parts that register later also show up here.
            report("Settings: unknown keys in profile \"" + active + "\" (typo?): " + String.join(", ", unknown));
        }
        try {
            PROFILES.writeReference(MANAGER);
        } catch (IOException e) {
            Debug.logWarning("Could not write settings_reference.yml: " + e.getMessage());
        }
        Debug.logInternal("Settings: loaded profile \"" + active + "\"");
    }

    /**
     * Copies values from the old JSON files that differ from the current defaults into the default profile.
     */
    private static void importLegacy(List<String> warnings) throws IOException {
        ObjectMapper json = new ObjectMapper();
        ObjectNode imported = MANAGER.getMapper().createObjectNode();
        for (Map.Entry<String, String> legacy : LEGACY_FILES.entrySet()) {
            Path file = BASE_DIR.resolve(legacy.getKey());
            if (!Files.exists(file)) continue;
            JsonNode root;
            try {
                root = json.readTree(file.toFile());
            } catch (IOException e) {
                warnings.add("Could not import old settings " + file + ": " + e.getMessage());
                continue;
            }
            if (!root.isObject()) continue;
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = legacy.getValue() + "." + field.getKey();
                boolean sameAsDefault = MANAGER.find(key)
                        .map(setting -> MANAGER.defaultToNode(setting).equals(field.getValue()))
                        .orElse(false);
                if (!sameAsDefault) {
                    SettingsManager.putNested(imported, key, field.getValue());
                }
            }
            Debug.logMessage("Imported old settings from " + file + " into profiles/default.yml");
        }
        if (!imported.isEmpty()) {
            PROFILES.writeProfile(ProfileStore.DEFAULT_PROFILE, null, imported);
        }
    }
}
