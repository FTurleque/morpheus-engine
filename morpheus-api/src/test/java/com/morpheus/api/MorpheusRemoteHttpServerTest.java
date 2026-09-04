package com.morpheus.api;

import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusRemoteHttpServerTest {
    @TempDir
    Path temp;

    @Test
    void remoteFacadeRequiresBearerEnforcesRolesAndBoundsConcurrency() throws Exception {
        Path database = temp.resolve("morpheus.db");
        Path allowedWorkspaceRoot = Files.createDirectory(temp.resolve("allowed-workspaces"));
        Path allowedWorkspace = Files.createDirectories(allowedWorkspaceRoot.resolve("project"));
        Path outsideWorkspace = Files.createDirectory(temp.resolve("outside-workspace"));
        ProjectSpecificationId persistedOutsideProject = ProjectSpecificationId.generate();
        try (SqliteSpecificationKnowledgeStore store = new SqliteSpecificationKnowledgeStore(database)) {
            store.putProject(new ProjectStoreEntry(
                    persistedOutsideProject,
                    SourceLocator.file(outsideWorkspace.toString())));
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
        assertAuthFileNeverPersistsRawTokens(persistedAuth, read, write, admin);

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
                AllowedWorkspaceRoots.of(List.of(allowedWorkspaceRoot)),
                "127.0.0.1",
                0,
                auth,
                keyStore,
                RemoteHttpTestSupport.KEYSTORE_PASSWORD.toCharArray(),
                1,
                new ExternalReferenceResolverRegistry(List.of()),
                minos,
                nexus,
                project -> ChangeWriteCapabilityObservation.denied("test"))) {

            URI base = URI.create("https://127.0.0.1:" + server.port() + "/api/v1");

            HttpResponse<String> unauthenticated = send(client, base.resolve("/api/v1/health"), "GET", null, null);
            assertUnauthenticatedRequestRejected(unauthenticated);

            HttpResponse<String> health = send(client, base.resolve("/api/v1/health"), "GET", read.token(), null);
            assertHealthResponseHasSecurityHeaders(health);

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
            assertReadOnlyReasoningSucceeds(readReasoning);

            HttpResponse<String> readCannotWrite = send(client, base.resolve("/api/v1/projects"), "POST", read.token(), "{}");
            HttpResponse<String> writerReachedWriteRoute = send(client, base.resolve("/api/v1/projects"), "POST", write.token(), "{}");
            HttpResponse<String> writerCannotReadAdminMetrics = send(client, base.resolve("/api/v1/metrics"), "GET", write.token(), null);
            HttpResponse<String> readCannotRegisterWorkspace = send(
                    client, base.resolve("/api/v1/projects"), "POST", read.token(), registrationBody(allowedWorkspace));
            assertRoleEnforcementOnWriteAndAdminRoutes(
                    readCannotWrite, writerReachedWriteRoute, writerCannotReadAdminMetrics, readCannotRegisterWorkspace);

            HttpResponse<String> exactRootRegistration = send(
                    client, base.resolve("/api/v1/projects"), "POST", write.token(), registrationBody(allowedWorkspaceRoot));
            HttpResponse<String> descendantRegistration = send(
                    client, base.resolve("/api/v1/projects"), "POST", write.token(), registrationBody(allowedWorkspace));
            assertWorkspaceRegistrationSucceedsForRootAndDescendant(exactRootRegistration, descendantRegistration);

            HttpResponse<String> outsideRegistration = send(
                    client, base.resolve("/api/v1/projects"), "POST", write.token(), registrationBody(outsideWorkspace));
            assertOutsideWorkspaceRegistrationRejected(outsideRegistration, outsideWorkspace);

            Path traversingWorkspace = allowedWorkspace.resolve("..").resolve("project");
            HttpResponse<String> traversalRegistration = send(
                    client, base.resolve("/api/v1/projects"), "POST", write.token(), registrationBody(traversingWorkspace));
            assertWorkspaceTraversalRejected(traversalRegistration);

            HttpResponse<String> persistedOutsideSync = send(
                    client,
                    base.resolve("/api/v1/projects/" + persistedOutsideProject + "/sync"),
                    "POST",
                    write.token(),
                    null);
            assertPersistedOutsideProjectSyncRejected(persistedOutsideSync, outsideWorkspace);

            Path linkedWorkspace = allowedWorkspaceRoot.resolve("linked-outside");
            if (createSymlink(linkedWorkspace, outsideWorkspace)) {
                HttpResponse<String> linkedRegistration = send(
                        client, base.resolve("/api/v1/projects"), "POST", write.token(), registrationBody(linkedWorkspace));
                assertLinkedOutsideWorkspaceRejected(linkedRegistration);
            }

            String workspace = URLEncoder.encode(allowedWorkspace.toString(), StandardCharsets.UTF_8);
            String trustedPin = "0".repeat(64);
            URI probe = URI.create(base + "/provider-plugins/probe?pluginId=missing&workspace=" + workspace);
            URI pinnedProbe = URI.create(probe + "&sha256=" + trustedPin);
            HttpResponse<String> readCannotProbePlugin = send(client, probe, "POST", read.token(), null);
            HttpResponse<String> writeCannotProbePlugin = send(client, probe, "POST", write.token(), null);
            HttpResponse<String> adminCannotProbeWithGet = send(client, probe, "GET", admin.token(), null);
            assertPluginProbeRequiresAdminRole(readCannotProbePlugin, writeCannotProbePlugin, adminCannotProbeWithGet);

            HttpResponse<String> adminCannotProbeWithoutIntegrityPin = send(
                    client, probe, "POST", admin.token(), null);
            HttpResponse<String> adminCannotProbeWithMalformedIntegrityPin = send(
                    client, URI.create(probe + "&sha256=abc"), "POST", admin.token(), null);
            HttpResponse<String> adminCanProbeServerConfiguredDirectory = send(
                    client, pinnedProbe, "POST", admin.token(), null);
            assertPluginProbeIntegrityPinValidation(
                    adminCannotProbeWithoutIntegrityPin,
                    adminCannotProbeWithMalformedIntegrityPin,
                    adminCanProbeServerConfiguredDirectory);

            URI outsideProbe = URI.create(base + "/provider-plugins/probe?pluginId=missing&workspace="
                    + URLEncoder.encode(outsideWorkspace.toString(), StandardCharsets.UTF_8)
                    + "&sha256=" + trustedPin);
            HttpResponse<String> adminCannotProbeOutsideWorkspace = send(
                    client, outsideProbe, "POST", admin.token(), null);

            URI clientSelectedDirectory = URI.create(
                    pinnedProbe + "&directory=" + URLEncoder.encode(temp.resolve("attacker-plugins").toString(), StandardCharsets.UTF_8));
            HttpResponse<String> adminCannotSelectPluginRoot = send(
                    client, clientSelectedDirectory, "POST", admin.token(), null);
            assertPluginProbeWorkspaceBoundaryEnforced(adminCannotProbeOutsideWorkspace, outsideWorkspace, adminCannotSelectPluginRoot);

            HttpResponse<String> discovery = send(
                    client,
                    URI.create(base + "/provider-plugins/discover"),
                    "GET",
                    read.token(),
                    null);
            HttpResponse<String> metrics = send(client, base.resolve("/api/v1/metrics"), "GET", admin.token(), null);
            assertPluginDiscoveryAndAdminMetricsSucceed(discovery, metrics);

            HttpResponse<String> status = send(client, base.resolve("/api/v1/server/status"), "GET", read.token(), null);
            assertServerStatusExposesModeWithoutLeakingSecrets(status, read, write, admin);

            HttpResponse<String> writerCannotBackup = send(client, base.resolve("/api/v1/server/backups"), "POST", write.token(), null);
            HttpResponse<String> backup = send(client, base.resolve("/api/v1/server/backups"), "POST", admin.token(), null);
            assertBackupRequiresAdminRoleAndSucceeds(writerCannotBackup, backup);

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
                assertBoundedConcurrencyProducesThrottling(statuses);
            }

            MorpheusRemoteIdentityFile.revoke(auth, "reader");
            HttpResponse<String> revokedReader = send(
                    client, base.resolve("/api/v1/health"), "GET", read.token(), null);

            HttpResponse<String> finalStatus = send(client, base.resolve("/api/v1/server/status"), "GET", admin.token(), null);
            assertRevokedReaderRejectedAndStatusReportsThrottling(revokedReader, finalStatus);
        }
    }

    @Test
    void oversizedTlsKeystoreIsRejectedBeforePkcs12Parsing() throws Exception {
        Path auth = temp.resolve("oversized-auth.txt");
        MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        Path allowed = Files.createDirectory(temp.resolve("oversized-allowed"));
        Path keyStore = temp.resolve("oversized.p12");
        Files.write(keyStore, new byte[MorpheusRemoteHttpServer.MAX_KEYSTORE_BYTES + 1]);

        assertThrows(IllegalArgumentException.class, () -> MorpheusRemoteHttpServer.start(
                temp.resolve("oversized.db"),
                temp.resolve("oversized-backups"),
                temp.resolve("oversized-plugins"),
                AllowedWorkspaceRoots.of(List.of(allowed)),
                "127.0.0.1",
                0,
                auth,
                keyStore,
                RemoteHttpTestSupport.KEYSTORE_PASSWORD.toCharArray(),
                1,
                new ExternalReferenceResolverRegistry(List.of()),
                () -> new ExternalIntegrationStatus("MINOS", "DISABLED", false, "test", Map.of()),
                new DisabledTechnicalContextProvider("NEXUS", "test"),
                project -> ChangeWriteCapabilityObservation.denied("test")));
    }

    @Test
    void remoteProxyTimeoutExcludesOperationsWithoutCooperativeCancellation() {
        assertTrue(MorpheusRemoteHttpServer.usesBoundedUpstreamTimeout("GET", "/api/v1/health"));
        assertTrue(MorpheusRemoteHttpServer.usesBoundedUpstreamTimeout("POST", "/api/v1/reasoning/analyze"));
        assertFalse(MorpheusRemoteHttpServer.usesBoundedUpstreamTimeout("POST", "/api/v1/provider-plugins/probe"));
        assertFalse(MorpheusRemoteHttpServer.usesBoundedUpstreamTimeout("POST", "/api/v1/projects/p1/sync"));
        assertFalse(MorpheusRemoteHttpServer.usesBoundedUpstreamTimeout("POST", "/api/v1/projects"));
        assertFalse(MorpheusRemoteHttpServer.usesBoundedUpstreamTimeout("PUT", "/api/v1/anything"));
        assertFalse(MorpheusRemoteHttpServer.usesBoundedUpstreamTimeout("DELETE", "/api/v1/anything"));
    }

    private void assertAuthFileNeverPersistsRawTokens(
            String persistedAuth,
            MorpheusRemoteIdentityFile.GeneratedCredential read,
            MorpheusRemoteIdentityFile.GeneratedCredential write,
            MorpheusRemoteIdentityFile.GeneratedCredential admin) {
        assertFalse(persistedAuth.contains(read.token()));
        assertFalse(persistedAuth.contains(write.token()));
        assertFalse(persistedAuth.contains(admin.token()));
    }

    private void assertUnauthenticatedRequestRejected(HttpResponse<String> unauthenticated) {
        assertEquals(401, unauthenticated.statusCode());
        assertTrue(unauthenticated.headers().firstValue("WWW-Authenticate").orElse("").contains("Bearer"));
    }

    private void assertHealthResponseHasSecurityHeaders(HttpResponse<String> health) {
        assertEquals(200, health.statusCode());
        assertEquals("nosniff", health.headers().firstValue("X-Content-Type-Options").orElseThrow());
        assertEquals("DENY", health.headers().firstValue("X-Frame-Options").orElseThrow());
        assertFalse(health.headers().firstValue("Access-Control-Allow-Origin").isPresent());
    }

    private void assertReadOnlyReasoningSucceeds(HttpResponse<String> readReasoning) {
        assertEquals(200, readReasoning.statusCode(), readReasoning.body());
        assertTrue(readReasoning.body().contains("\"assisted\":false"), readReasoning.body());
        assertTrue(readReasoning.body().contains("\"mutated\":false"), readReasoning.body());
    }

    private void assertRoleEnforcementOnWriteAndAdminRoutes(
            HttpResponse<String> readCannotWrite,
            HttpResponse<String> writerReachedWriteRoute,
            HttpResponse<String> writerCannotReadAdminMetrics,
            HttpResponse<String> readCannotRegisterWorkspace) {
        assertEquals(403, readCannotWrite.statusCode());
        assertEquals(400, writerReachedWriteRoute.statusCode());
        assertEquals(403, writerCannotReadAdminMetrics.statusCode());
        assertEquals(403, readCannotRegisterWorkspace.statusCode());
    }

    private void assertWorkspaceRegistrationSucceedsForRootAndDescendant(
            HttpResponse<String> exactRootRegistration, HttpResponse<String> descendantRegistration) {
        assertEquals(201, exactRootRegistration.statusCode(), exactRootRegistration.body());
        assertEquals(201, descendantRegistration.statusCode(), descendantRegistration.body());
    }

    private void assertOutsideWorkspaceRegistrationRejected(HttpResponse<String> outsideRegistration, Path outsideWorkspace) {
        assertEquals(400, outsideRegistration.statusCode(), outsideRegistration.body());
        assertTrue(outsideRegistration.body().contains("outside the server-configured allowed roots"));
        assertFalse(outsideRegistration.body().contains(outsideWorkspace.toString()));
    }

    private void assertWorkspaceTraversalRejected(HttpResponse<String> traversalRegistration) {
        assertEquals(400, traversalRegistration.statusCode(), traversalRegistration.body());
        assertTrue(traversalRegistration.body().contains("workspace traversal is not allowed"));
    }

    private void assertPersistedOutsideProjectSyncRejected(HttpResponse<String> persistedOutsideSync, Path outsideWorkspace) {
        assertEquals(400, persistedOutsideSync.statusCode(), persistedOutsideSync.body());
        assertFalse(persistedOutsideSync.body().contains(outsideWorkspace.toString()));
    }

    private void assertLinkedOutsideWorkspaceRejected(HttpResponse<String> linkedRegistration) {
        assertEquals(400, linkedRegistration.statusCode(), linkedRegistration.body());
    }

    private void assertPluginProbeRequiresAdminRole(
            HttpResponse<String> readCannotProbePlugin,
            HttpResponse<String> writeCannotProbePlugin,
            HttpResponse<String> adminCannotProbeWithGet) {
        assertEquals(403, readCannotProbePlugin.statusCode());
        assertEquals(403, writeCannotProbePlugin.statusCode());
        assertEquals(405, adminCannotProbeWithGet.statusCode());
    }

    private void assertPluginProbeIntegrityPinValidation(
            HttpResponse<String> adminCannotProbeWithoutIntegrityPin,
            HttpResponse<String> adminCannotProbeWithMalformedIntegrityPin,
            HttpResponse<String> adminCanProbeServerConfiguredDirectory) {
        assertEquals(400, adminCannotProbeWithoutIntegrityPin.statusCode(), adminCannotProbeWithoutIntegrityPin.body());
        assertTrue(adminCannotProbeWithoutIntegrityPin.body().contains("PLUGIN_SHA256_REQUIRED"));
        assertEquals(400, adminCannotProbeWithMalformedIntegrityPin.statusCode(), adminCannotProbeWithMalformedIntegrityPin.body());
        assertTrue(adminCannotProbeWithMalformedIntegrityPin.body().contains("PLUGIN_SHA256_INVALID"));
        assertEquals(200, adminCanProbeServerConfiguredDirectory.statusCode(), adminCanProbeServerConfiguredDirectory.body());
        assertTrue(adminCanProbeServerConfiguredDirectory.body().contains("PLUGIN_NOT_FOUND"));
    }

    private void assertPluginProbeWorkspaceBoundaryEnforced(
            HttpResponse<String> adminCannotProbeOutsideWorkspace,
            Path outsideWorkspace,
            HttpResponse<String> adminCannotSelectPluginRoot) {
        assertEquals(400, adminCannotProbeOutsideWorkspace.statusCode(), adminCannotProbeOutsideWorkspace.body());
        assertFalse(adminCannotProbeOutsideWorkspace.body().contains(outsideWorkspace.toString()));
        assertEquals(400, adminCannotSelectPluginRoot.statusCode());
        assertTrue(adminCannotSelectPluginRoot.body().contains("SERVER_CONFIGURED_PLUGIN_DIRECTORY"));
    }

    private void assertPluginDiscoveryAndAdminMetricsSucceed(HttpResponse<String> discovery, HttpResponse<String> metrics) {
        assertEquals(200, discovery.statusCode(), discovery.body());
        assertEquals(200, metrics.statusCode());
        assertRemoteDiscoveryJsonCarriesNoServerLocation(discovery);
    }

    /**
     * The projection is verified in the SDK; this proves it survives canonical JSON serialization and the proxy,
     * which is the form a remote caller actually receives.
     */
    private void assertRemoteDiscoveryJsonCarriesNoServerLocation(HttpResponse<String> discovery) {
        String body = discovery.body();
        assertFalse(body.contains("\"directory\""),
                () -> "remote plugin discovery must not name the server plugin directory: " + body);
        assertFalse(body.contains("\"jarPath\""),
                () -> "remote plugin discovery must not carry absolute JAR pathnames: " + body);
        assertFalse(body.contains("file:"), () -> "remote plugin discovery must not carry a file: URI: " + body);

        for (String location : List.of(
                temp.toAbsolutePath().toString(),
                temp.resolve("provider-plugins").toAbsolutePath().toString(),
                System.getProperty("user.home"))) {
            assertFalse(body.contains(location), () -> "remote plugin discovery leaked a location: " + body);
            assertFalse(body.contains(location.replace('\\', '/')),
                    () -> "remote plugin discovery leaked a location: " + body);
            assertFalse(body.contains(location.replace("\\", "\\\\")),
                    () -> "remote plugin discovery leaked a JSON-escaped location: " + body);
        }
    }

    private void assertServerStatusExposesModeWithoutLeakingSecrets(
            HttpResponse<String> status,
            MorpheusRemoteIdentityFile.GeneratedCredential read,
            MorpheusRemoteIdentityFile.GeneratedCredential write,
            MorpheusRemoteIdentityFile.GeneratedCredential admin) {
        assertEquals(200, status.statusCode());
        assertTrue(status.body().contains("\"mode\":\"REMOTE\""));
        assertTrue(status.body().contains("\"transport\":\"HTTPS\""));
        assertTrue(status.body().contains("\"maxProxyResponseBytes\":" + MorpheusRemoteHttpServer.MAX_PROXY_RESPONSE_BYTES));
        assertTrue(status.body().contains("\"maxProxyInFlightBytes\":" + MorpheusRemoteHttpServer.MAX_PROXY_IN_FLIGHT_BYTES));
        assertFalse(status.body().contains(read.token()));
        assertFalse(status.body().contains(write.token()));
        assertFalse(status.body().contains(admin.token()));
        assertFalse(status.body().contains(MorpheusRemoteIdentityFile.sha256Hex(admin.token())));
        assertFalse(status.body().contains("changeit"));
    }

    private void assertBackupRequiresAdminRoleAndSucceeds(HttpResponse<String> writerCannotBackup, HttpResponse<String> backup) {
        assertEquals(403, writerCannotBackup.statusCode());
        assertEquals(201, backup.statusCode());
        assertTrue(backup.body().contains("\"integrityOk\":true"));
        assertTrue(backup.body().contains("\"schemaVersion\":17"));
        assertRemoteBackupNamesTheFileWithoutTheServerPathname(backup);
    }

    /**
     * The backup directory is server-configured and restore is offline-only, so a remote ADMIN needs the backup's
     * identity, not its location. Anything that would let the caller reconstruct the server's filesystem layout is
     * a disclosure the response does not need to make.
     */
    private void assertRemoteBackupNamesTheFileWithoutTheServerPathname(HttpResponse<String> backup) {
        String body = backup.body();
        assertTrue(body.contains("\"fileName\":"), () -> "remote backup must name the file: " + body);
        assertFalse(body.contains("\"path\":"),
                () -> "remote backup must not expose the absolute backup pathname: " + body);
        assertFalse(body.contains("file:"), () -> "remote backup must not expose a file: URI: " + body);

        for (String location : List.of(
                temp.toAbsolutePath().toString(),
                temp.resolve("backups").toAbsolutePath().toString(),
                System.getProperty("user.home"))) {
            assertFalse(body.contains(location),
                    () -> "remote backup leaked a server filesystem location: " + body);
            assertFalse(body.contains(location.replace('\\', '/')),
                    () -> "remote backup leaked a server filesystem location: " + body);
        }
    }

    private void assertBoundedConcurrencyProducesThrottling(List<Integer> statuses) {
        assertTrue(statuses.contains(201));
        assertTrue(statuses.contains(429), "expected bounded remote concurrency to produce HTTP 429: " + statuses);
        assertTrue(statuses.stream().allMatch(code -> code == 201 || code == 429));
    }

    private void assertRevokedReaderRejectedAndStatusReportsThrottling(
            HttpResponse<String> revokedReader, HttpResponse<String> finalStatus) {
        assertEquals(401, revokedReader.statusCode(), revokedReader.body());
        assertEquals(200, finalStatus.statusCode());
        assertTrue(finalStatus.body().contains("\"throttledRequests\":"));
    }

    private String registrationBody(Path workspace) {
        return new com.morpheus.application.query.compact.CanonicalJsonSerializer()
                .toJson(Map.of("workspace", workspace.toString()));
    }

    private boolean createSymlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException unsupported) {
            return false;
        }
    }

    private HttpResponse<String> send(HttpClient client, URI uri, String method, String token, String body) throws Exception {
        return RemoteHttpTestSupport.send(client, uri, method, token, body);
    }

    private Path createKeyStore() throws Exception {
        return RemoteHttpTestSupport.createKeyStore(temp.resolve("server.p12"));
    }

    private HttpClient trustedClient() throws Exception {
        return RemoteHttpTestSupport.trustedClient();
    }
}
