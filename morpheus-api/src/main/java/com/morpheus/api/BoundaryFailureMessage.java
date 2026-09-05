package com.morpheus.api;

import com.morpheus.application.security.ServerLocationDisclosure;

/**
 * The text of a failure MORPHEUS did not author for a caller outside the machine.
 *
 * <p>These arrive from anywhere below a route — the platform, the JDBC driver, a store — and their message is
 * written for whoever reads the server's logs, not for whoever called it. {@link java.nio.file.AccessDeniedException}
 * reports the pathname and nothing else.</p>
 *
 * <p>A value that names a filesystem location is replaced by the exception's simple name rather than scrubbed,
 * because a partially scrubbed pathname is still a pathname, and the status code already says what went wrong.
 * Both servers apply this: the local one is the internal hop behind the remote facade, so its answers reach the
 * same callers.</p>
 */
final class BoundaryFailureMessage {

    private BoundaryFailureMessage() {
    }

    static String safe(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank() || !ServerLocationDisclosure.isSafeToRelay(message)) {
            return failure.getClass().getSimpleName();
        }
        return message;
    }
}
