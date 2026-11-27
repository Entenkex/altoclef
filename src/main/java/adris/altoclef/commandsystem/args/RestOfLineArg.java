package adris.altoclef.commandsystem.args;

import adris.altoclef.commandsystem.StringReader;
import adris.altoclef.commandsystem.exception.CommandNotFinishedException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Takes everything that is left on the line, including spaces (e.g. a setting value like {@code [coal, charcoal]}).
 * Must be the last argument of a command.
 */
public class RestOfLineArg extends Arg<String> {

    private final Supplier<Stream<String>> suggestions;

    public RestOfLineArg(String name, String defaultValue) {
        this(name, defaultValue, Stream::empty);
    }

    public RestOfLineArg(String name, String defaultValue, Supplier<Stream<String>> suggestions) {
        super(name, defaultValue, false);
        this.suggestions = suggestions;
    }

    @Override
    public Stream<String> getSuggestions(StringReader reader) {
        return suggestions.get();
    }

    @Override
    protected StringParser<String> getParser() {
        return reader -> {
            List<String> parts = new ArrayList<>();
            while (reader.hasNext()) {
                parts.add(reader.next());
            }
            String value = String.join(" ", parts);
            if (value.isBlank()) throw new CommandNotFinishedException("Value cannot be empty");
            return value;
        };
    }

    @Override
    public String getTypeName() {
        return "Text";
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }
}
