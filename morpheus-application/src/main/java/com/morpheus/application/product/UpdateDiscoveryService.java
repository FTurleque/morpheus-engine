package com.morpheus.application.product;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/**
 * Explicit read-only update discovery. Construction performs no I/O; callers must invoke {@link #check(URI)} with a
 * concrete manifest URI. The service never downloads or installs the advertised artifact.
 */
public final class UpdateDiscoveryService {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final Duration timeout;

    public UpdateDiscoveryService() {
        this(HttpClient.newBuilder()
                .connectTimeout(DEFAULT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), DEFAULT_TIMEOUT);
    }

    UpdateDiscoveryService(HttpClient httpClient, Duration timeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public UpdateCheckResult check(URI manifestUri) {
        Objects.requireNonNull(manifestUri, "manifestUri");
        if (!manifestUri.isAbsolute()) {
            throw new IllegalArgumentException("manifestUri must be absolute");
        }
        UpdateManifest manifest = readManifest(manifestUri);
        String currentVersion = ProductMetadata.version();
        boolean available = compareVersions(manifest.version(), currentVersion) > 0;
        return new UpdateCheckResult(
                currentVersion,
                manifest.version(),
                manifest.channel(),
                manifest.artifactUri(),
                manifest.sha256(),
                manifestUri,
                available);
    }

    UpdateManifest readManifest(URI manifestUri) {
        String scheme = manifestUri.getScheme().toLowerCase(Locale.ROOT);
        Properties properties = switch (scheme) {
            case "file" -> readFile(manifestUri);
            case "http", "https" -> readHttp(manifestUri);
            default -> throw new IllegalArgumentException(
                    "unsupported update manifest scheme: " + scheme + " (expected file, http or https)");
        };
        return new UpdateManifest(
                required(properties, "version"),
                required(properties, "channel"),
                URI.create(required(properties, "artifactUri")),
                required(properties, "sha256"));
    }

    private Properties readFile(URI uri) {
        try (InputStream input = Files.newInputStream(Path.of(uri))) {
            return load(input);
        } catch (IOException failure) {
            throw new IllegalArgumentException("cannot read update manifest: " + uri, failure);
        }
    }

    private Properties readHttp(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(timeout)
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
                return load(body);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("update manifest request interrupted: " + uri, interrupted);
        } catch (IOException failure) {
            throw new IllegalArgumentException("cannot fetch update manifest: " + uri, failure);
        }
    }

    private static Properties load(InputStream input) throws IOException {
        Properties properties = new Properties();
        properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        return properties;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing update manifest property: " + key);
        }
        return value.trim();
    }

    static int compareVersions(String left, String right) {
        ParsedVersion a = ParsedVersion.parse(left);
        ParsedVersion b = ParsedVersion.parse(right);
        int max = Math.max(a.parts.length, b.parts.length);
        for (int index = 0; index < max; index++) {
            int av = index < a.parts.length ? a.parts[index] : 0;
            int bv = index < b.parts.length ? b.parts[index] : 0;
            int compared = Integer.compare(av, bv);
            if (compared != 0) {
                return compared;
            }
        }
        if (a.preRelease == b.preRelease) {
            return 0;
        }
        return a.preRelease ? -1 : 1;
    }

    private record ParsedVersion(int[] parts, boolean preRelease) {
        static ParsedVersion parse(String value) {
            Objects.requireNonNull(value, "value");
            String normalized = value.trim();
            if (normalized.equals(ProductMetadata.DEVELOPMENT_VERSION)) {
                return new ParsedVersion(new int[]{0, 0, 0}, true);
            }
            String[] releaseAndSuffix = normalized.split("-", 2);
            String[] rawParts = releaseAndSuffix[0].split("\\.");
            if (rawParts.length == 0) {
                throw new IllegalArgumentException("invalid version: " + value);
            }
            int[] parts = new int[rawParts.length];
            for (int index = 0; index < rawParts.length; index++) {
                try {
                    parts[index] = Integer.parseInt(rawParts[index]);
                } catch (NumberFormatException failure) {
                    throw new IllegalArgumentException("invalid numeric version: " + value, failure);
                }
                if (parts[index] < 0) {
                    throw new IllegalArgumentException("version parts must be non-negative: " + value);
                }
            }
            return new ParsedVersion(parts, releaseAndSuffix.length == 2);
        }
    }
}
