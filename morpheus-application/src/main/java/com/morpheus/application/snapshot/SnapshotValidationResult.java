package com.morpheus.application.snapshot;

import java.util.List;
import java.util.Objects;

/** Structured outcome of validating one knowledge snapshot before publication. */
public record SnapshotValidationResult(List<String> errors, List<String> warnings) {
    public SnapshotValidationResult {
        errors = normalized(errors, "errors");
        warnings = normalized(warnings, "warnings");
    }

    public static SnapshotValidationResult valid() {
        return new SnapshotValidationResult(List.of(), List.of());
    }

    public static SnapshotValidationResult validWithWarnings(List<String> warnings) {
        return new SnapshotValidationResult(List.of(), warnings);
    }

    public static SnapshotValidationResult invalid(List<String> errors) {
        if (Objects.requireNonNull(errors, "errors").isEmpty()) {
            throw new IllegalArgumentException("invalid validation result requires at least one error");
        }
        return new SnapshotValidationResult(errors, List.of());
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    private static List<String> normalized(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        return values.stream().map(value -> requireNonBlank(value, name + " item")).toList();
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
