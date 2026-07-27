package com.morpheus.application.operability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/** Thread-safe process-local counters and duration aggregates; no external telemetry transport is implied. */
public final class OperationalMetrics {
    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TimingAccumulator> timings = new ConcurrentHashMap<>();

    public void increment(String name) {
        add(name, 1L);
    }

    public void add(String name, long delta) {
        String metric = requireMetricName(name);
        if (delta < 0L) {
            throw new IllegalArgumentException("counter delta must not be negative");
        }
        counters.computeIfAbsent(metric, ignored -> new LongAdder()).add(delta);
    }

    public void recordDurationNanos(String name, long durationNanos) {
        String metric = requireMetricName(name);
        if (durationNanos < 0L) {
            throw new IllegalArgumentException("durationNanos must not be negative");
        }
        timings.computeIfAbsent(metric, ignored -> new TimingAccumulator()).record(durationNanos);
    }

    public Snapshot snapshot() {
        TreeMap<String, Long> counterCopy = new TreeMap<>();
        counters.forEach((name, value) -> counterCopy.put(name, value.sum()));
        TreeMap<String, Timing> timingCopy = new TreeMap<>();
        timings.forEach((name, value) -> timingCopy.put(name, value.snapshot()));
        return new Snapshot(ordered(counterCopy), ordered(timingCopy));
    }

    private String requireMetricName(String name) {
        Objects.requireNonNull(name, "name");
        String normalized = name.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("metric name must not be blank");
        }
        return normalized;
    }

    private static <T> Map<String, T> ordered(Map<String, T> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public record Snapshot(Map<String, Long> counters, Map<String, Timing> timings) {
        public Snapshot {
            Objects.requireNonNull(counters, "counters");
            Objects.requireNonNull(timings, "timings");
            counters = ordered(new TreeMap<>(counters));
            timings = ordered(new TreeMap<>(timings));
        }
    }

    public record Timing(long count, long totalNanos, long maxNanos) {
        public Timing {
            if (count < 0L || totalNanos < 0L || maxNanos < 0L) {
                throw new IllegalArgumentException("timing values must not be negative");
            }
        }
    }

    private static final class TimingAccumulator {
        private final LongAdder count = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final LongAccumulator maxNanos = new LongAccumulator(Long::max, 0L);

        void record(long durationNanos) {
            count.increment();
            totalNanos.add(durationNanos);
            maxNanos.accumulate(durationNanos);
        }

        Timing snapshot() {
            return new Timing(count.sum(), totalNanos.sum(), maxNanos.get());
        }
    }
}
