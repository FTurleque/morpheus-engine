package com.morpheus.api.transport;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimedBoundedInputReaderTest {
    @Test
    void readsWithinConfiguredBound() throws Exception {
        byte[] payload = "payload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertArrayEquals(payload, TimedBoundedInputReader.read(
                    new ByteArrayInputStream(payload), payload.length, Duration.ofSeconds(1), executor));
        }
    }

    @Test
    void acceptsEmptyPayloadAtZeroByteBound() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertArrayEquals(new byte[0], TimedBoundedInputReader.read(
                    new ByteArrayInputStream(new byte[0]), 0, Duration.ofSeconds(1), executor));
        }
    }

    @Test
    void rejectsPayloadPastConfiguredBound() {
        byte[] payload = new byte[65];
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThrows(TimedBoundedInputReader.LimitExceededException.class, () ->
                    TimedBoundedInputReader.read(
                            new ByteArrayInputStream(payload), 64, Duration.ofSeconds(1), executor));
        }
    }

    @Test
    void rejectsNonEmptyPayloadAtZeroByteBound() {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThrows(TimedBoundedInputReader.LimitExceededException.class, () ->
                    TimedBoundedInputReader.read(
                            new ByteArrayInputStream(new byte[]{1}), 0, Duration.ofSeconds(1), executor));
        }
    }

    @Test
    void rejectsInvalidConfigurationBeforeSubmittingRead() {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            IllegalArgumentException negativeBound = assertThrows(IllegalArgumentException.class,
                    () -> TimedBoundedInputReader.read(
                            new ByteArrayInputStream(new byte[0]), -1, Duration.ofSeconds(1), executor));
            assertEquals("maxBytes must be between 0 and Integer.MAX_VALUE - 1", negativeBound.getMessage());
            IllegalArgumentException unboundedBound = assertThrows(IllegalArgumentException.class,
                    () -> TimedBoundedInputReader.read(
                            new ByteArrayInputStream(new byte[0]), Integer.MAX_VALUE, Duration.ofSeconds(1), executor));
            assertEquals("maxBytes must be between 0 and Integer.MAX_VALUE - 1", unboundedBound.getMessage());
            IllegalArgumentException zeroTimeout = assertThrows(IllegalArgumentException.class,
                    () -> TimedBoundedInputReader.read(
                            new ByteArrayInputStream(new byte[0]), 1, Duration.ZERO, executor));
            assertEquals("timeout must be positive", zeroTimeout.getMessage());
            IllegalArgumentException negativeTimeout = assertThrows(IllegalArgumentException.class,
                    () -> TimedBoundedInputReader.read(
                            new ByteArrayInputStream(new byte[0]), 1, Duration.ofMillis(-1), executor));
            assertEquals("timeout must be positive", negativeTimeout.getMessage());
        }
    }

    @Test
    void propagatesInputIoFailure() {
        InputStream input = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("fixture failure");
            }
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            IOException failure = assertThrows(IOException.class, () ->
                    TimedBoundedInputReader.read(input, 64, Duration.ofSeconds(1), executor));
            assertEquals("fixture failure", failure.getMessage());
        }
    }

    @Test
    void closesSlowInputWhenReadDeadlineExpires() throws Exception {
        BlockingInputStream input = new BlockingInputStream();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThrows(TimedBoundedInputReader.ReadTimeoutException.class, () ->
                    TimedBoundedInputReader.read(input, 64, Duration.ofMillis(50), executor));
            assertTrue(input.awaitClosed(), "timeout must close the request input to unblock the read task");
        }
    }

    private static final class BlockingInputStream extends InputStream {
        private final CountDownLatch closed = new CountDownLatch(1);

        @Override
        public int read() throws IOException {
            try {
                closed.await();
                return -1;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("blocking input interrupted", interrupted);
            }
        }

        @Override
        public void close() {
            closed.countDown();
        }

        private boolean awaitClosed() throws InterruptedException {
            return closed.await(1, TimeUnit.SECONDS);
        }
    }
}
