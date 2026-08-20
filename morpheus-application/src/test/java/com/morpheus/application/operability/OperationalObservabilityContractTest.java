package com.morpheus.application.operability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalObservabilityContractTest {

    @TempDir
    Path tempDir;

    @Test
    void structuredSinkRedactsSecretsAndAbsolutePathsBeforeWritingJsonLines() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        StructuredOperationalEventSink sink = new StructuredOperationalEventSink(output);
        String secret = "super-secret-token-value";
        String absolutePath = tempDir.resolve("morpheus.db").toAbsolutePath().toString();

        sink.emit(new OperationalEvent(
                Instant.parse("2026-07-26T18:00:00Z"),
                OperationalEventLevel.ERROR,
                OperationalEventCode.DATABASE_NOT_READY,
                Map.of(
                        "databasePath", absolutePath,
                        "apiToken", secret,
                        "message", "authorization=Bearer123 retry=false",
                        "projectId", "project-1")));

        String json = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(json.startsWith("{\"timestamp\":\"2026-07-26T18:00:00Z\""));
        assertTrue(json.contains("\"code\":\"DATABASE_NOT_READY\""));
        assertTrue(json.contains("\"databasePath\":\"<path-redacted>\""));
        assertTrue(json.contains("\"apiToken\":\"<redacted>\""));
        assertTrue(json.contains("authorization=<redacted>"));
        assertTrue(json.contains("\"projectId\":\"project-1\""));
        assertFalse(json.contains(secret));
        assertFalse(json.contains(absolutePath));
    }

    @Test
    void freeTextAuthorizationHeadersNeverExposeBearerOrBasicCredentials() {
        SensitiveValueRedactor redactor = new SensitiveValueRedactor();
        String bearer = "bearer-token-value";
        String basic = "dXNlcjpwYXNzd29yZA==";

        String bearerMessage = redactor.redact(
                "message", "upstream failed Authorization: Bearer " + bearer + " retry=false");
        String basicMessage = redactor.redact(
                "detail", "authorization=Basic " + basic + "; status=401");

        assertEquals("upstream failed Authorization: <redacted> retry=false", bearerMessage);
        assertEquals("authorization=<redacted>; status=401", basicMessage);
        assertFalse(bearerMessage.contains(bearer));
        assertFalse(basicMessage.contains(basic));
        assertFalse(bearerMessage.contains("Bearer"));
        assertFalse(basicMessage.contains("Basic"));
    }

    @Test
    void operationalMetricsExposeStableProcessLocalCountersAndTimings() {
        OperationalMetrics metrics = new OperationalMetrics();
        metrics.increment("sync.success");
        metrics.increment("sync.success");
        metrics.increment("sync.failure");
        metrics.recordDurationNanos("sync.duration", 10L);
        metrics.recordDurationNanos("sync.duration", 30L);

        OperationalMetrics.Snapshot snapshot = metrics.snapshot();
        assertEquals(2L, snapshot.counters().get("sync.success"));
        assertEquals(1L, snapshot.counters().get("sync.failure"));
        assertEquals(new OperationalMetrics.Timing(2L, 40L, 30L), snapshot.timings().get("sync.duration"));
    }

    @Test
    void eventAttributesAreCanonicalAndImmutable() {
        OperationalEvent event = new OperationalEvent(
                Instant.parse("2026-07-26T18:00:00Z"),
                OperationalEventLevel.INFO,
                OperationalEventCode.SYNC_COMPLETED,
                Map.of("zeta", "2", "alpha", "1"));

        assertEquals(java.util.List.of("alpha", "zeta"), java.util.List.copyOf(event.attributes().keySet()));
    }
}
