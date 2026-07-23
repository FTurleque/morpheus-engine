package com.morpheus.application.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Canonical project-relative source path using '/' separators. */
public record SourcePath(String value) implements Comparable<SourcePath> {
    public SourcePath {
        Objects.requireNonNull(value, "value");
        String raw = value.trim().replace('\\', '/');
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("source path must not be blank");
        }
        if (raw.startsWith("/") || raw.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("source path must be relative: " + value);
        }
        List<String> segments = new ArrayList<>();
        for (String segment : raw.split("/+")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                throw new IllegalArgumentException("source path must not escape project root: " + value);
            }
            segments.add(segment);
        }
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("source path must identify a source: " + value);
        }
        value = String.join("/", segments);
    }

    @Override
    public int compareTo(SourcePath other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
