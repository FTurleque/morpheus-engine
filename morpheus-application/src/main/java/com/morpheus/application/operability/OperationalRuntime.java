package com.morpheus.application.operability;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The counters, the sink and the recorder of one process, as a value its launcher hands to what records or observes.
 *
 * <p>Two runtimes share nothing: a component that was handed one writes to it alone, which is what lets a test read
 * the counters of the code it ran and of nothing else. No network exporter, telemetry endpoint or external service is
 * created here.</p>
 */
public final class OperationalRuntime {
    private final OperationalLogMode mode;
    private final OperationalMetrics metrics = new OperationalMetrics();
    private final DeferredOperationalEventSink logFile = new DeferredOperationalEventSink();
    private final OperationalEventSink sink;
    private final OperationalRecorder recorder;

    private OperationalRuntime(OperationalLogMode mode, PrintStream standardError) {
        this.mode = Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(standardError, "standardError");
        this.sink = switch (mode) {
            case OFF -> OperationalEventSink.noop();
            case JSON_LINES -> new StructuredOperationalEventSink(standardError);
            case FILE -> logFile;
        };
        this.recorder = new OperationalRecorder(sink, metrics);
    }

    /** A runtime in the given mode; the JSON-lines mode writes to the standard error of the process. */
    public static OperationalRuntime of(OperationalLogMode mode) {
        return of(mode, System.err);
    }

    public static OperationalRuntime of(OperationalLogMode mode, PrintStream standardError) {
        return new OperationalRuntime(mode, standardError);
    }

    public OperationalMetrics metrics() {
        return metrics;
    }

    public OperationalEventSink sink() {
        return sink;
    }

    public OperationalRecorder recorder() {
        return recorder;
    }

    /** Whether the sink writes anything: a caller that would format a record for nothing asks before it does. */
    public boolean writesEvents() {
        return mode != OperationalLogMode.OFF;
    }

    /**
     * Names the directory of the log file, once, in the mode that writes one. The launcher calls it before anything
     * else runs: this module does not know the layout of the process, and an event emitted earlier is not kept.
     *
     * @throws OperationalLogFileException when the file cannot be written there
     * @throws IllegalStateException when the mode writes no file, or the directory was already named
     */
    public void writeLogFileIn(Path logsDirectory) {
        if (mode != OperationalLogMode.FILE) {
            throw new IllegalStateException("the operational log mode writes no file");
        }
        logFile.directTo(RotatingOperationalLogFile.open(logsDirectory, metrics));
    }
}
