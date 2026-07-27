package com.morpheus.application.operability;

import java.util.Map;
import java.util.Objects;

/** Executes provider/external calls with local timing and stable diagnostics, without recording business payloads. */
public final class OperationalExecution {
    private final OperationalRecorder recorder;

    public OperationalExecution(OperationalRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public <T> T providerRead(String providerId, ThrowingSupplier<T> operation) {
        return execute(
                "provider.read",
                OperationalEventCode.PROVIDER_READ_STARTED,
                OperationalEventCode.PROVIDER_READ_COMPLETED,
                OperationalEventCode.PROVIDER_READ_FAILED,
                Map.of("providerId", requireIdentifier(providerId, "providerId")),
                operation);
    }

    public <T> T externalCall(String integration, String operationName, ThrowingSupplier<T> operation) {
        return execute(
                "external." + requireMetricSegment(integration),
                OperationalEventCode.EXTERNAL_CALL_STARTED,
                OperationalEventCode.EXTERNAL_CALL_COMPLETED,
                OperationalEventCode.EXTERNAL_CALL_FAILED,
                Map.of(
                        "integration", requireIdentifier(integration, "integration"),
                        "operation", requireIdentifier(operationName, "operationName")),
                operation);
    }

    private <T> T execute(
            String metricName,
            OperationalEventCode startCode,
            OperationalEventCode successCode,
            OperationalEventCode failureCode,
            Map<String, String> attributes,
            ThrowingSupplier<T> operation) {
        Objects.requireNonNull(operation, "operation");
        OperationalRecorder.Operation recorded = recorder.begin(metricName, startCode, attributes);
        try {
            T result = operation.get();
            recorded.success(successCode, Map.of("outcome", "success"));
            return result;
        } catch (RuntimeException runtime) {
            recorded.failure(failureCode, Map.of("errorType", runtime.getClass().getSimpleName()));
            throw runtime;
        } catch (Exception checked) {
            recorded.failure(failureCode, Map.of("errorType", checked.getClass().getSimpleName()));
            throw new OperationalExecutionException("Observed operation failed", checked);
        }
    }

    private String requireIdentifier(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private String requireMetricSegment(String value) {
        String normalized = requireIdentifier(value, "integration")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_");
        return switch (normalized) {
            case "minos", "nexus", "technical_context" -> normalized;
            default -> "other";
        };
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    public static final class OperationalExecutionException extends RuntimeException {
        public OperationalExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
