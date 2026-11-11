package com.morpheus.cli.command;

import com.morpheus.application.product.UpdateDiscoveryService;
import com.morpheus.application.product.UpdateManifestSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Reads an update manifest from a local file or over HTTPS, for {@code update-check}. The client never follows a
 * redirect: a {@code 3xx} answer is refused by its status like any other answer outside {@code 2xx}.
 */
final class UpdateManifestFetcher implements UpdateManifestSource {
    private final HttpClient httpClient;

    UpdateManifestFetcher() {
        this(HttpClient.newBuilder());
    }

    UpdateManifestFetcher(HttpClient.Builder client) {
        this.httpClient = Objects.requireNonNull(client, "client")
                .connectTimeout(UpdateDiscoveryService.DEFAULT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public byte[] read(URI manifestUri, int byteLimit, Duration deadline) {
        String scheme = manifestUri.getScheme().toLowerCase(Locale.ROOT);
        return switch (scheme) {
            case "file" -> readFile(manifestUri, byteLimit);
            case "https" -> readHttps(manifestUri, byteLimit, deadline);
            default -> throw new IllegalArgumentException(
                    "unsupported update manifest scheme: " + scheme + " (expected file or https)");
        };
    }

    private static byte[] readFile(URI uri, int byteLimit) {
        try (InputStream input = Files.newInputStream(Path.of(uri))) {
            return input.readNBytes(byteLimit + 1);
        } catch (IOException failure) {
            throw new IllegalArgumentException("cannot read update manifest: " + uri, failure);
        }
    }

    private byte[] readHttps(URI uri, int byteLimit, Duration deadline) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(deadline)
                .header("Accept", "text/plain, text/x-java-properties")
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw new IllegalArgumentException(
                        "update manifest request failed with HTTP " + response.statusCode() + ": " + uri);
            }
            try (InputStream body = response.body()) {
                return readBodyWithDeadline(body, uri, byteLimit, deadline);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("update manifest request interrupted: " + uri, interrupted);
        } catch (IOException failure) {
            throw new IllegalArgumentException("cannot fetch update manifest: " + uri, failure);
        }
    }

    /**
     * {@link HttpRequest.Builder#timeout(Duration)} only bounds the time to receive the response headers when
     * the body is consumed through {@link HttpResponse.BodyHandlers#ofInputStream()}: a server that answers
     * quickly and then stalls or trickles the body can otherwise block this thread past that deadline
     * indefinitely. The read runs on its own thread with a wall-clock deadline; on timeout the underlying stream
     * is closed to unblock the read (interrupting a thread parked in socket I/O does not reliably do so) before
     * the deadline is reported as a failure.
     */
    private static byte[] readBodyWithDeadline(InputStream body, URI uri, int byteLimit, Duration deadline)
            throws IOException, InterruptedException {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<byte[]> read = executor.submit(() -> body.readNBytes(byteLimit + 1));
            try {
                return read.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException timedOut) {
                read.cancel(true);
                closeQuietly(body);
                throw new IllegalArgumentException(
                        "update manifest body did not complete within " + deadline + ": " + uri, timedOut);
            } catch (InterruptedException interrupted) {
                read.cancel(true);
                closeQuietly(body);
                throw interrupted;
            } catch (ExecutionException executionFailure) {
                // body.readNBytes(int) only declares IOException; whatever actually failed the read is
                // preserved as the cause either way, and readHttps's own IOException handler already turns
                // this into a clean IllegalArgumentException naming the manifest URI.
                throw new IOException("cannot fetch update manifest: " + uri, executionFailure.getCause());
            }
        }
    }

    private static void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // Best-effort cleanup after a timed-out or interrupted read.
        }
    }
}
