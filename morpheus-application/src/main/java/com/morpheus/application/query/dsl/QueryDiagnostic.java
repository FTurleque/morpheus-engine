package com.morpheus.application.query.dsl;

import java.util.Objects;

/** Stable structured validation diagnostic for an M24 query. */
public record QueryDiagnostic(String code, String path, String message) implements Comparable<QueryDiagnostic> {
    public QueryDiagnostic {
        code = require(code, "code");
        path = require(path, "path");
        message = require(message, "message");
    }

    @Override
    public int compareTo(QueryDiagnostic other) {
        int byPath = path.compareTo(other.path);
        if (byPath != 0) {
            return byPath;
        }
        int byCode = code.compareTo(other.code);
        return byCode != 0 ? byCode : message.compareTo(other.message);
    }

    private static String require(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
