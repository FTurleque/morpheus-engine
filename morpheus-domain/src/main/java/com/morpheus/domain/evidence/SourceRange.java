package com.morpheus.domain.evidence;

/** Optional one-based line range locating evidence inside a textual source. */
public record SourceRange(int startLine, int endLine) {
    public SourceRange {
        if (startLine < 1) {
            throw new IllegalArgumentException("startLine must be >= 1");
        }
        if (endLine < startLine) {
            throw new IllegalArgumentException("endLine must be >= startLine");
        }
    }
}
