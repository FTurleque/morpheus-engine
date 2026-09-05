package com.morpheus.api;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Ephemeral TLS material and a bearer-aware client, shared by the remote HTTPS facade tests. */
final class RemoteHttpTestSupport {
    static final String KEYSTORE_PASSWORD = "changeit";
    private static final String CERTIFICATE_ALIAS = "morpheus-test";

    private RemoteHttpTestSupport() {
    }

    static HttpResponse<String> send(HttpClient client, URI uri, String method, String token, String body)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    static Path createKeyStore(Path keyStore) throws Exception {
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

    /**
     * A client that trusts exactly the certificate the test server was given, and nothing else.
     *
     * <p>The obvious shortcut is a TrustManager that accepts every chain. It works, and it quietly removes the
     * only part of TLS these tests could still get wrong: a facade that served the wrong certificate, or none,
     * would pass. Trusting this one certificate keeps the handshake a real assertion -- and keeps a
     * trust-anything pattern out of a shared helper, where it would be copied into the next test that needs a
     * client.</p>
     */
    static HttpClient trustedClient(Path keyStore) throws Exception {
        return HttpClient.newBuilder()
                .sslContext(trustedContext(keyStore))
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** The same pinned trust, for tests that need a raw {@code SSLSocket} rather than an {@code HttpClient}. */
    static SSLContext trustedContext(Path keyStore) throws Exception {
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
        return context;
    }
}
