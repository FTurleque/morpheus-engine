package com.morpheus.application.operability;

/**
 * Process-local observability runtime. Counters are always local; optional structured logs go only to stderr.
 * No network exporter, telemetry endpoint or external service is created here.
 */
public final class LocalOperationalRuntime {
    public static final String LOG_MODE_ENV = OperationalLogMode.ENVIRONMENT_VARIABLE;

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

    /**
     * The sink of the mode the environment selects. A value that is no mode is refused by {@link OperationalLogMode},
     * and the launcher reads it first so that the refusal is its own message rather than an initialization error here.
     */
    private static OperationalEventSink createSink() {
        return switch (OperationalLogMode.parse(System.getenv(LOG_MODE_ENV))) {
            case OFF -> OperationalEventSink.noop();
            case JSON_LINES -> new StructuredOperationalEventSink(System.err);
        };
    }
}
