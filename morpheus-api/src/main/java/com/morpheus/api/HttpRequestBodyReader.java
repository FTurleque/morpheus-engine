package com.morpheus.api;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * Bounded, deadline-aware request-body read, with each way it can fail named.
 *
 * <p>{@link MorpheusHttpRequestDecoder} is its caller: every local router reads its body through the decoder, and the
 * decoder turns a {@link RequestBodyException} into the {@code 400} a client sees.</p>
 */
final class HttpRequestBodyReader {

    private HttpRequestBodyReader() {
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
