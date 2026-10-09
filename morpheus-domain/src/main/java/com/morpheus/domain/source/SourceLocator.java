package com.morpheus.domain.source;

import java.util.Locale;
import java.util.Objects;

/**
 * Provider-neutral locator for a specification source.
 *
 * <p>A locator explains where a source was observed. It is never a MORPHEUS domain identity.</p>
 *
 * <p>A locator is a stable, comparable designation: {@link #file(String)} normalizes, so the same file read on two
 * platforms is recorded under the same locator, which the stores persist and the project registry compares. It is
 * not the text of a refusal. A refusal names a file as the operator will find it and substitutes nothing
 * ({@code WorkspaceRelativePathText}), and the two differ for a file name that contains a backslash on a platform
 * where that is a legal character. A failure message, and the {@code source} text given to a
 * {@code ProviderIngestionBudget} method, must not be built from a locator value.</p>
 */
public record SourceLocator(String scheme, String value) implements Comparable<SourceLocator> {

    public SourceLocator {
        scheme = requireNonBlank(scheme, "scheme").toLowerCase(Locale.ROOT);
        value = requireNonBlank(value, "value");
    }

    /**
     * A file locator: the path trimmed, every backslash rewritten to a slash, and any leading {@code ./} dropped.
     * A backslash is rewritten even where it is part of a file name, because a locator must not depend on the
     * platform that read the file.
     */
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
