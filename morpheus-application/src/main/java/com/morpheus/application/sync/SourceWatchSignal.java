package com.morpheus.application.sync;

import java.util.Objects;
import java.util.Optional;

/** Normalized WatchService signal. It is a trigger hint, never the final source-state proof. */
public record SourceWatchSignal(
        Kind kind,
        Optional<SourcePath> path) implements Comparable<SourceWatchSignal> {

    public SourceWatchSignal {
        Objects.requireNonNull(kind, "kind");
        path = Objects.requireNonNull(path, "path");
        if (kind == Kind.OVERFLOW && path.isPresent()) {
            throw new IllegalArgumentException("OVERFLOW must not carry a path");
        }
        if (kind != Kind.OVERFLOW && path.isEmpty()) {
            throw new IllegalArgumentException(kind + " requires a path");
        }
    }

    public static SourceWatchSignal overflow() {
        return new SourceWatchSignal(Kind.OVERFLOW, Optional.empty());
    }

    @Override
    public int compareTo(SourceWatchSignal other) {
        int kindOrder = kind.compareTo(other.kind);
        return kindOrder != 0
                ? kindOrder
                : path.map(SourcePath::toString).orElse("")
                        .compareTo(other.path.map(SourcePath::toString).orElse(""));
    }

    public enum Kind {
        CREATE,
        MODIFY,
        DELETE,
        OVERFLOW
    }
}
