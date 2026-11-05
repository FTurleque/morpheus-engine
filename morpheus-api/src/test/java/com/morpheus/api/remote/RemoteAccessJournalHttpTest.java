package com.morpheus.api.remote;

import com.morpheus.api.AllowedWorkspaceRoots;
import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.operability.OperationalLogMode;
import com.morpheus.application.operability.OperationalRuntime;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The remote server says who was authorized to do what.
 *
 * <p>It authenticated a request into a local variable and kept only the role: after a write token was misused,
 * nothing said which principal had registered a project, started a sync or taken a backup. Asserted here through a
 * real server, because the record is written where the request ends and that is not one place.</p>
 */
class RemoteAccessJournalHttpTest {
    @TempDir
    Path temp;

    @Test
    void anAuthorizedRequestIsAttributedARefusedOneTooAndAnUnauthenticatedOneLeavesNothing() throws Exception {
        Path auth = temp.resolve("remote-auth.txt");
        var reader = MorpheusRemoteIdentityFile.create(auth, "reader", MorpheusRemoteRole.READ);
        var writer = MorpheusRemoteIdentityFile.create(auth, "writer", MorpheusRemoteRole.WRITE);
        MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        Path journal = temp.resolve(RemoteAccessJournal.FILE_NAME);
        ProjectSpecificationId unknownProject = ProjectSpecificationId.generate();
        HttpClient client = RemoteHttpTestSupport.trustedClient(
                RemoteHttpTestSupport.createKeyStore(temp.resolve("server.p12")));

        try (MorpheusRemoteHttpServer server = start(auth)) {
            URI base = URI.create("https://127.0.0.1:" + server.port());
            assertEquals(List.of(), Files.readAllLines(journal), "the journal exists and is empty before any request");

            assertEquals(401, send(client, base, "/api/v1/health", "GET", null, null).statusCode());
            assertEquals(401, send(client, base, "/api/v1/health", "GET", "not-a-token", null).statusCode());
            assertEquals(404, send(client, base, "/api/v1/nowhere", "GET", reader.token(), null).statusCode());
            assertEquals(List.of(), Files.readAllLines(journal),
                    "a caller without a principal, or a request for no route, cannot fill the journal");

            HttpResponse<String> read = send(client, base, "/api/v1/health", "GET", reader.token(), null);
            HttpResponse<String> refused = send(client, base, "/api/v1/projects", "POST", reader.token(), "{}");
            HttpResponse<String> written = send(client, base, "/api/v1/projects", "POST", writer.token(), "{}");
            HttpResponse<String> onAPath = send(
                    client, base, "/api/v1/projects/" + unknownProject + "/sync", "POST", writer.token(), null);
            assertEquals(200, read.statusCode(), read.body());
            assertEquals(403, refused.statusCode(), refused.body());
            assertEquals(400, written.statusCode(), written.body());

            List<String> lines = linesOnceThereAre(6, journal);
            assertEquals(6, lines.size(), lines::toString);
            List<String> ofRead = recordsOf(read, lines, 1);
            assertRecord(ofRead.get(0), "completed", read, "reader", "READ", "READ", "GET", "health", "200");
            assertTrue(ofRead.get(0).contains("\"level\":\"INFO\""), ofRead.get(0));
            List<String> ofRefused = recordsOf(refused, lines, 1);
            assertRecord(ofRefused.get(0), "completed", refused, "reader", "READ", "WRITE", "POST", "projects", "403");
            assertTrue(ofRefused.get(0).contains("\"level\":\"WARNING\""), ofRefused.get(0));
            List<String> ofWritten = recordsOf(written, lines, 2);
            assertRecord(ofWritten.get(0), "accepted", written, "writer", "WRITE", "WRITE", "POST", "projects", null);
            assertRecord(ofWritten.get(1), "completed", written, "writer", "WRITE", "WRITE", "POST", "projects", "400");
            List<String> ofOnAPath = recordsOf(onAPath, lines, 2);
            assertRecord(ofOnAPath.get(0), "accepted", onAPath, "writer", "WRITE", "WRITE", "POST",
                    "projects/{projectId}/sync", null);
            assertRecord(ofOnAPath.get(1), "completed", onAPath, "writer", "WRITE", "WRITE", "POST",
                    "projects/{projectId}/sync", Integer.toString(onAPath.statusCode()));

            String everything = String.join("\n", lines);
            assertFalse(everything.contains(unknownProject.toString()), "a record holds the template, not the path");
            for (var identity : List.of(reader, writer)) {
                assertFalse(everything.contains(identity.token()), "a record holds no token");
                assertFalse(everything.contains(sha256(identity.token())), "a record holds no fingerprint of a token");
            }
            assertFalse(Files.readString(auth).contains("REMOTE_ACCESS"), "the journal is not the identity file");
        }
    }

