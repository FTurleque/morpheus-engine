package com.morpheus.integration.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server reads stdin one message at a time, so a handler that never completes used to stop the session from
 * reading anything else -- a cancellation included -- for as long as the process lived.
 */
class BoundedStdioServerTransportProviderHandlerDeadlineTest {
    private static final String ONE_NOTIFICATION = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/test\",\"params\":{}}\n";

    @Test
    void aHandlerThatNeverCompletesDoesNotBlockTheReaderForever() throws Exception {
        CountDownLatch handlerCancelled = new CountDownLatch(1);
        BoundedStdioServerTransportProvider provider = provider(Duration.ofMillis(200));

        provider.setSessionFactory(transport -> session(transport,
                message -> Mono.<Void>never().doOnCancel(handlerCancelled::countDown)));

        assertTrue(provider.awaitTermination(Duration.ofSeconds(10)),
                "a handler past its deadline must release the reader and end the session");
        assertTrue(provider.terminatedInFailure(), "a handler past its deadline fails the session closed");
        assertTrue(handlerCancelled.await(2, TimeUnit.SECONDS), "the overdue handler is cancelled");
    }

    @Test
    void aHandlerWithinItsDeadlineIsServedNormally() throws Exception {
        CountDownLatch handlerCompleted = new CountDownLatch(1);
        BoundedStdioServerTransportProvider provider = provider(Duration.ofSeconds(10));

        provider.setSessionFactory(transport -> session(transport,
                message -> Mono.delay(Duration.ofMillis(50)).then(Mono.fromRunnable(handlerCompleted::countDown))));

        assertTrue(provider.awaitTermination(Duration.ofSeconds(10)));
        assertTrue(handlerCompleted.await(2, TimeUnit.SECONDS));
        assertFalse(provider.terminatedInFailure(), "a handler within its deadline ends at EOF, not in failure");
    }

    @Test
    void theDeadlineMustBePositiveAndDefaultsToTheProductionBound() {
        IllegalArgumentException zero = assertThrows(IllegalArgumentException.class, () -> provider(Duration.ZERO));
        assertEquals("handlerDeadline must be positive", zero.getMessage());
        assertThrows(NullPointerException.class, () -> provider(null));
        assertEquals(Duration.ofMinutes(2), BoundedStdioServerTransportProvider.DEFAULT_HANDLER_DEADLINE);
    }

    private static BoundedStdioServerTransportProvider provider(Duration handlerDeadline) {
        return new BoundedStdioServerTransportProvider(
                McpJsonDefaults.getMapper(),
                new ByteArrayInputStream(ONE_NOTIFICATION.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayOutputStream(),
                4096,
                4,
                "",
                handlerDeadline);
    }

    private static McpServerSession session(
            McpServerTransport transport,
            Function<McpSchema.JSONRPCMessage, Mono<Void>> handler) {
        return new McpServerSession("deadline-session", Duration.ofSeconds(30), transport, null, Map.of(), Map.of()) {
            @Override
            public Mono<Void> handle(McpSchema.JSONRPCMessage message) {
                return handler.apply(message);
            }
        };
    }
}
