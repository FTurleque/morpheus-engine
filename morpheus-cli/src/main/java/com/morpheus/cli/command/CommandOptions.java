package com.morpheus.cli.command;

import com.morpheus.application.query.PageRequest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The one parser of a sub-command's option tokens. The sub-command declares what it accepts -- the options that take
 * one value, those that take a value each time they are given, and the flags -- and nothing else is parsed.
 *
 * <p>Each token is refused, naming it, in this order: a token that is not an option ({@code unexpected positional
 * argument}), an option that is not declared ({@code unknown option}), a single-valued option or a flag given twice
 * ({@code duplicate option}), a value option with nothing after it or followed by another option
 * ({@code requires a value}), and a blank value ({@link OptionValue}). The options cannot be read without having been
 * declared: there is no parse that accepts every key and a check to remember afterwards.</p>
 *
 * <p>{@link #optional} and {@link #required} return the value trimmed; {@link #asGiven} returns it as typed, for a
 * path or a text that keeps its spelling.</p>
 */
public final class CommandOptions {
    private static final String OPT_OFFSET = "offset";
    private static final String OPT_LIMIT = "limit";

    private final Map<String, List<String>> values;
    private final Set<String> flags;

    private CommandOptions(Map<String, List<String>> values, Set<String> flags) {
        this.values = Map.copyOf(values);
        this.flags = Set.copyOf(flags);
    }

    /** What a sub-command accepts: options with one value, options repeated with a value each time, and flags. */
    public record Declared(Set<String> single, Set<String> repeated, Set<String> flags) {
        public Declared {
            single = Set.copyOf(single);
            repeated = Set.copyOf(repeated);
            flags = Set.copyOf(flags);
            Set<String> all = new HashSet<>();
            for (Set<String> kind : List.of(single, repeated, flags)) {
                for (String name : kind) {
                    if (!all.add(name)) {
                        throw new IllegalArgumentException("option declared twice: --" + name);
                    }
                }
            }
        }

        public static Declared nothing() {
            return new Declared(Set.of(), Set.of(), Set.of());
        }

        public static Declared values(String... names) {
            return values(Set.of(names));
        }

        public static Declared values(Set<String> names) {
            return new Declared(names, Set.of(), Set.of());
        }

        public Declared andRepeated(String... names) {
            return new Declared(single, Set.of(names), flags);
        }

        public Declared andFlags(String... names) {
            return new Declared(single, repeated, Set.of(names));
        }
    }

    public static CommandOptions parse(List<String> tokens, Declared declared) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        Set<String> flags = new HashSet<>();
        int index = 0;
        while (index < tokens.size()) {
            String token = tokens.get(index);
            index++;
            if (!token.startsWith("--")) {
                throw new IllegalArgumentException("unexpected positional argument: " + token);
            }
            String key = token.substring(2);
            if (declared.flags().contains(key)) {
                if (!flags.add(key)) {
                    throw new IllegalArgumentException("duplicate option: " + token);
                }
                continue;
            }
            boolean single = declared.single().contains(key);
            if (!single && !declared.repeated().contains(key)) {
                throw new IllegalArgumentException("unknown option: " + token);
            }
            if (single && values.containsKey(key)) {
                throw new IllegalArgumentException("duplicate option: " + token);
            }
            if (index >= tokens.size() || tokens.get(index).startsWith("--")) {
                throw new IllegalArgumentException(token + " requires a value");
            }
            values.computeIfAbsent(key, name -> new ArrayList<>()).add(OptionValue.nonBlank(token, tokens.get(index)));
            index++;
        }
        return new CommandOptions(values, flags);
    }

    public String required(String key) {
        return optional(key).orElseThrow(() -> new IllegalArgumentException("--" + key + " is required"));
    }

    public Path requiredPath(String key) {
        return Path.of(required(key));
    }

    public Optional<String> optional(String key) {
        return asGiven(key).map(String::trim);
    }

    /** The value of a single-valued option, as typed. */
    public Optional<String> asGiven(String key) {
        return Optional.ofNullable(values.get(key)).map(List::getFirst);
    }

    /** Every value of a repeated option, as typed, in the order given. */
    public List<String> all(String key) {
        return List.copyOf(values.getOrDefault(key, List.of()));
    }

    public boolean given(String key) {
        return values.containsKey(key) || flags.contains(key);
    }

    public boolean flag(String key) {
        return flags.contains(key);
    }

    public int intValue(String key, int defaultValue, int min, int max) {
        long value = longValue(key, defaultValue, min, max);
        return Math.toIntExact(value);
    }

    public long longValue(String key, long defaultValue, long min, long max) {
        return optional(key).map(raw -> bounded(key, raw, min, max)).orElse(defaultValue);
    }

    public long requiredLong(String key, long min, long max) {
        return bounded(key, required(key), min, max);
    }

    private static long bounded(String key, String raw, long min, long max) {
        long value;
        try {
            value = Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("--" + key + " must be an integer", exception);
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException("--" + key + " must be between " + min + " and " + max);
        }
        return value;
    }

    /** {@code --offset} from 0 and {@code --limit} from 1 to {@link PageRequest#MAX_LIMIT}, 20 when absent. */
    public PageRequest page() {
        return new PageRequest(intValue(OPT_OFFSET, 0, 0, Integer.MAX_VALUE), intValue(OPT_LIMIT, 20, 1, PageRequest.MAX_LIMIT));
    }
}
