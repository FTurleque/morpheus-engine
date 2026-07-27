package com.morpheus.application.operability;

import java.util.Locale;

/**
 * Process-local observability runtime. Counters are always local; optional structured logs go only to stderr.
 * No network exporter, telemetry endpoint or external service is created here.
 */
public final class LocalOperationalRuntime {
    public static final String LOG_MODE_ENV = "MORPHEUS_OPERATIONAL_LOGS";

    private static final OperationalMetrics METRICS = new OperationalMetrics();
    private static final OperationalEventSink SINK = createSink();
    private static final OperationalRecorder RECORDER = new OperationalRecorder(SINK, METRICS);

    private LocalOperationalRuntime() {
    }

    public static OperationalMetrics metrics() {
        return METRICS;
    }

    public static OperationalEventSink sink() {
        return SINK;
    }

    public static OperationalRecorder recorder() {
        return RECORDER;
    }

    private static OperationalEventSink createSink() {
        String mode = System.getenv(LOG_MODE_ENV);
        if (mode == null || mode.isBlank()) {
            return OperationalEventSink.noop();
        }
        return switch (mode.trim().toLowerCase(Locale.ROOT)) {
            case "off", "none" -> OperationalEventSink.noop();
            case "json", "jsonl", "structured" -> new StructuredOperationalEventSink(System.err);
            default -> OperationalEventSink.noop();
        };
    }
}
