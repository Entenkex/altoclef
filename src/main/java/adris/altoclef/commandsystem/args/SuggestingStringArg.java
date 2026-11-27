package adris.altoclef.commandsystem.args;

import adris.altoclef.commandsystem.StringReader;

import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A single word argument with tab completion from a dynamic list (e.g. setting keys, profile names).
 */
public class SuggestingStringArg extends StringArg {

    private final Supplier<Stream<String>> suggestions;

    public SuggestingStringArg(String name, String defaultValue, Supplier<Stream<String>> suggestions) {
        super(name, defaultValue, false);
        this.suggestions = suggestions;
    }

    @Override
    public Stream<String> getSuggestions(StringReader reader) {
        return suggestions.get();
    }
}
