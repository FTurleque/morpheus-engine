package com.morpheus.application.operability;

import com.morpheus.application.store.SpecificationKnowledgeStore;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Local-first operability projection shared by transports. */
public final class LocalOperabilityService {
    private final ReadinessProbe readinessProbe;
    private final OperationalMetrics metrics;
    private final OperationalEventSink eventSink;

    public LocalOperabilityService(
            SpecificationKnowledgeStore store,
            OperationalMetrics metrics,
            OperationalEventSink eventSink) {
        this(() -> Objects.requireNonNull(store, "store").listProjects().size(), metrics, eventSink);
    }

    public LocalOperabilityService(
            ReadinessProbe readinessProbe,
            OperationalMetrics metrics,
            OperationalEventSink eventSink) {
        this.readinessProbe = Objects.requireNonNull(readinessProbe, "readinessProbe");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.eventSink = Objects.requireNonNull(eventSink, "eventSink");
    }

    /** Liveness is process-local and does not claim that storage or optional integrations are ready. */
    public Health health() {
        return new Health("UP", Instant.now());
    }

    /** Readiness requires the local knowledge store to answer a real operation. */
    public Readiness readiness() {
        Instant checkedAt = Instant.now();
        try {
            int projectCount = readinessProbe.projectCount();
            eventSink.emit(new OperationalEvent(
                    checkedAt,
                    OperationalEventLevel.INFO,
                    OperationalEventCode.DATABASE_READY,
                    Map.of("projectCount", Integer.toString(projectCount))));
            return new Readiness("READY", checkedAt, Optional.empty());
        } catch (RuntimeException failure) {
            eventSink.emit(new OperationalEvent(
                    checkedAt,
                    OperationalEventLevel.ERROR,
                    OperationalEventCode.DATABASE_NOT_READY,
                    Map.of("errorType", failure.getClass().getSimpleName())));
            return new Readiness(
                    "NOT_READY",
                    checkedAt,
                    Optional.of(OperationalEventCode.DATABASE_NOT_READY.name()));
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(health(), readiness(), metrics.snapshot());
    }

    public OperationalMetrics.Snapshot metrics() {
        return metrics.snapshot();
    }

    @FunctionalInterface
    public interface ReadinessProbe {
        int projectCount();
    }

    public record Health(String status, Instant checkedAt) {
        public Health {
            if (!"UP".equals(status)) {
                throw new IllegalArgumentException("health status must be UP");
            }
            Objects.requireNonNull(checkedAt, "checkedAt");
        }
    }

    public record Readiness(String status, Instant checkedAt, Optional<String> diagnosticCode) {
        public Readiness {
            if (!"READY".equals(status) && !"NOT_READY".equals(status)) {
                throw new IllegalArgumentException("readiness status must be READY or NOT_READY");
            }
            Objects.requireNonNull(checkedAt, "checkedAt");
            Objects.requireNonNull(diagnosticCode, "diagnosticCode");
            if ("READY".equals(status) && diagnosticCode.isPresent()) {
                throw new IllegalArgumentException("READY must not contain a diagnostic code");
            }
            if ("NOT_READY".equals(status) && diagnosticCode.isEmpty()) {
                throw new IllegalArgumentException("NOT_READY requires a diagnostic code");
            }
        }
    }

    public record Snapshot(Health health, Readiness readiness, OperationalMetrics.Snapshot metrics) {
        public Snapshot {
            Objects.requireNonNull(health, "health");
            Objects.requireNonNull(readiness, "readiness");
            Objects.requireNonNull(metrics, "metrics");
        }
    }
}
