package com.morpheus.application.product;

import java.io.ByteArrayInputStream;
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
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Explicit read-only update discovery. Construction performs no I/O; callers must invoke {@link #check(URI)} with a
 * concrete manifest URI. The service never downloads or installs the advertised artifact. Remote manifests require
 * HTTPS, advertise only HTTPS artifacts, and must provide an HTTPS provenance attestation URI. Local manifests use the
 * file scheme and remain available for explicit diagnostics and test fixtures.
 */
public final class UpdateDiscoveryService {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    public static final int MAX_MANIFEST_BYTES = 64 * 1024;
    private static final Pattern NUMERIC_IDENTIFIER_PATTERN = Pattern.compile("\\d+");

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
            case "https" -> readHttp(manifestUri);
            case "http" -> throw new IllegalArgumentException(
                    "insecure update manifest scheme: http (expected file or https)");
            default -> throw new IllegalArgumentException(
                    "unsupported update manifest scheme: " + scheme + " (expected file or https)");
        };
        UpdateManifest manifest = new UpdateManifest(
                required(properties, "version"),
                required(properties, "channel"),
                URI.create(required(properties, "artifactUri")),
                required(properties, "sha256"),
                optionalUri(properties, "attestationUri"));
        manifest.requireRemoteTrust(manifestUri);
        return manifest;
    }

    private Properties readFile(URI uri) {
        try (InputStream input = Files.newInputStream(Path.of(uri))) {
            return parseBoundedManifest(input.readNBytes(MAX_MANIFEST_BYTES + 1), uri);
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
                return parseBoundedManifest(readBodyWithDeadline(body, uri), uri);
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
     * indefinitely. The read runs on its own thread with a wall-clock deadline of {@link #timeout}; on timeout
     * the underlying stream is closed to unblock the read (interrupting a thread parked in socket I/O does not
     * reliably do so) before the deadline is reported as a failure.
     */
    private byte[] readBodyWithDeadline(InputStream body, URI uri) throws IOException, InterruptedException {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<byte[]> read = executor.submit(() -> body.readNBytes(MAX_MANIFEST_BYTES + 1));
            try {
                return read.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException timedOut) {
                read.cancel(true);
                closeQuietly(body);
                throw new IllegalArgumentException(
                        "update manifest body did not complete within " + timeout + ": " + uri, timedOut);
            } catch (InterruptedException interrupted) {
                read.cancel(true);
                closeQuietly(body);
                throw interrupted;
            } catch (ExecutionException executionFailure) {
                Throwable cause = executionFailure.getCause();
                if (cause instanceof IOException ioFailure) {
                    throw ioFailure;
                }
                if (cause instanceof RuntimeException runtimeFailure) {
                    throw runtimeFailure;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IOException("cannot fetch update manifest: " + uri, cause);
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

    private static Properties parseBoundedManifest(byte[] payload, URI source) throws IOException {
        if (payload.length > MAX_MANIFEST_BYTES) {
            throw new IllegalArgumentException(
                    "update manifest exceeds " + MAX_MANIFEST_BYTES + " bytes: " + source);
        }
        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(payload), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing update manifest property: " + key);
        }
        return value.trim();
    }

    private static Optional<URI> optionalUri(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            return Optional.of(URI.create(value.trim()));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("invalid update manifest URI property: " + key, failure);
        }
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
        if (a.preRelease == null && b.preRelease == null) {
            return 0;
        }
        if (a.preRelease == null) {
            return 1;
        }
        if (b.preRelease == null) {
            return -1;
        }
        return comparePreRelease(a.preRelease, b.preRelease);
    }

    private static int comparePreRelease(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int common = Math.min(a.length, b.length);
        for (int index = 0; index < common; index++) {
            String av = a[index];
            String bv = b[index];
            boolean an = NUMERIC_IDENTIFIER_PATTERN.matcher(av).matches();
            boolean bn = NUMERIC_IDENTIFIER_PATTERN.matcher(bv).matches();
            int compared;
            if (an && bn) {
                compared = compareNumericIdentifier(av, bv);
            } else if (an != bn) {
                compared = an ? -1 : 1;
            } else {
                compared = av.compareTo(bv);
            }
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(a.length, b.length);
    }

    private static int compareNumericIdentifier(String left, String right) {
        String a = stripLeadingZeroes(left);
        String b = stripLeadingZeroes(right);
        int length = Integer.compare(a.length(), b.length());
        return length != 0 ? length : a.compareTo(b);
    }

    private static String stripLeadingZeroes(String value) {
        int index = 0;
        while (index < value.length() - 1 && value.charAt(index) == '0') {
            index++;
        }
        return value.substring(index);
    }

    private record ParsedVersion(int[] parts, String preRelease) {
        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ParsedVersion that)) return false;
            return Arrays.equals(parts, that.parts) && Objects.equals(preRelease, that.preRelease);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(parts), preRelease);
        }

        @Override
        public String toString() {
            return "ParsedVersion[parts=" + Arrays.toString(parts) + ", preRelease=" + preRelease + "]";
        }

        static ParsedVersion parse(String value) {
            Objects.requireNonNull(value, "value");
            String normalized = value.trim();
            if (normalized.equals(ProductMetadata.DEVELOPMENT_VERSION)) {
                return new ParsedVersion(new int[]{0, 0, 0}, "development");
            }
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("version must not be blank");
            }
            String withoutBuildMetadata = normalized.split("\\+", 2)[0];
            String[] releaseAndSuffix = withoutBuildMetadata.split("-", 2);
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
            String preRelease = releaseAndSuffix.length == 2 ? releaseAndSuffix[1] : null;
            if (preRelease != null && (preRelease.isBlank() || !preRelease.matches("[0-9A-Za-z.-]+"))) {
                throw new IllegalArgumentException("invalid prerelease version: " + value);
            }
            return new ParsedVersion(parts, preRelease);
        }
    }
}
