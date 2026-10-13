package com.morpheus.application.operability;

/**
 * The kinds of failure the product deliberately does not propagate, each under the name its counter and its events
 * carry.
 *
 * <p>A failure that is set aside so that something else can go on - an optional adapter that does not load, an answer
 * that becomes a generic error - used to leave nothing behind: no counter, no event, and after the process ended, no
 * trace that anything had failed. The list is closed so that a counter's name is decided here and not at each catch.
 * A non-zero counter means the product went on without something; {@code docs/developer/OPERABILITY.md} says what, per
 * family.</p>
 */
public enum SuppressedFailureFamily {
    /** An optional reasoning adapter present on the class path could not be loaded or described. */
    ADAPTER_DISCOVERY("adapter-discovery", OperationalEventLevel.WARNING),
    /** An HTTP request ended in a failure nothing had foreseen and was answered with a generic internal error. */
    HTTP_INTERNAL_ERROR("http-internal-error", OperationalEventLevel.ERROR);

    private final String key;
    private final OperationalEventLevel level;

    SuppressedFailureFamily(String key, OperationalEventLevel level) {
        this.key = key;
        this.level = level;
    }

    public String key() {
        return key;
    }

    /** The counter that moves each time a failure of this family is set aside. */
    public String counter() {
        return "failure.suppressed." + key;
    }

    OperationalEventLevel level() {
        return level;
    }
}
