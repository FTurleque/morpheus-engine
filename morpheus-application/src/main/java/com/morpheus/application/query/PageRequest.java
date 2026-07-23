package com.morpheus.application.query;

/** Stable bounded offset pagination contract shared by M5 query services. */
public record PageRequest(int offset, int limit) {
    public static final int MAX_LIMIT = 100;

    public PageRequest {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be greater than or equal to zero");
        }
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    public static PageRequest first(int limit) {
        return new PageRequest(0, limit);
    }
}
