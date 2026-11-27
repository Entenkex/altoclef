package adris.altoclef.util.helpers;

import adris.altoclef.Debug;
import adris.altoclef.util.serialization.IListConfigFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Loads simple list files (one entry per line, # for comments), e.g. the butler whitelist/blacklist.
 * <p>
 * Settings are not handled here anymore, see {@link adris.altoclef.settings.SettingsService}.
 */
public class ConfigHelper {

    private static final String ALTO_FOLDER = "altoclef";
    // For reloading
    private static final HashMap<String, Runnable> loadedConfigs = new HashMap<>();

    private static File getConfigFile(String path) {
        return Paths.get(ALTO_FOLDER, path).toFile();
    }

    public static void reloadAllConfigs() {
        for (Runnable reload : loadedConfigs.values()) {
            reload.run();
        }
    }

    private static <T extends IListConfigFile> T getListConfig(String path, Supplier<T> getDefault) {
        T result = getDefault.get();

        result.onLoadStart();

        File loadFrom = getConfigFile(path);
        if (!loadFrom.exists()) {
            // Empty
            Debug.logInternal("Lists file not found at " + path);
            return result;
        }

        try {
            FileInputStream fis = new FileInputStream(loadFrom);
            Scanner sc = new Scanner(fis);    //file to be scanned
            //returns true if there is another line to read
            while (sc.hasNextLine()) {
                String line = trimComment(sc.nextLine()).trim();
                if (line.isEmpty()) continue;
                result.addLine(line);
            }
            sc.close();
        } catch (IOException e) {
            Debug.logError("Failed to load list config file ("+loadFrom+")",e);
            return null;
        }
        return result;
    }

    public static <T extends IListConfigFile> void loadListConfig(String path, Supplier<T> getDefault, Consumer<T> onReload) {
        T result = getListConfig(path, getDefault);
        loadedConfigs.put(path, () -> onReload.accept(getListConfig(path, getDefault)));
        onReload.accept(result);
    }

    private static String trimComment(String line) {
        int pound = line.indexOf('#');
        if (pound == -1) {
            return line;
        }
        return line.substring(0, pound);
    }

    public static void ensureCommentedListFileExists(String path, String startingComment) {
        File loadFrom = getConfigFile(path);
        if (loadFrom.exists()) {
            // Already exists, don't make a new one.
            return;
        }
        StringBuilder result = new StringBuilder();
        for (String line : startingComment.split("\\r?\\n")) {
            if (!line.isEmpty()) {
                result.append("# ").append(line).append("\n");
            }
        }
        try {
            Files.write(loadFrom.toPath(), result.toString().getBytes());
        } catch (IOException e) {
            Debug.logError("Failed to write list config to ("+loadFrom+")",e);
        }
    }
}
