package com.morpheus.application.product;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.KeyStoreException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.KeyManagerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code HttpRequest.Builder#timeout(Duration)} only bounds the time to receive response headers when the body is
 * consumed through {@code HttpResponse.BodyHandlers#ofInputStream()}. A server that answers quickly and then
 * stalls mid-body used to block {@link UpdateDiscoveryService#check(URI)} indefinitely; these tests exercise a
 * real TLS server that reproduces exactly that shape.
 */
class UpdateDiscoveryServiceStalledBodyTest {
    private static final String KEYSTORE_PASSWORD = "changeit";
    private static final String CERTIFICATE_ALIAS = "morpheus-update-test";
    private static final Duration SERVICE_TIMEOUT = Duration.ofSeconds(1);

    @TempDir
    private Path tempDir;

    private HttpsServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void aServerThatStallsMidBodyFailsWithinTheConfiguredDeadlineInsteadOfHangingForever() throws Exception {
        Path keyStore = createKeyStore(tempDir.resolve("update-test.p12"));
        CountDownLatch releaseBody = new CountDownLatch(1);
        server = startServer(keyStore, exchange -> {
            byte[] validManifest = validManifestBytes();
            exchange.sendResponseHeaders(200, validManifest.length);
            exchange.getResponseBody().write(validManifest, 0, 4);
            exchange.getResponseBody().flush();
            try {
                // Longer than SERVICE_TIMEOUT on purpose: the test proves the client gives up, not that the
                // server eventually finishes. The latch is never counted down within this test.
                releaseBody.await(10, TimeUnit.SECONDS);
                exchange.getResponseBody().write(validManifest, 4, validManifest.length - 4);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });

        UpdateDiscoveryService service = new UpdateDiscoveryService(trustedClient(keyStore), SERVICE_TIMEOUT);
        URI manifestUri = URI.create("https://localhost:" + server.getAddress().getPort() + "/manifest");

        long start = System.nanoTime();
        RuntimeException failure = assertThrows(RuntimeException.class, () -> service.check(manifestUri));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertTrue(elapsed.compareTo(SERVICE_TIMEOUT.multipliedBy(3)) < 0,
                "a stalled body must fail near the configured deadline, not after the full server stall: " + elapsed);
        assertTrue(failure.getMessage() != null && failure.getMessage().contains(manifestUri.toString()),
                "the failure must name the manifest URI it was reading: " + failure.getMessage());
    }

    @Test
    void aSubsequentRequestAfterATimeoutStillSucceedsProvingTheReaderThreadAndConnectionWereReleased()
            throws Exception {
        Path keyStore = createKeyStore(tempDir.resolve("update-test-2.p12"));
        CountDownLatch releaseBody = new CountDownLatch(1);
        server = startServer(keyStore, exchange -> {
            String path = exchange.getRequestURI().getPath();
            byte[] validManifest = validManifestBytes();
            if ("/stall".equals(path)) {
                exchange.sendResponseHeaders(200, validManifest.length);
                exchange.getResponseBody().write(validManifest, 0, 4);
                exchange.getResponseBody().flush();
                try {
                    releaseBody.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
                return;
            }
            exchange.sendResponseHeaders(200, validManifest.length);
            exchange.getResponseBody().write(validManifest);
            exchange.close();
        });

        UpdateDiscoveryService service = new UpdateDiscoveryService(trustedClient(keyStore), SERVICE_TIMEOUT);
        int port = server.getAddress().getPort();

        assertThrows(RuntimeException.class,
                () -> service.check(URI.create("https://localhost:" + port + "/stall")));

        UpdateCheckResult result = service.check(URI.create("https://localhost:" + port + "/fast"));
        assertEquals("9.9.9", result.availableVersion());
    }

    @Test
    void anOversizedManifestBodyIsStillRejectedThroughTheTimedReader() throws Exception {
        Path keyStore = createKeyStore(tempDir.resolve("update-test-3.p12"));
        byte[] oversized = "version=9.9.9\n".repeat(10_000).getBytes(StandardCharsets.UTF_8);
        assertTrue(oversized.length > UpdateDiscoveryService.MAX_MANIFEST_BYTES);
        server = startServer(keyStore, exchange -> {
            exchange.sendResponseHeaders(200, oversized.length);
            exchange.getResponseBody().write(oversized);
            exchange.close();
        });

        UpdateDiscoveryService service = new UpdateDiscoveryService(trustedClient(keyStore), SERVICE_TIMEOUT);
        URI manifestUri = URI.create("https://localhost:" + server.getAddress().getPort() + "/manifest");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.check(manifestUri));
        assertTrue(failure.getMessage().contains("exceeds"), failure.getMessage());
    }

    @Test
    void aPromptNormalResponseSucceedsWellUnderTheDeadline() throws Exception {
        Path keyStore = createKeyStore(tempDir.resolve("update-test-4.p12"));
        server = startServer(keyStore, exchange -> {
            byte[] validManifest = validManifestBytes();
            exchange.sendResponseHeaders(200, validManifest.length);
            exchange.getResponseBody().write(validManifest);
            exchange.close();
        });

        UpdateDiscoveryService service = new UpdateDiscoveryService(trustedClient(keyStore), SERVICE_TIMEOUT);
        UpdateCheckResult result = service.check(
                URI.create("https://localhost:" + server.getAddress().getPort() + "/manifest"));

        assertEquals("9.9.9", result.availableVersion());
        assertTrue(result.updateAvailable());
    }

    @Test
    void interruptingTheCallingThreadWhileWaitingForABodyCancelsTheReadAndPropagatesInterruption()
            throws Exception {
        Path keyStore = createKeyStore(tempDir.resolve("update-test-5.p12"));
        CountDownLatch serverStarted = new CountDownLatch(1);
        CountDownLatch releaseBody = new CountDownLatch(1);
        server = startServer(keyStore, exchange -> {
            byte[] validManifest = validManifestBytes();
            exchange.sendResponseHeaders(200, validManifest.length);
            exchange.getResponseBody().write(validManifest, 0, 4);
            exchange.getResponseBody().flush();
            serverStarted.countDown();
            try {
                releaseBody.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });

        UpdateDiscoveryService service = new UpdateDiscoveryService(
                trustedClient(keyStore), Duration.ofSeconds(30));
        URI manifestUri = URI.create("https://localhost:" + server.getAddress().getPort() + "/manifest");
        AtomicReference<Throwable> observed = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                service.check(manifestUri);
            } catch (Throwable failure) {
                observed.set(failure);
            }
        });
        caller.start();

        assertTrue(serverStarted.await(10, TimeUnit.SECONDS), "the server must have started streaming headers");
        Thread.sleep(200);
        caller.interrupt();
        caller.join(TimeUnit.SECONDS.toMillis(10));

        assertTrue(!caller.isAlive(), "the caller thread must terminate promptly once interrupted");
        assertNotNull(observed.get(), "an interrupted read must surface a failure rather than silently succeed");
        assertTrue(observed.get() instanceof IllegalStateException,
                "interruption must be reported the same way as an interrupted request: " + observed.get());
    }

    @Test
    void aLocalFileManifestIsReadThroughTheSameBoundedParsingPath() throws Exception {
        Path manifestFile = tempDir.resolve("manifest.properties");
        Files.write(manifestFile, validManifestFileBytes());

        UpdateDiscoveryService service = new UpdateDiscoveryService(
                HttpClient.newHttpClient(), SERVICE_TIMEOUT);
        UpdateCheckResult result = service.check(manifestFile.toUri());

        assertEquals("9.9.9", result.availableVersion());
    }

    private static byte[] validManifestFileBytes() {
        String manifest = """
                version=9.9.9
                channel=stable
                artifactUri=file:///tmp/morpheus.zip
                sha256=%s
                """.formatted("a".repeat(64));
        return manifest.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] validManifestBytes() {
        String manifest = """
                version=9.9.9
                channel=stable
                artifactUri=https://example.invalid/morpheus.zip
                sha256=%s
                attestationUri=https://example.invalid/morpheus.zip.att
                """.formatted("a".repeat(64));
        return manifest.getBytes(StandardCharsets.UTF_8);
    }

    private HttpsServer startServer(Path keyStore, com.sun.net.httpserver.HttpHandler handler) throws Exception {
        HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(serverSslContext(keyStore)));
        httpsServer.createContext("/", handler);
        httpsServer.setExecutor(Executors.newCachedThreadPool());
        httpsServer.start();
        return httpsServer;
    }

    private static Path createKeyStore(Path keyStore) throws Exception {
        Path javaHome = Path.of(System.getProperty("java.home"));
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path keytool = javaHome.resolve("bin").resolve(windows ? "keytool.exe" : "keytool");
        Process process = new ProcessBuilder(
                keytool.toString(),
                "-genkeypair",
                "-alias", CERTIFICATE_ALIAS,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "2",
                "-storetype", "PKCS12",
                "-keystore", keyStore.toString(),
                "-storepass", KEYSTORE_PASSWORD,
                "-keypass", KEYSTORE_PASSWORD,
                "-dname", "CN=localhost",
                "-ext", "SAN=DNS:localhost,IP:127.0.0.1",
                "-noprompt")
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return keyStore;
    }

    private static SSLContext serverSslContext(Path keyStore)
            throws IOException, KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException,
            KeyManagementException, java.security.cert.CertificateException {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(keyStore)) {
            store.load(input, KEYSTORE_PASSWORD.toCharArray());
        }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(store, KEYSTORE_PASSWORD.toCharArray());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), null, null);
        return context;
    }

    /** A client that trusts exactly the certificate the test server was given, and nothing else. */
    private static HttpClient trustedClient(Path keyStore) throws Exception {
        KeyStore server = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(keyStore)) {
            server.load(input, KEYSTORE_PASSWORD.toCharArray());
        }
        Certificate certificate = server.getCertificate(CERTIFICATE_ALIAS);
        assertNotNull(certificate, "the generated test keystore must expose its certificate");

        KeyStore trusted = KeyStore.getInstance("PKCS12");
        trusted.load(null, null);
        trusted.setCertificateEntry(CERTIFICATE_ALIAS, certificate);

        TrustManagerFactory trustManagers =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trusted);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustManagers.getTrustManagers(), null);

        return HttpClient.newBuilder()
                .sslContext(context)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }
}
