package com.morpheus.api.transport;

import java.util.Map;
import java.util.Objects;

/** Internal typed failure translated to the stable M11 JSON error envelope. */
public final class ApiFailure extends RuntimeException {
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

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }

    public static ApiFailure badRequest(String message) {
        return new ApiFailure(400, "BAD_REQUEST", message);
    }

    public static ApiFailure notFound(String message) {
        return new ApiFailure(404, "NOT_FOUND", message);
    }

    public static ApiFailure methodNotAllowed(String message) {
        return new ApiFailure(405, "METHOD_NOT_ALLOWED", message);
    }

    public static ApiFailure conflict(String message) {
        return new ApiFailure(409, "STATE_CONFLICT", message);
    }

    static ApiFailure unsupportedMediaType(String message) {
        return new ApiFailure(415, "UNSUPPORTED_MEDIA_TYPE", message);
    }
}
