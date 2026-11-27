package adris.altoclef.settings;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 * Creates the YAML mapper used for settings profiles.
 * Minecraft types (items, block positions, ...) are added as extra Jackson modules by the caller.
 */
public final class SettingsYaml {

    private SettingsYaml() {
    }

    public static YAMLMapper createMapper(Module... modules) {
        YAMLFactory factory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)
                .build();
        YAMLMapper.Builder builder = YAMLMapper.builder(factory)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        for (Module module : modules) {
            builder.addModule(module);
        }
        return builder.build();
    }
}
