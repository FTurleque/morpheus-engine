package com.morpheus.api;

import java.util.Objects;

/** Internal carrier from local HTTP route execution to envelope rendering. */
record MorpheusHttpRouteResponse(int status, Object data) {
    MorpheusHttpRouteResponse {
        if (status < 200 || status > 599) {
            throw new IllegalArgumentException("route status must be between 200 and 599");
        }
        Objects.requireNonNull(data, "data");
    }
}
