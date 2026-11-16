package com.morpheus.api.remote;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import static com.morpheus.api.remote.RemoteHttpTestSupport.send;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client the remote facade tests share gives a TLS handshake the same budget as the request it opens.
 *
 * <p>The JDK client keeps its connect timer running until the TLS handshake is over, not only until the TCP
 * connection is accepted: a handshake the server starts late is reported as {@code HTTP connect timed out} although
 * the socket connected at once. The first handshake in a test JVM loads and compiles the TLS classes on both ends
 * while the other test classes of the module run beside it: on a loaded four-processor Windows runner it has run
 * past five seconds, where every later handshake takes milliseconds. This test
 * reproduces it without the load: the server accepts the connection and starts its handshake only after
 * {@link #HANDSHAKE_DELAY}.</p>
 */
class TrustedClientHandshakeBudgetTest {
    /** Longer than the five seconds the first handshake of a test JVM exceeded on a loaded Windows runner. */
    private static final Duration HANDSHAKE_DELAY = Duration.ofSeconds(6);
    private static final String BODY = "late";

    @TempDir
    Path temporaryDirectory;

    @Test
    void aHandshakeTheServerStartsLateIsAnsweredWithinTheBudgetOfTheRequest() throws Exception {
        Path keyStore = RemoteHttpTestSupport.createKeyStore(temporaryDirectory.resolve("remote.p12"));
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();

        try (SSLServerSocket listener = (SSLServerSocket) serverContext(keyStore).getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getLoopbackAddress());
             HttpClient client = RemoteHttpTestSupport.trustedClient(keyStore)) {
            Thread server = Thread.ofPlatform().daemon().start(() -> answerLate(listener, serverFailure));

            HttpResponse<String> response = send(
                    client, URI.create("https://127.0.0.1:" + listener.getLocalPort() + "/"), "GET", null, null);

            assertEquals(200, response.statusCode(), response::body);
            assertEquals(BODY, response.body());
            server.join(RemoteHttpTestSupport.REQUEST_TIMEOUT);
            assertFalse(server.isAlive(), "the late server must have finished its single exchange");
            assertNull(serverFailure.get(), () -> "the late server failed: " + serverFailure.get());
        }
    }

    @Test
    void theBudgetOfAHandshakeIsTheBudgetOfTheRequestItOpens() throws Exception {
        Path keyStore = RemoteHttpTestSupport.createKeyStore(temporaryDirectory.resolve("remote.p12"));
        try (HttpClient client = RemoteHttpTestSupport.trustedClient(keyStore)) {
            assertTrue(client.connectTimeout().isPresent(), "a connection the facade never answers must still end");
            assertEquals(RemoteHttpTestSupport.REQUEST_TIMEOUT, client.connectTimeout().orElseThrow());
            assertTrue(HANDSHAKE_DELAY.compareTo(RemoteHttpTestSupport.REQUEST_TIMEOUT) < 0,
                    "the late handshake must fit in the budget it proves");
        }
    }

    private static void answerLate(SSLServerSocket listener, AtomicReference<Throwable> failure) {
        try (SSLSocket socket = (SSLSocket) listener.accept()) {
            Thread.sleep(HANDSHAKE_DELAY);
            socket.startHandshake();
            readRequestHead(socket.getInputStream());
            byte[] body = BODY.getBytes(StandardCharsets.US_ASCII);
            OutputStream output = socket.getOutputStream();
            output.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " + body.length
                    + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(body);
            output.flush();
        } catch (IOException | InterruptedException | RuntimeException exception) {
            failure.set(exception);
        }
    }

    private static void readRequestHead(InputStream input) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        while (!head.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n")) {
            int next = input.read();
            if (next < 0) {
                throw new IOException("the client closed the connection before the end of its request head");
            }
            head.write(next);
        }
    }

    private static SSLContext serverContext(Path keyStore) throws Exception {
        KeyStore server = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(keyStore)) {
            server.load(input, RemoteHttpTestSupport.KEYSTORE_PASSWORD.toCharArray());
        }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(server, RemoteHttpTestSupport.KEYSTORE_PASSWORD.toCharArray());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), null, null);
        return context;
    }
}
