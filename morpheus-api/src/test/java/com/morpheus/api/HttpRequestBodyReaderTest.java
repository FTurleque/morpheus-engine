package com.morpheus.api;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRequestBodyReaderTest {
    @Test
    void returnsPayloadWithinBudget() {
        byte[] payload = "morpheus".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertArrayEquals(payload, HttpRequestBodyReader.read(
                    new ByteArrayInputStream(payload), payload.length, Duration.ofSeconds(1), executor));
        }
    }

    @Test
    void mapsByteLimitFailure() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            HttpRequestBodyReader.RequestBodyException failure = assertThrows(
                    HttpRequestBodyReader.RequestBodyException.class,
                    () -> HttpRequestBodyReader.read(
                            new ByteArrayInputStream(new byte[]{1, 2}), 1, Duration.ofSeconds(1), executor));

            assertEquals(HttpRequestBodyReader.Failure.LIMIT_EXCEEDED, failure.failure());
            assertTrue(failure.getMessage().contains("exceeds 1 bytes"));
        }
    }

    @Test
    void mapsTimeoutAndClosesBlockedInput() {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream blocked = new InputStream() {
            @Override
            public int read() throws IOException {
                while (!closed.get()) {
                    try {
                        Thread.sleep(5L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
                return -1;
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            HttpRequestBodyReader.RequestBodyException failure = assertThrows(
                    HttpRequestBodyReader.RequestBodyException.class,
                    () -> HttpRequestBodyReader.read(blocked, 16, Duration.ofMillis(30), executor));

            assertEquals(HttpRequestBodyReader.Failure.READ_TIMEOUT, failure.failure());
            assertTrue(closed.get());
        }
    }

    @Test
    void mapsIoFailure() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("fixture failure");
            }
        };

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            HttpRequestBodyReader.RequestBodyException failure = assertThrows(
                    HttpRequestBodyReader.RequestBodyException.class,
                    () -> HttpRequestBodyReader.read(broken, 16, Duration.ofSeconds(1), executor));

            assertEquals(HttpRequestBodyReader.Failure.IO_FAILURE, failure.failure());
            assertEquals("cannot read request body", failure.getMessage());
        }
    }
}
