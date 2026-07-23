package com.morpheus.application.query;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Deterministic lexical query over requirement key, title and statement. */
public record RequirementSearchQuery(String text) {
    public RequirementSearchQuery {
        Objects.requireNonNull(text, "text");
        text = normalize(text);
    }

    public static RequirementSearchQuery all() {
        return new RequirementSearchQuery("");
    }

    public List<String> terms() {
        if (text.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(text.split("\\p{javaWhitespace}+"))
                .filter(term -> !term.isEmpty())
                .toList();
    }

    static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
