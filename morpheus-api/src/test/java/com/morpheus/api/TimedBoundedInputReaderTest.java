package com.morpheus.api;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Duration;
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
}
