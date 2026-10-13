package com.morpheus.api;

import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A request that dies of a failure nobody foresaw is still answered with a generic internal error, and the server now
 * keeps something of it.
 *
 * <p>The caller is told nothing, on purpose. The server kept nothing either: no counter, no event. An operator could
 * not learn that requests were ending in a 500, let alone of what kind. The counter is read here where an operator
 * reads it - in the metrics the same server serves - so the test observes the product and not its internals.</p>
 */
class UnforeseenFailureTraceTest {
    private static final String COUNTER = "failure.suppressed.http-internal-error";
    private static final Pattern VALUE = Pattern.compile("\"" + Pattern.quote(COUNTER) + "\":(\\d+)");

    @TempDir
    Path temporaryDirectory;

    @Test
    void anUnforeseenFailureIsAnsweredGenericallyAndCountedInTheMetricsTheServerServes() throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("unforeseen.db"), "127.0.0.1", 0,
                new ExternalReferenceResolverRegistry(List.of()),
                () -> {
                    throw new UnsupportedOperationException("status source at /srv/morpheus/minos is gone");
                });
             HttpClient client = HttpClient.newHttpClient()) {
            long before = counter(client, server);

            HttpResponse<String> first = get(client, server, "/integrations/minos/status");
            HttpResponse<String> second = get(client, server, "/integrations/minos/status");

            assertEquals(500, first.statusCode(), first::body);
            assertEquals("{\"apiVersion\":\"v1\",\"error\":{\"code\":\"INTERNAL_ERROR\",\"message\":"
                    + "\"internal MORPHEUS API error\",\"details\":{}}}", first.body());
            assertFalse(first.body().contains("/srv/"), first::body);
            assertEquals(500, second.statusCode(), second::body);
            assertEquals(before + 2, counter(client, server),
                    "each request answered with a generic internal error must move the counter by one");
        }
    }

    @Test
    void aRequestThatIsRefusedByNameDoesNotMoveTheCounter() throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("foreseen.db"), "127.0.0.1", 0);
             HttpClient client = HttpClient.newHttpClient()) {
            long before = counter(client, server);

            assertEquals(404, get(client, server, "/nowhere").statusCode());
            assertEquals(405, client.send(HttpRequest.newBuilder(URI.create(server.baseUri() + "/version"))
                    .method("DELETE", HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode());

            assertEquals(before, counter(client, server), "a refusal the server names is not an unforeseen failure");
        }
    }

    /** The counter as the metrics route serves it; absent means it never moved. */
    private static long counter(HttpClient client, MorpheusHttpServer server) throws Exception {
        HttpResponse<String> metrics = get(client, server, "/metrics");
        assertEquals(200, metrics.statusCode(), metrics::body);
        assertTrue(metrics.body().contains("\"counters\""), metrics::body);
        Matcher value = VALUE.matcher(metrics.body());
        return value.find() ? Long.parseLong(value.group(1)) : 0L;
    }

    private static HttpResponse<String> get(HttpClient client, MorpheusHttpServer server, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(server.baseUri() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
