package com.morpheus.application.operability;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A sink whose destination is named once, after the process has read where it is.
 *
 * <p>The operational runtime chooses its sink in a static initializer of the application module, which does not know
 * the layout of the process. The launcher does, and directs this sink before anything else runs. An event emitted
 * before that has no destination and is not kept; directing the sink a second time is refused, so the destination of
 * a process cannot change under it.</p>
 */
public final class DeferredOperationalEventSink implements OperationalEventSink {
    private final AtomicReference<OperationalEventSink> destination = new AtomicReference<>();

    /** @throws IllegalStateException when a destination was already named */
    public void directTo(OperationalEventSink sink) {
        if (!destination.compareAndSet(null, Objects.requireNonNull(sink, "sink"))) {
            throw new IllegalStateException("the operational log already has a destination");
        }
    }

    @Override
    public void emit(OperationalEvent event) {
        Objects.requireNonNull(event, "event");
        OperationalEventSink named = destination.get();
        if (named != null) {
            named.emit(event);
        }
    }
}
