package com.morpheus.application.context;

import java.util.Objects;

/** Provider-neutral request sent to the external technical context engine. */
public record TechnicalContextRequest(String query, TechnicalContextOptions options) {
    public TechnicalContextRequest {
        Objects.requireNonNull(query, "query");
        query = query.trim();
        if (query.isEmpty()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        Objects.requireNonNull(options, "options");
    }
}
