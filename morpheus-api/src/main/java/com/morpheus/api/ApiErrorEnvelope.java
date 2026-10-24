package com.morpheus.api;

import java.util.Objects;

/** The envelope of a refused request of the HTTP API. */
public record ApiErrorEnvelope(String apiVersion, ApiError error) {
    public ApiErrorEnvelope {
        Objects.requireNonNull(apiVersion, "apiVersion");
        Objects.requireNonNull(error, "error");
    }
}
