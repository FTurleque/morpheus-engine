package com.morpheus.application.operability;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable local operational event. Attribute ordering is canonical for stable diagnostics. */
public record OperationalEvent(
        Instant occurredAt,
        OperationalEventLevel level,
        OperationalEventCode code,
        Map<String, String> attributes) {

    public OperationalEvent {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(attributes, "attributes");
        TreeMap<String, String> canonical = new TreeMap<>();
        attributes.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("operational event attribute names must not be blank");
            }
            canonical.put(key.trim(), Objects.requireNonNull(value, "operational event attribute value"));
        });
        attributes = Map.copyOf(canonical);
    }
}
