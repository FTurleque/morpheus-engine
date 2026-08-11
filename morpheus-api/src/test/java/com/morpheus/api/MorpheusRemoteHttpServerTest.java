package com.morpheus.api;

import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusRemoteHttpServerTest {
    @TempDir
    Path temp;

    @Test
    void remoteFacadeRequiresBearerEnforcesRolesAndBoundsConcurrency() throws Exception {
        Path database = temp.resolve("morpheus.db");
        try (SqliteSpecificationKnowledgeStore ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // current schema
        }
        // Make backup requests long enough for a deterministic maxConcurrent=1 saturation proof.
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE m26_load(id INTEGER PRIMARY KEY, payload BLOB NOT NULL)");
            statement.execute("INSERT INTO m26_load(payload) VALUES(randomblob(8000000))");
        }

        Path auth = temp.resolve("remote-auth.txt");
        var read = MorpheusRemoteIdentityFile.create(auth, "reader", MorpheusRemoteRole.READ);
        var write = MorpheusRemoteIdentityFile.create(auth, "writer", MorpheusRemoteRole.WRITE);
        var admin = MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        String persistedAuth = java.nio.file.Files.readString(auth);
        assertFalse(persistedAuth.contains(read.token()));
        assertFalse(persistedAuth.contains(write.token()));
        assertFalse(persistedAuth.contains(admin.token()));

        Path keyStore = createKeyStore();
        Path providerPluginDirectory = temp.resolve("provider-plugins");
        HttpClient client = trustedClient();
        var minos = (com.morpheus.application.reference.ExternalIntegrationStatusProvider) () ->
                new ExternalIntegrationStatus("MINOS", "DISABLED", false, "test", Map.of());
        var nexus = new DisabledTechnicalContextProvider("NEXUS", "test");

        try (MorpheusRemoteHttpServer server = MorpheusRemoteHttpServer.start(
                database,
                temp.resolve("backups"),
                providerPluginDirectory,
                "127.0.0.1",
                0,
                auth,
                keyStore,
                "changeit".toCharArray(),
                1,
                new ExternalReferenceResolverRegistry(List.of()),
                minos,
                nexus,
                project -> ChangeWriteCapabilityObservation.denied("test"))) {

            URI base = URI.create("https://127.0.0.1:" + server.port() + "/api/v1");

            HttpResponse<String> unauthenticated = send(client, base.resolve("/api/v1/health"), "GET", null, null);
            assertEquals(401, unauthenticated.statusCode());
            assertTrue(unauthenticated.headers().firstValue("WWW-Authenticate").orElse("").contains("Bearer"));

            HttpResponse<String> health = send(client, base.resolve("/api/v1/health"), "GET", read.token(), null);
            assertEquals(200, health.statusCode());
            assertEquals("nosniff", health.headers().firstValue("X-Content-Type-Options").orElseThrow());
            assertEquals("DENY", health.headers().firstValue("X-Frame-Options").orElseThrow());
            assertFalse(health.headers().firstValue("Access-Control-Allow-Origin").isPresent());

            HttpResponse<String> readReasoning = send(
                    client,
                    base.resolve("/api/v1/reasoning/analyze"),
                    "POST",
                    read.token(),
                    """
                    {"question":"What remains authoritative?","evidence":[
                      {"id":"fact-1","kind":"PUBLISHED_FACT","subject":"history",
                       "statement":"Published history remains authoritative","provenance":{"source":"remote-test"}}
                    ],"adapterIds":[]}
                    """);
            assertEquals(200, readReasoning.statusCode(), readReasoning.body());
            assertTrue(readReasoning.body().contains("\"assisted\":false"), readReasoning.body());
            assertTrue(readReasoning.body().contains("\"mutated\":false"), readReasoning.body());

            HttpResponse<String> readCannotWrite = send(client, base.resolve("/api/v1/projects"), "POST", read.token(), "{}");
            assertEquals(403, readCannotWrite.statusCode());

            HttpResponse<String> writerReachedWriteRoute = send(client, base.resolve("/api/v1/projects"), "POST", write.token(), "{}");
            assertEquals(400, writerReachedWriteRoute.statusCode());

            HttpResponse<String> writerCannotReadAdminMetrics = send(client, base.resolve("/api/v1/metrics"), "GET", write.token(), null);
            assertEquals(403, writerCannotReadAdminMetrics.statusCode());

            String workspace = URLEncoder.encode(temp.toString(), StandardCharsets.UTF_8);
            URI probe = URI.create(base + "/provider-plugins/probe?pluginId=missing&workspace=" + workspace);
            HttpResponse<String> readCannotProbePlugin = send(client, probe, "POST", read.token(), null);
            assertEquals(403, readCannotProbePlugin.statusCode());
            HttpResponse<String> writeCannotProbePlugin = send(client, probe, "POST", write.token(), null);
            assertEquals(403, writeCannotProbePlugin.statusCode());
            HttpResponse<String> adminCannotProbeWithGet = send(client, probe, "GET", admin.token(), null);
            assertEquals(405, adminCannotProbeWithGet.statusCode());
            HttpResponse<String> adminCanProbeServerConfiguredDirectory = send(
                    client, probe, "POST", admin.token(), null);
            assertEquals(200, adminCanProbeServerConfiguredDirectory.statusCode(), adminCanProbeServerConfiguredDirectory.body());
            assertTrue(adminCanProbeServerConfiguredDirectory.body().contains("PLUGIN_NOT_FOUND"));

            URI clientSelectedDirectory = URI.create(
                    probe + "&directory=" + URLEncoder.encode(temp.resolve("attacker-plugins").toString(), StandardCharsets.UTF_8));
            HttpResponse<String> adminCannotSelectPluginRoot = send(
                    client, clientSelectedDirectory, "POST", admin.token(), null);
            assertEquals(400, adminCannotSelectPluginRoot.statusCode());
            assertTrue(adminCannotSelectPluginRoot.body().contains("SERVER_CONFIGURED_PLUGIN_DIRECTORY"));

            HttpResponse<String> discovery = send(
                    client,
                    URI.create(base + "/provider-plugins/discover"),
                    "GET",
                    read.token(),
                    null);
            assertEquals(200, discovery.statusCode(), discovery.body());

            HttpResponse<String> metrics = send(client, base.resolve("/api/v1/metrics"), "GET", admin.token(), null);
            assertEquals(200, metrics.statusCode());

            HttpResponse<String> status = send(client, base.resolve("/api/v1/server/status"), "GET", read.token(), null);
            assertEquals(200, status.statusCode());
            assertTrue(status.body().contains("\"mode\":\"REMOTE\""));
            assertTrue(status.body().contains("\"transport\":\"HTTPS\""));
            assertFalse(status.body().contains(read.token()));
            assertFalse(status.body().contains(write.token()));
            assertFalse(status.body().contains(admin.token()));
            assertFalse(status.body().contains(MorpheusRemoteIdentityFile.sha256Hex(admin.token())));
            assertFalse(status.body().contains("changeit"));

            HttpResponse<String> writerCannotBackup = send(client, base.resolve("/api/v1/server/backups"), "POST", write.token(), null);
            assertEquals(403, writerCannotBackup.statusCode());

            HttpResponse<String> backup = send(client, base.resolve("/api/v1/server/backups"), "POST", admin.token(), null);
            assertEquals(201, backup.statusCode());
            assertTrue(backup.body().contains("\"integrityOk\":true"));
            assertTrue(backup.body().contains("\"schemaVersion\":15"));

            try (var pool = Executors.newFixedThreadPool(8)) {
                List<Callable<Integer>> calls = new ArrayList<>();
                for (int index = 0; index < 8; index++) {
                    calls.add(() -> send(client, base.resolve("/api/v1/server/backups"), "POST", admin.token(), null).statusCode());
                }
                List<Integer> statuses = pool.invokeAll(calls).stream().map(future -> {
                    try {
                        return future.get();
                    } catch (Exception failure) {
                        throw new IllegalStateException(failure);
                    }
                }).toList();
                assertTrue(statuses.contains(201));
                assertTrue(statuses.contains(429), "expected bounded remote concurrency to produce HTTP 429: " + statuses);
                assertTrue(statuses.stream().allMatch(code -> code == 201 || code == 429));
            }

            HttpResponse<String> finalStatus = send(client, base.resolve("/api/v1/server/status"), "GET", admin.token(), null);
            assertEquals(200, finalStatus.statusCode());
            assertTrue(finalStatus.body().contains("\"throttledRequests\":"));
        }
    }

    private HttpResponse<String> send(HttpClient client, URI uri, String method, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Path createKeyStore() throws Exception {
        Path keyStore = temp.resolve("server.p12");
        Path javaHome = Path.of(System.getProperty("java.home"));
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path keytool = javaHome.resolve("bin").resolve(windows ? "keytool.exe" : "keytool");
        Process process = new ProcessBuilder(
                keytool.toString(),
                "-genkeypair",
                "-alias", "morpheus-test",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "2",
                "-storetype", "PKCS12",
                "-keystore", keyStore.toString(),
                "-storepass", "changeit",
                "-keypass", "changeit",
                "-dname", "CN=localhost",
                "-ext", "SAN=DNS:localhost,IP:127.0.0.1",
                "-noprompt")
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return keyStore;
    }

    private HttpClient trustedClient() throws Exception {
        TrustManager[] trustAll = {new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustAll, new SecureRandom());
        return HttpClient.newBuilder().sslContext(context).connectTimeout(Duration.ofSeconds(5)).build();
    }
}
