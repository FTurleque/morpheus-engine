package com.morpheus.api;

import java.util.Map;
import java.util.Objects;

/** Internal typed failure translated to the stable M11 JSON error envelope. */
final class ApiFailure extends RuntimeException {
    private final int status;
    private final String code;
    private final Map<String, Object> details;

    ApiFailure(int status, String code, String message) {
        this(status, code, message, Map.of());
    }

    ApiFailure(int status, String code, String message, Map<String, Object> details) {
        super(Objects.requireNonNull(message, "message"));
        this.status = status;
        this.code = Objects.requireNonNull(code, "code");
        this.details = Map.copyOf(Objects.requireNonNull(details, "details"));
    }

    int status() {
        return status;
    }

    String code() {
        return code;
    }

    Map<String, Object> details() {
        return details;
    }

    static ApiFailure badRequest(String message) {
        return new ApiFailure(400, "BAD_REQUEST", message);
    }

    static ApiFailure notFound(String message) {
        return new ApiFailure(404, "NOT_FOUND", message);
    }

    static ApiFailure methodNotAllowed(String message) {
        return new ApiFailure(405, "METHOD_NOT_ALLOWED", message);
    }

    static ApiFailure conflict(String message) {
        return new ApiFailure(409, "STATE_CONFLICT", message);
    }

    static ApiFailure unsupportedMediaType(String message) {
        return new ApiFailure(415, "UNSUPPORTED_MEDIA_TYPE", message);
    }
}
