package com.morpheus.domain.source;

import java.util.Locale;
import java.util.Objects;

/**
 * Provider-neutral locator for a specification source.
 *
 * <p>A locator explains where a source was observed. It is never a MORPHEUS domain identity.
 */
public record SourceLocator(String scheme, String value) implements Comparable<SourceLocator> {

    public SourceLocator {
        scheme = requireNonBlank(scheme, "scheme").toLowerCase(Locale.ROOT);
        value = requireNonBlank(value, "value");
    }

    public static SourceLocator file(String relativePath) {
        String normalized = requireNonBlank(relativePath, "relativePath").replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return new SourceLocator("file", normalized);
    }

    @Override
    public int compareTo(SourceLocator other) {
        int schemeComparison = scheme.compareTo(other.scheme);
        return schemeComparison != 0 ? schemeComparison : value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return scheme + ":" + value;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }
}
