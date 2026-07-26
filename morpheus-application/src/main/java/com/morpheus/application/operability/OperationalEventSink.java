package com.morpheus.application.operability;

import java.util.Objects;

@FunctionalInterface
public interface OperationalEventSink {
    void emit(OperationalEvent event);

    static OperationalEventSink noop() {
        return event -> Objects.requireNonNull(event, "event");
    }
}
