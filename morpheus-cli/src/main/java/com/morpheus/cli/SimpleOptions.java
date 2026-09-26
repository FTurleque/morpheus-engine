package com.morpheus.cli;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Shared {@code --key value} option parsing (no boolean flags, no positionals) used identically by
 * every CLI adapter whose commands take only key/value options.
 *
 * <p>An option given with an empty or blank value is refused, not read as absent. {@link #optional} used to trim
 * the value and drop it when nothing was left, so {@code --project ""} emptied a filter and widened a query,
 * {@code --workspace ""} persisted a membership without a workspace and {@code --limit ""} fell back to the default,
 * each with exit code 0. None of the adapters has an option whose empty value means something the omitted option does
 * not: omitting it is the way to say "none".</p>
 *
 * <p>The refusal comes after the unknown-option check, in {@link #rejectUnknown}, so that a misspelled option given an
 * empty value is still reported as unknown; reading a blank value before that check refuses it too. The refusal
 * itself is {@link OptionValue}'s, shared with the other parser families.</p>
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
        return Optional.ofNullable(values.get(key)).map(value -> nonBlank(key, value));
    }

    void rejectUnknown(Set<String> allowed) {
        values.keySet().stream().filter(key -> !allowed.contains(key)).findFirst()
                .ifPresent(key -> {
                    throw new IllegalArgumentException("unknown option: --" + key);
                });
        values.forEach(SimpleOptions::nonBlank);
    }

    private static String nonBlank(String key, String value) {
        return OptionValue.nonBlank("--" + key, value).trim();
    }

    private static String require(List<String> tokens, int index, String option) {
        if (index >= tokens.size() || tokens.get(index).startsWith("--")) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return tokens.get(index);
    }
}