    @Test
    void whenTheJournalCannotBeWrittenAWriteIsRefusedByNameAndAReadIsStillServed() throws Exception {
        Path auth = temp.resolve("remote-auth.txt");
        var reader = MorpheusRemoteIdentityFile.create(auth, "reader", MorpheusRemoteRole.READ);
        var writer = MorpheusRemoteIdentityFile.create(auth, "writer", MorpheusRemoteRole.WRITE);
        MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        Path journal = temp.resolve(RemoteAccessJournal.FILE_NAME);
        HttpClient client = RemoteHttpTestSupport.trustedClient(
                RemoteHttpTestSupport.createKeyStore(temp.resolve("server.p12")));

        try (MorpheusRemoteHttpServer server = start(auth)) {
            URI base = URI.create("https://127.0.0.1:" + server.port());
            Files.delete(journal);
            Files.createDirectory(journal);

            HttpResponse<String> written = send(client, base, "/api/v1/projects", "POST", writer.token(), "{}");
            HttpResponse<String> read = send(client, base, "/api/v1/health", "GET", reader.token(), null);

            assertEquals(503, written.statusCode(), written.body());
            assertTrue(written.body().contains("\"" + RemoteAccessJournal.REFUSAL + "\""), written.body());
            assertFalse(written.body().contains(temp.toString()), "the refusal names no server location");
            assertEquals(200, read.statusCode(), read.body());
        }
    }

    /**
     * The journal once it holds the expected number of records. The outcome of a request is written after its
     * exchange is closed, so the client can hold a response whose record is not on disk yet.
     */
    private static List<String> linesOnceThereAre(int expected, Path journal) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        List<String> lines = Files.readAllLines(journal);
        while (lines.size() < expected && System.nanoTime() < deadline) {
            Thread.sleep(20);
            lines = Files.readAllLines(journal);
        }
        return lines;
    }

    /**
     * The records of one request, in the order they were written, once their number is checked. Only that order is a
     * property of the server: a request is accepted before it is served and completed after its exchange is closed,
     * so the client can send its next request, and that request can be recorded, before the outcome of the previous
     * one is on disk.
     */
    private static List<String> recordsOf(HttpResponse<String> response, List<String> lines, int expected) {
        String requestId = "\"requestId\":\"" + response.headers().firstValue("X-Request-Id").orElseThrow() + "\"";
        List<String> records = lines.stream().filter(line -> line.contains(requestId)).toList();
        assertEquals(expected, records.size(), lines::toString);
        return records;
    }

    private MorpheusRemoteHttpServer start(Path auth) throws Exception {
        Path workspaces = Files.createDirectory(temp.resolve("workspaces"));
        return MorpheusRemoteHttpServer.start(
                temp.resolve("morpheus.db"),
                temp.resolve("backups"),
                temp.resolve("provider-plugins"),
                AllowedWorkspaceRoots.of(List.of(workspaces)),
                "127.0.0.1",
                0,
                auth,
                temp.resolve("server.p12"),
                RemoteHttpTestSupport.KEYSTORE_PASSWORD.toCharArray(),
                4,
                new ExternalReferenceResolverRegistry(List.of()),
                () -> new ExternalIntegrationStatus("MINOS", "DISABLED", false, "test", Map.of()),
                new DisabledTechnicalContextProvider("NEXUS", "test"),
                project -> ChangeWriteCapabilityObservation.denied("test"), OperationalRuntime.of(OperationalLogMode.OFF));
    }

    private static HttpResponse<String> send(
            HttpClient client, URI base, String path, String method, String token, String body) throws Exception {
        return RemoteHttpTestSupport.send(client, base.resolve(path), method, token, body);
    }

    private static void assertRecord(
            String line,
            String phase,
            HttpResponse<String> response,
            String principal,
            String held,
            String required,
            String method,
            String route,
            String status) {
        assertTrue(line.contains("\"code\":\"REMOTE_ACCESS\""), line);
        for (Map.Entry<String, String> field : Map.of(
                "phase", phase,
                "requestId", response.headers().firstValue("X-Request-Id").orElseThrow(),
                "principal", principal,
                "roleHeld", held,
                "roleRequired", required,
                "method", method,
                "route", route).entrySet()) {
            assertTrue(line.contains("\"" + field.getKey() + "\":\"" + field.getValue() + "\""),
                    () -> field + " in " + line);
        }
        assertEquals(status != null, line.contains("\"status\":"), line);
        if (status != null) {
            assertTrue(line.contains("\"status\":\"" + status + "\""), line);
        }
    }

    private static String sha256(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
