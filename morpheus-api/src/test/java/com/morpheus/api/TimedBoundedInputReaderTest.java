package com.morpheus.api;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void rejectsPayloadPastConfiguredBound() {
        byte[] payload = new byte[65];
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThrows(TimedBoundedInputReader.LimitExceededException.class, () ->
                    TimedBoundedInputReader.read(
                            new ByteArrayInputStream(payload), 64, Duration.ofSeconds(1), executor));
        }
    }

    @Test
    void closesSlowInputWhenReadDeadlineExpires() {
        BlockingInputStream input = new BlockingInputStream();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThrows(TimedBoundedInputReader.ReadTimeoutException.class, () ->
                    TimedBoundedInputReader.read(input, 64, Duration.ofMillis(50), executor));
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
    }
}
