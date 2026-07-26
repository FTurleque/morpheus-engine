package com.morpheus.api;

import com.morpheus.application.operability.LocalOperabilityService;
import com.morpheus.application.operability.OperationalMetrics;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Transport-neutral API projection of M19 liveness, readiness and process-local metrics. */
public final class MorpheusOperabilityApiService {
    private final LocalOperabilityService operability;

    public MorpheusOperabilityApiService(LocalOperabilityService operability) {
        this.operability = Objects.requireNonNull(operability, "operability");
    }

    public HealthView health() {
        LocalOperabilityService.Health health = operability.health();
        return new HealthView(health.status(), health.checkedAt());
    }

    public ReadinessView readiness() {
        LocalOperabilityService.Readiness readiness = operability.readiness();
        return new ReadinessView(
                readiness.status(),
                readiness.checkedAt(),
                readiness.diagnosticCode().orElse(null));
    }

    public MetricsView metrics() {
        OperationalMetrics.Snapshot snapshot = operability.snapshot().metrics();
        LinkedHashMap<String, TimingView> timings = new LinkedHashMap<>();
        snapshot.timings().forEach((name, timing) -> timings.put(name, new TimingView(
                timing.count(),
                timing.totalNanos(),
                timing.maxNanos())));
        return new MetricsView(snapshot.counters(), Map.copyOf(timings));
    }

    public record HealthView(String status, Instant checkedAt) {
        public HealthView {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(checkedAt, "checkedAt");
        }
    }

    public record ReadinessView(String status, Instant checkedAt, String diagnosticCode) {
        public ReadinessView {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(checkedAt, "checkedAt");
        }
    }

    public record MetricsView(Map<String, Long> counters, Map<String, TimingView> timings) {
        public MetricsView {
            counters = Map.copyOf(Objects.requireNonNull(counters, "counters"));
            timings = Map.copyOf(Objects.requireNonNull(timings, "timings"));
        }
    }

    public record TimingView(long count, long totalNanos, long maxNanos) {
    }
}
