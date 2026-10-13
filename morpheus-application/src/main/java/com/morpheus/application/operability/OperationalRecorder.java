package com.morpheus.application.operability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

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

    /**
     * Records a failure that is deliberately not propagated. The counter of its family moves whatever the sink is; an
     * event is emitted with the family and the type of the failure, and never its message, which may name a server
     * location.
     */
    public void suppressed(SuppressedFailureFamily family, Throwable failure) {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(failure, "failure");
        metrics.increment(family.counter());
        sink.emit(new OperationalEvent(Instant.now(), family.level(), OperationalEventCode.FAILURE_SUPPRESSED,
                Map.of("family", family.key(), "failureType", failure.getClass().getSimpleName())));
    }

    /** {@link #suppressed} as an observer, for a component that reports what it set aside without knowing who listens. */
    public Consumer<Throwable> suppressing(SuppressedFailureFamily family) {
        Objects.requireNonNull(family, "family");
        return failure -> suppressed(family, failure);
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
