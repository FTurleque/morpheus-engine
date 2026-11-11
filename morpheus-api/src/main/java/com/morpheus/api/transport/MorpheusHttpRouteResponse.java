package com.morpheus.api.transport;

import java.util.Objects;

/** Internal carrier from local HTTP route execution to envelope rendering. */
public record MorpheusHttpRouteResponse(int status, Object data) {
    public MorpheusHttpRouteResponse {
        if (status < 200 || status > 599) {
            throw new IllegalArgumentException("route status must be between 200 and 599");
        }
        Objects.requireNonNull(data, "data");
    }
}
