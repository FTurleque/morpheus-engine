package com.morpheus.api;

import java.util.Map;
import java.util.Objects;

/** The error a refused request of the HTTP API answers with. */
public record ApiError(String code, String message, Map<String, Object> details) {
    public ApiError {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        details = Map.copyOf(Objects.requireNonNull(details, "details"));
    }
}
