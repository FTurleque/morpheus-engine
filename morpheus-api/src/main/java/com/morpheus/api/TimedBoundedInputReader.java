package com.morpheus.api;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Reads a request body with both a byte bound and a wall-clock deadline. */
final class TimedBoundedInputReader {
    private TimedBoundedInputReader() {
    }

    static byte[] read(InputStream input, int maxBytes, Duration timeout, ExecutorService executor) throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(executor, "executor");
        if (maxBytes < 0 || maxBytes == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maxBytes must be between 0 and Integer.MAX_VALUE - 1");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }

        Future<byte[]> read = executor.submit(() -> input.readNBytes(maxBytes + 1));
        try {
            byte[] bytes = read.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (bytes.length > maxBytes) {
                throw new LimitExceededException("request body exceeds " + maxBytes + " bytes");
            }
            return bytes;
        } catch (TimeoutException timeoutFailure) {
            read.cancel(true);
            closeQuietly(input);
            throw new ReadTimeoutException("request body exceeded its read deadline", timeoutFailure);
        } catch (InterruptedException interrupted) {
            read.cancel(true);
            closeQuietly(input);
            Thread.currentThread().interrupt();
            throw new IOException("request body read was interrupted", interrupted);
        } catch (ExecutionException executionFailure) {
            Throwable cause = executionFailure.getCause();
            if (cause instanceof IOException ioFailure) throw ioFailure;
            if (cause instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            if (cause instanceof Error error) throw error;
            throw new IOException("request body read failed", cause);
        }
    }

    private static void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // The read task was already interrupted; close is best-effort cleanup.
        }
    }

    static final class ReadTimeoutException extends IOException {
        private ReadTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class LimitExceededException extends IOException {
        private LimitExceededException(String message) {
            super(message);
        }
    }
}
