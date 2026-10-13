package com.morpheus.application.operability;

/**
 * The operational runtime of this process, for what cannot be handed one.
 *
 * <p>The launcher takes {@link #process()} once and passes it on by constructor. The static accessors remain for the
 * logging binding, which the service loader instantiates and nobody constructs, and for the classes that are not yet
 * handed a recorder: they all write to the one runtime the launcher holds, so the counters the process serves are
 * complete. A test never reads it - it builds an {@link OperationalRuntime} or an {@link OperationalMetrics} of its
 * own.</p>
 */
public final class LocalOperationalRuntime {
    public static final String LOG_MODE_ENV = OperationalLogMode.ENVIRONMENT_VARIABLE;

    /**
     * The mode the environment selects. A value that is no mode is refused by {@link OperationalLogMode}, and the
     * launcher reads it first so that the refusal is its own message rather than an initialization error here.
     */
    private static final OperationalRuntime PROCESS =
            OperationalRuntime.of(OperationalLogMode.parse(System.getenv(LOG_MODE_ENV)));

    private LocalOperationalRuntime() {
    }

    public static OperationalRuntime process() {
        return PROCESS;
    }

    public static OperationalMetrics metrics() {
        return PROCESS.metrics();
    }

    public static OperationalEventSink sink() {
        return PROCESS.sink();
    }

    public static boolean writesEvents() {
        return PROCESS.writesEvents();
    }

    public static OperationalRecorder recorder() {
        return PROCESS.recorder();
    }
}
