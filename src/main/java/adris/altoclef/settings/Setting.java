package adris.altoclef.settings;

import com.fasterxml.jackson.core.type.TypeReference;

import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * One configurable value with a key, a type, a default and optional limits.
 * <p>
 * Settings are registered at the {@link SettingsManager}, which fills them from the active profile.
 * Code reads the current value with {@link #get()}.
 * <p>
 * Limits: values above {@code max} are clamped to {@code max}, values below {@code min} to {@code min}.
 * Values of the wrong type or that fail the custom check fall back to the default.
 * <p>
 * This class does not depend on Minecraft, so it can be unit tested.
 */
public final class Setting<T> {

    private final String key;
    private final Type type;
    private final T defaultValue;
    private final String description;
    private final T min;
    private final T max;
    private final Function<T, String> check;

    private T value;

    private Setting(Builder<T> builder) {
        this.key = builder.key;
        this.type = builder.type;
        this.defaultValue = builder.defaultValue;
        this.description = builder.description;
        this.min = builder.min;
        this.max = builder.max;
        this.check = builder.check;
        this.value = defaultValue;
    }

    // ------------------------------------------------------------------ factories

    public static Builder<Boolean> bool(String key, boolean defaultValue) {
        return new Builder<>(key, Boolean.class, defaultValue);
    }

    public static Builder<Integer> integer(String key, int defaultValue) {
        return new Builder<>(key, Integer.class, defaultValue);
    }

    public static Builder<Float> floating(String key, float defaultValue) {
        return new Builder<>(key, Float.class, defaultValue);
    }

    public static Builder<Double> decimal(String key, double defaultValue) {
        return new Builder<>(key, Double.class, defaultValue);
    }

    public static Builder<String> string(String key, String defaultValue) {
        return new Builder<>(key, String.class, defaultValue);
    }

    public static <E extends Enum<E>> Builder<E> enumeration(String key, Class<E> type, E defaultValue) {
        return new Builder<>(key, type, defaultValue);
    }

    /**
     * Any type Jackson can read, e.g. {@code new TypeReference<List<Item>>() {}}.
     */
    public static <T> Builder<T> of(String key, TypeReference<T> type, T defaultValue) {
        return new Builder<>(key, type.getType(), defaultValue);
    }

    public static <T> Builder<T> of(String key, Class<T> type, T defaultValue) {
        return new Builder<>(key, type, defaultValue);
    }

    // ------------------------------------------------------------------ access

    public T get() {
        return value;
    }

    public String getKey() {
        return key;
    }

    public Type getType() {
        return type;
    }

    public T getDefault() {
        return defaultValue;
    }

    public String getDescription() {
        return description;
    }

    public Optional<T> getMin() {
        return Optional.ofNullable(min);
    }

    public Optional<T> getMax() {
        return Optional.ofNullable(max);
    }

    public boolean isDefault() {
        return Objects.equals(value, defaultValue);
    }

    /**
     * Sets a new value: clamps it to min/max and falls back to the default if it is invalid.
     *
     * @return a warning if the value had to be changed, empty if it was accepted as is.
     */
    public Optional<String> set(T newValue) {
        Resolved<T> resolved = resolve(newValue);
        value = resolved.value;
        return Optional.ofNullable(resolved.warning);
    }

    public void reset() {
        value = defaultValue;
    }

    /**
     * What {@link #set} would store for {@code candidate}, without changing anything.
     */
    public Resolved<T> resolve(T candidate) {
        if (candidate == null) {
            return new Resolved<>(defaultValue, key + ": no value given, using default " + defaultValue, true);
        }
        if (min != null && compare(candidate, min) < 0) {
            return new Resolved<>(min, key + ": " + candidate + " is below the minimum, using " + min, false);
        }
        if (max != null && compare(candidate, max) > 0) {
            return new Resolved<>(max, key + ": " + candidate + " is above the maximum, using " + max, false);
        }
        if (check != null) {
            String error = check.apply(candidate);
            if (error != null) {
                return new Resolved<>(defaultValue, key + ": " + error + ", using default " + defaultValue, true);
            }
        }
        return new Resolved<>(candidate, null, false);
    }

    @SuppressWarnings("unchecked")
    private static <T> int compare(T a, T b) {
        return ((Comparable<T>) a).compareTo(b);
    }

    @Override
    public String toString() {
        return key + "=" + value;
    }

    /**
     * Result of {@link #resolve}: the value that would be stored, a warning if it differs from the input,
     * and whether the input was rejected (fell back to the default) rather than only clamped.
     */
    public record Resolved<T>(T value, String warning, boolean rejected) {
    }

    // ------------------------------------------------------------------ builder

    public static final class Builder<T> {
        private final String key;
        private final Type type;
        private final T defaultValue;
        private String description = "";
        private T min;
        private T max;
        private Function<T, String> check;

        private Builder(String key, Type type, T defaultValue) {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("Setting key must not be empty");
            this.key = key;
            this.type = Objects.requireNonNull(type);
            this.defaultValue = defaultValue;
        }

        public Builder<T> description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Only for types that implement {@link Comparable} (numbers, strings, enums).
         */
        public Builder<T> min(T min) {
            requireComparable(min);
            this.min = min;
            return this;
        }

        public Builder<T> max(T max) {
            requireComparable(max);
            this.max = max;
            return this;
        }

        public Builder<T> range(T min, T max) {
            return min(min).max(max);
        }

        /**
         * Custom check; returns an error message for an invalid value, or {@code null} if it is fine.
         * Invalid values fall back to the default.
         */
        public Builder<T> check(Function<T, String> check) {
            this.check = check;
            return this;
        }

        public Setting<T> build() {
            if (min != null && max != null && compare(min, max) > 0) {
                throw new IllegalArgumentException(key + ": min " + min + " is greater than max " + max);
            }
            Setting<T> setting = new Setting<>(this);
            Resolved<T> resolvedDefault = setting.resolve(defaultValue);
            if (resolvedDefault.warning != null && defaultValue != null) {
                throw new IllegalArgumentException("Default of " + key + " is invalid: " + resolvedDefault.warning);
            }
            return setting;
        }

        /**
         * Builds the setting and registers it at the manager.
         */
        public Setting<T> register(SettingsManager manager) {
            return manager.register(build());
        }

        private static void requireComparable(Object value) {
            if (!(value instanceof Comparable)) {
                throw new IllegalArgumentException("min/max need a Comparable value, got " + value);
            }
        }
    }
}
