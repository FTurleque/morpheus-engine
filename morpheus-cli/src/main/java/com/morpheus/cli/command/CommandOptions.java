package com.morpheus.cli.command;

import com.morpheus.application.query.PageRequest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The options of a command that answers {@code MORPHEUS error [code]: message}: {@code --key value} pairs and the
 * boolean flags the command names, then {@link #rejectUnknown} with the keys it reads.
 */
public final class CommandOptions {
    private static final String OPT_OFFSET = "offset";
    private static final String OPT_LIMIT = "limit";

    private final Map<String, String> values;
    private final Set<String> flags;

    private CommandOptions(Map<String, String> values, Set<String> flags) {
        this.values = Map.copyOf(values);
        this.flags = Set.copyOf(flags);
    }

    public static CommandOptions parse(List<String> tokens, Set<String> allowedFlags) {
        Map<String, String> values = new HashMap<>();
        Set<String> flags = new HashSet<>();
        List<String> positionals = new ArrayList<>();
        int index = 0;
        while (index < tokens.size()) {
            String token = tokens.get(index);
            index++;
            if (!token.startsWith("--")) {
                positionals.add(token);
                continue;
            }
            index = consumeOption(token, tokens, index, allowedFlags, values, flags);
        }
        if (!positionals.isEmpty()) {
            throw new IllegalArgumentException("unexpected positional arguments: " + positionals);
        }
        return new CommandOptions(values, flags);
    }

    private static int consumeOption(
            String token,
            List<String> tokens,
            int index,
            Set<String> allowedFlags,
            Map<String, String> values,
            Set<String> flags) {
        String key = token.substring(2);
        if (key.isBlank()) {
            throw new IllegalArgumentException("invalid empty option");
        }
        if (allowedFlags.contains(key)) {
            if (!flags.add(key)) {
                throw new IllegalArgumentException("duplicate flag: --" + key);
            }
            return index;
        }
        if (index >= tokens.size() || tokens.get(index).startsWith("--")) {
            throw new IllegalArgumentException("--" + key + " requires a value");
        }
        if (values.putIfAbsent(key, tokens.get(index)) != null) {
            throw new IllegalArgumentException("duplicate option: --" + key);
        }
        return index + 1;
    }

    public void rejectUnknown(Set<String> allowed) {
        Set<String> unknown = new HashSet<>(values.keySet());
        unknown.addAll(flags);
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("unknown options: " + unknown);
        }
        new TreeMap<>(values).forEach((key, value) -> OptionValue.nonBlank("--" + key, value));
    }

    public String required(String key) {
        return optional(key).orElseThrow(() -> new IllegalArgumentException("--" + key + " is required"));
    }

    public Path requiredPath(String key) {
        return Path.of(required(key));
    }

    public Optional<String> optional(String key) {
        return Optional.ofNullable(values.get(key)).map(value -> OptionValue.nonBlank("--" + key, value).trim());
    }

    public boolean flag(String key) {
        return flags.contains(key);
    }

    public int intValue(String key, int defaultValue, int min, int max) {
        long value = longValue(key, defaultValue, min, max);
        return Math.toIntExact(value);
    }

    public long longValue(String key, long defaultValue, long min, long max) {
        String raw = values.get(key);
        long value;
        try {
            value = raw == null ? defaultValue : Long.parseLong(OptionValue.nonBlank("--" + key, raw));
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
