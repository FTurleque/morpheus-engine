package com.morpheus.api;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared bounded/deadline-aware request-body boundary for HTTP extension routes.
 *
 * <p>The route executor is process-lifetime by design: it only creates virtual reader tasks on demand and prevents
 * every independently registered HTTP context from silently falling back to an unbounded wall-clock read.</p>
 */
final class HttpRequestBodyReader {
    private static final ExecutorService ROUTE_BODY_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private HttpRequestBodyReader() {
    }

    static byte[] read(HttpExchange exchange) {
        Objects.requireNonNull(exchange, "exchange");
        return read(
                exchange.getRequestBody(),
                MorpheusHttpServer.MAX_REQUEST_BODY_BYTES,
                MorpheusHttpServer.REQUEST_BODY_READ_TIMEOUT,
                ROUTE_BODY_EXECUTOR);
    }

    static byte[] read(InputStream input, int maxBytes, Duration timeout, ExecutorService executor) {
        try {
            return TimedBoundedInputReader.read(input, maxBytes, timeout, executor);
        } catch (TimedBoundedInputReader.LimitExceededException tooLarge) {
            throw new RequestBodyException(
                    Failure.LIMIT_EXCEEDED,
                    "request body exceeds " + maxBytes + " bytes",
                    tooLarge);
        } catch (TimedBoundedInputReader.ReadTimeoutException timeoutFailure) {
            throw new RequestBodyException(
                    Failure.READ_TIMEOUT,
                    "request body exceeded its read deadline",
                    timeoutFailure);
        } catch (IOException failure) {
            throw new RequestBodyException(
                    Failure.IO_FAILURE,
                    "cannot read request body",
                    failure);
        }
    }

    enum Failure {
        LIMIT_EXCEEDED,
        READ_TIMEOUT,
        IO_FAILURE
    }

    static final class RequestBodyException extends IllegalArgumentException {
        private final Failure failure;

        private RequestBodyException(Failure failure, String message, Throwable cause) {
            super(message, cause);
            this.failure = Objects.requireNonNull(failure, "failure");
        }

        Failure failure() {
            return failure;
        }
    }
}
