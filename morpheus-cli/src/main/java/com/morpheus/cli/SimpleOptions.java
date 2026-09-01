package com.morpheus.cli;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Shared {@code --key value} option parsing (no boolean flags, no positionals) used identically by
 * every CLI adapter whose commands take only key/value options.
 */
final class SimpleOptions {
    private final Map<String, String> values = new LinkedHashMap<>();

    static SimpleOptions parse(List<String> tokens) {
        SimpleOptions result = new SimpleOptions();
        int index = 0;
        while (index < tokens.size()) {
            String token = tokens.get(index);
            index++;
            if (!token.startsWith("--")) {
                throw new IllegalArgumentException("unknown token: " + token);
            }
            String key = token.substring(2);
            if (result.values.putIfAbsent(key, require(tokens, index, token)) != null) {
                throw new IllegalArgumentException("duplicate option: " + token);
            }
            index++;
        }
        return result;
    }

    String required(String key) {
        return optional(key).orElseThrow(() -> new IllegalArgumentException("--" + key + " is required"));
    }

    Optional<String> optional(String key) {
        return Optional.ofNullable(values.get(key)).map(String::trim).filter(value -> !value.isEmpty());
    }

    void rejectUnknown(Set<String> allowed) {
        values.keySet().stream().filter(key -> !allowed.contains(key)).findFirst()
                .ifPresent(key -> {
                    throw new IllegalArgumentException("unknown option: --" + key);
                });
    }

    private static String require(List<String> tokens, int index, String option) {
        if (index >= tokens.size() || tokens.get(index).startsWith("--")) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return tokens.get(index);
    }
}
