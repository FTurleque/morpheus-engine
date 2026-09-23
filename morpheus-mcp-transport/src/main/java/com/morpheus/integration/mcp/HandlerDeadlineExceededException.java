package com.morpheus.integration.mcp;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * An inbound message's handler did not complete within the transport's safety deadline. The server reads stdin one
 * message at a time, so such a handler had already stopped the session from reading anything else, cancellations
 * included. Cancelling it frees the reader but does not prove the work stopped, so the session is failed closed rather
 * than resumed (ADR-0106, §4).
 */
final class HandlerDeadlineExceededException extends TimeoutException {
    HandlerDeadlineExceededException(Duration deadline) {
        super("MCP STDIO handler did not complete within its " + deadline.toMillis() + " ms safety deadline");
    }
}
