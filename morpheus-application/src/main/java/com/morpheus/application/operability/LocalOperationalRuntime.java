package com.morpheus.application.operability;

import java.nio.file.Path;

/**
 * Process-local observability runtime. Counters are always local; optional structured logs go to stderr or to a file
 * of the logs directory. No network exporter, telemetry endpoint or external service is created here.
 */
public final class LocalOperationalRuntime {
    public static final String LOG_MODE_ENV = OperationalLogMode.ENVIRONMENT_VARIABLE;

    private static final OperationalMetrics METRICS = new OperationalMetrics();
    private static final OperationalLogMode MODE = OperationalLogMode.parse(System.getenv(LOG_MODE_ENV));
    private static final DeferredOperationalEventSink LOG_FILE = new DeferredOperationalEventSink();
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

    /** Whether the sink writes anything: a caller that would format a record for nothing asks before it does. */
    public static boolean writesEvents() {
        return MODE != OperationalLogMode.OFF;
    }

    public static OperationalRecorder recorder() {
        return RECORDER;
    }

    /**
     * Names the directory of the log file, once, in the mode that writes one. The launcher calls it before anything
     * else runs: this module does not know the layout of the process, and an event emitted earlier is not kept.
     *
     * @throws OperationalLogFileException when the file cannot be written there
     * @throws IllegalStateException when the mode writes no file, or the directory was already named
     */
    public static void writeLogFileIn(Path logsDirectory) {
        if (MODE != OperationalLogMode.FILE) {
            throw new IllegalStateException("the operational log mode writes no file");
        }
        LOG_FILE.directTo(RotatingOperationalLogFile.open(logsDirectory, METRICS));
    }

    /**
     * The sink of the mode the environment selects. A value that is no mode is refused by {@link OperationalLogMode},
     * and the launcher reads it first so that the refusal is its own message rather than an initialization error here.
     */
    private static OperationalEventSink createSink() {
        return switch (MODE) {
            case OFF -> OperationalEventSink.noop();
            case JSON_LINES -> new StructuredOperationalEventSink(System.err);
            case FILE -> LOG_FILE;
        };
    }
}
