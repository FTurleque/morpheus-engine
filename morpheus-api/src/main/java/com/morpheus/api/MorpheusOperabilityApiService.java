package com.morpheus.api;

import com.morpheus.application.operability.LocalOperabilityService;
import com.morpheus.application.operability.LocalOperationalRuntime;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Transport-neutral API projection of M19 liveness, readiness and process-local metrics. */
public final class MorpheusOperabilityApiService {
    private final LocalOperabilityService operability;

    public MorpheusOperabilityApiService(LocalOperabilityService operability) {
        this.operability = Objects.requireNonNull(operability, "operability");
    }

    public MorpheusOperabilityApiService(Path databasePath) {
        Path database = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.operability = new LocalOperabilityService(
                () -> {
                    try (SqliteSpecificationKnowledgeStore store = new SqliteSpecificationKnowledgeStore(database)) {
                        return store.listProjects().size();
                    }
                },
                LocalOperationalRuntime.metrics(),
                LocalOperationalRuntime.sink());
    }

    public HealthView health() {
        LocalOperabilityService.Health health = operability.health();
        return new HealthView(health.status(), health.checkedAt().toString());
    }

    public ReadinessView readiness() {
        LocalOperabilityService.Readiness readiness = operability.readiness();
        return new ReadinessView(
                readiness.status(),
                readiness.checkedAt().toString(),
                readiness.diagnosticCode().orElse(null));
    }

    public MetricsView metrics() {
        OperationalMetrics.Snapshot snapshot = operability.metrics();
        LinkedHashMap<String, TimingView> timings = new LinkedHashMap<>();
        snapshot.timings().forEach((name, timing) -> timings.put(name, new TimingView(
                timing.count(),
                timing.totalNanos(),
                timing.maxNanos())));
        return new MetricsView(snapshot.counters(), Map.copyOf(timings));
    }

    public record HealthView(String status, String checkedAt) {
        public HealthView {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(checkedAt, "checkedAt");
        }
    }

    public record ReadinessView(String status, String checkedAt, String diagnosticCode) {
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
