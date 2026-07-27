package com.morpheus.application.operability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Couples local structured events with in-process counters/timings. */
public final class OperationalRecorder {
    private final OperationalEventSink sink;
    private final OperationalMetrics metrics;

    public OperationalRecorder(OperationalEventSink sink, OperationalMetrics metrics) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public static OperationalRecorder noop() {
        return new OperationalRecorder(OperationalEventSink.noop(), new OperationalMetrics());
    }

    public OperationalMetrics metrics() {
        return metrics;
    }

    public Operation begin(String operation, OperationalEventCode startCode, Map<String, String> attributes) {
        String name = requireOperation(operation);
        Objects.requireNonNull(startCode, "startCode");
        Map<String, String> base = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
        metrics.increment(name + ".started");
        sink.emit(new OperationalEvent(Instant.now(), OperationalEventLevel.INFO, startCode, base));
        return new Operation(name, System.nanoTime(), base);
    }

    public final class Operation {
        private final String operation;
        private final long startedNanos;
        private final Map<String, String> baseAttributes;
        private final AtomicBoolean finished = new AtomicBoolean();

        private Operation(String operation, long startedNanos, Map<String, String> baseAttributes) {
            this.operation = operation;
            this.startedNanos = startedNanos;
            this.baseAttributes = baseAttributes;
        }

        public void success(OperationalEventCode code, Map<String, String> attributes) {
            finish(OperationalEventLevel.INFO, Objects.requireNonNull(code, "code"), "success", attributes);
        }

        public void warning(OperationalEventCode code, Map<String, String> attributes) {
            finish(OperationalEventLevel.WARNING, Objects.requireNonNull(code, "code"), "warning", attributes);
        }

        public void failure(OperationalEventCode code, Map<String, String> attributes) {
            finish(OperationalEventLevel.ERROR, Objects.requireNonNull(code, "code"), "failure", attributes);
        }

        private void finish(
                OperationalEventLevel level,
                OperationalEventCode code,
                String outcome,
                Map<String, String> attributes) {
            Objects.requireNonNull(attributes, "attributes");
            if (!finished.compareAndSet(false, true)) {
                throw new IllegalStateException("operational operation already finished: " + operation);
            }
            long durationNanos = Math.max(0L, System.nanoTime() - startedNanos);
            metrics.increment(operation + "." + outcome);
            metrics.recordDurationNanos(operation + ".duration", durationNanos);
            LinkedHashMap<String, String> merged = new LinkedHashMap<>(baseAttributes);
            merged.putAll(attributes);
            merged.put("durationMs", Long.toString(durationNanos / 1_000_000L));
            sink.emit(new OperationalEvent(Instant.now(), level, code, merged));
        }
    }

    private String requireOperation(String operation) {
        Objects.requireNonNull(operation, "operation");
        String normalized = operation.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("operation must not be blank");
        }
        return normalized;
    }
}
