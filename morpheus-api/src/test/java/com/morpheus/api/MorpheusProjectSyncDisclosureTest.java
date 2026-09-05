package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A remote caller must never learn a server pathname it did not supply.
 *
 * <p>{@code POST /projects/{id}/sync} is reachable remotely by a WRITE caller, and it answers an incomplete
 * scan with the scan's failures. Those failures are written by the platform, not by MORPHEUS:
 * {@link java.nio.file.AccessDeniedException} reports the pathname and nothing else, so relaying the failure
 * text handed the caller the server's layout. The route now answers with the stable code and drops any value
 * that names a location.</p>
 *
 * <p>The failure is provoked with a real unreadable file rather than a stub, so what is asserted is the
 * response of the running server. Removing read permission is reproducible only where POSIX permissions
 * exist; {@link MorpheusProjectSyncApiServiceContractTest} carries the platform-independent half.</p>
 */
class MorpheusProjectSyncDisclosureTest {

    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void anUnreadableSourceNeverPutsTheServerPathnameInTheHttpAnswer() throws Exception {
        Path workspace = Files.createDirectories(tempDirectory.resolve("classified").resolve("workspace"));
        Path sources = Files.createDirectory(workspace.resolve("openspec"));
        Path unreadable = Files.writeString(sources.resolve("spec.md"), "# spec", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("---------"));

        Path database = tempDirectory.resolve("morpheus.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response created = http.postJson(
                    server, "/projects", "{\"workspace\":" + http.jsonString(workspace.toString()) + "}");
            assertEquals(201, created.status(), created.body());
            String projectId = http.field(created.body(), "projectId");

            ApiTestSupport.Response sync = http.post(server, "/projects/" + projectId + "/sync");
            assertEquals(409, sync.status(), sync.body());

            String body = sync.body();
            assertFalse(body.contains(workspace.toString()),
                    () -> "the answer disclosed the server workspace pathname: " + body);
            assertFalse(body.contains(unreadable.toString()),
                    () -> "the answer disclosed the unreadable source pathname: " + body);
            assertFalse(body.contains("classified"),
                    () -> "the answer disclosed a server directory name: " + body);
            assertFalse(body.contains(tempDirectory.toString()),
                    () -> "the answer disclosed the server temporary root: " + body);

            assertTrue(body.contains("source scan is incomplete"), body);
            assertTrue(body.contains("SOURCE_UNREADABLE"),
                    () -> "the answer must still name why the scan failed: " + body);
        } finally {
            Files.setPosixFilePermissions(
                    tempDirectory.resolve("classified").resolve("workspace").resolve("openspec").resolve("spec.md"),
                    PosixFilePermissions.fromString("rw-------"));
        }
    }
}
