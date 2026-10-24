package com.morpheus.api;

import java.util.Objects;

/** The envelope of a successful response of the HTTP API. */
public record ApiSuccess(String apiVersion, Object data) {
    public ApiSuccess {
        Objects.requireNonNull(apiVersion, "apiVersion");
        Objects.requireNonNull(data, "data");
    }
}
