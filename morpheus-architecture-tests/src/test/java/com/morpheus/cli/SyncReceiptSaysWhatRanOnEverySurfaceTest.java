package com.morpheus.cli;

import com.morpheus.api.MorpheusHttpServer;
import com.morpheus.application.operability.OperationalLogMode;
import com.morpheus.application.operability.OperationalRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR-0126: a sync receipt names what was executed, on the CLI and on the HTTP API alike, over one database.
 * A receipt that says {@code published=true} says {@code FULL_REBUILD}; a sync that publishes nothing says
 * {@code INCREMENTAL}. The two surfaces differ on one point only, a sync without change: the CLI publishes
 * nothing, the HTTP sync always publishes.
 */
class SyncReceiptSaysWhatRanOnEverySurfaceTest {

    @TempDir
    Path temp;

    @Test
    void aPublishedReceiptSaysFullRebuildAndAnUnpublishedOneSaysIncremental() throws Exception {
        Path dataDirectory = temp.resolve("data");
        Path workspace = copyOfFixture("openspec-basic");
        Path database = dataDirectory.resolve("morpheus.db");

        String projectId = value(cli(dataDirectory, "projects", "add", "--workspace", workspace.toString()), "projectId");
        String bootstrap = cli(dataDirectory, "sync", "--project", projectId);
        assertTrue(bootstrap.contains("published=true") && bootstrap.contains("mode=FULL_REBUILD"), bootstrap);

        String cliUnchanged = cli(dataDirectory, "sync", "--project", projectId);
        assertTrue(cliUnchanged.contains("published=false"), cliUnchanged);
        assertTrue(cliUnchanged.contains("mode=INCREMENTAL"), cliUnchanged);

        touchASpecification(workspace, "first edit");
        String cliChanged = cli(dataDirectory, "sync", "--project", projectId);
        assertTrue(cliChanged.contains("published=true"), cliChanged);
        assertTrue(cliChanged.contains("mode=FULL_REBUILD"), cliChanged);

        touchASpecification(workspace, "second edit");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            String httpChanged = post(server, "/api/v1/projects/" + projectId + "/sync");
            assertTrue(httpChanged.contains("\"published\":true"), httpChanged);
            assertTrue(httpChanged.contains("\"mode\":\"FULL_REBUILD\""), httpChanged);

            String httpUnchanged = post(server, "/api/v1/projects/" + projectId + "/sync");
            assertTrue(httpUnchanged.contains("\"published\":true"),
                    "the HTTP sync publishes even when nothing changed: " + httpUnchanged);
            assertTrue(httpUnchanged.contains("\"mode\":\"FULL_REBUILD\""), httpUnchanged);

            String httpStatus = get(server, "/api/v1/projects/" + projectId + "/sync-status");
            assertTrue(httpStatus.contains("\"lastSuccessfulMode\":\"FULL_REBUILD\""), httpStatus);
        }

        String cliStatus = cli(dataDirectory, "--json", "sync-status", "--project", projectId);
        assertTrue(cliStatus.contains("\"lastSuccessfulMode\":\"FULL_REBUILD\""), cliStatus);
    }

    private String cli(Path dataDirectory, String... command) {
        String[] arguments = new String[command.length + 2];
        arguments[0] = "--data-dir";
        arguments[1] = dataDirectory.toString();
        System.arraycopy(command, 0, arguments, 2, command.length);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Properties properties = new Properties();
        properties.setProperty("os.name", System.getProperty("os.name"));
        properties.setProperty("user.home", temp.resolve("home").toString());
        int exitCode;
        try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            exitCode = MorpheusMain.run(
                    OperationalRuntime.of(OperationalLogMode.OFF), arguments, out, err, Map.of(), properties);
        }
        assertEquals(0, exitCode, stderr.toString(StandardCharsets.UTF_8));
        return stdout.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String value(String output, String key) {
        String prefix = key + "=";
        return output.lines().filter(line -> line.startsWith(prefix)).findFirst()
                .map(line -> line.substring(prefix.length()).trim())
                .orElseThrow(() -> new AssertionError("missing " + key + " in output: " + output));
    }

    private static String post(MorpheusHttpServer server, String path) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri(server, path)).POST(HttpRequest.BodyPublishers.noBody()).build());
    }

    private static String get(MorpheusHttpServer server, String path) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(uri(server, path)).GET().build());
    }

    private static URI uri(MorpheusHttpServer server, String path) {
        return URI.create("http://" + server.host() + ":" + server.port() + path);
    }

    private static String send(HttpRequest request) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return response.body();
    }

    private Path copyOfFixture(String name) throws IOException {
        Path source = fixture(name);
        Path copy = temp.resolve("workspace-" + name);
        try (var files = Files.walk(source)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Path target = copy.resolve(source.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(file, target);
                }
            }
        }
        return copy;
    }

    private static void touchASpecification(Path workspace, String text) throws IOException {
        Path specification;
        try (var files = Files.walk(workspace.resolve("openspec"))) {
            specification = files.filter(path -> path.getFileName().toString().equals("spec.md")).findFirst().orElseThrow();
        }
        Files.writeString(specification, Files.readString(specification) + "\nA line added by the " + text + ".\n");
    }

    private static Path fixture(String name) {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path fromRoot = current.resolve("experiments/m0/fixtures").resolve(name);
        if (Files.isDirectory(fromRoot)) {
            return fromRoot;
        }
        Path fromModule = current.resolve("../experiments/m0/fixtures").normalize().resolve(name);
        if (Files.isDirectory(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("M0 fixture not found: " + name + " from " + current);
    }
}
