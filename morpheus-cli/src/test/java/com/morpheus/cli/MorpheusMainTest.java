package com.morpheus.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusMainTest {
    @TempDir
    Path tempDir;

    @Test
    void officialLauncherKeepsAPlainSyncIncrementalInsteadOfInjectingForce() {
        String projectId = registerFixtureProject();

        Invocation bootstrap = launch("--data-dir", data().toString(), "sync", "--project", projectId);
        assertEquals(CliExitCode.SUCCESS.code(), bootstrap.exitCode(), bootstrap.stderr());
        assertTrue(bootstrap.stdout().contains("mode=FULL_REBUILD"), bootstrap.stdout());

        Invocation unchanged = launch("--data-dir", data().toString(), "sync", "--project", projectId);
        assertEquals(CliExitCode.SUCCESS.code(), unchanged.exitCode(), unchanged.stderr());
        assertTrue(unchanged.stdout().contains("mode=INCREMENTAL"), unchanged.stdout());
        assertTrue(unchanged.stdout().contains("fullRebuildReason=none"), unchanged.stdout());
        assertTrue(unchanged.stdout().contains("published=false"), unchanged.stdout());
    }

    @Test
    void officialLauncherForwardsAnExplicitForceExactlyOnce() {
        String projectId = registerFixtureProject();
        assertEquals(CliExitCode.SUCCESS.code(), launch("--data-dir", data().toString(), "sync", "--project", projectId).exitCode());

        Invocation forced = launch("--data-dir", data().toString(), "sync", "--project", projectId, "--force");

        // A second --force would be rejected by CommandOptions as "duplicate flag", so SUCCESS proves single delivery.
        assertEquals(CliExitCode.SUCCESS.code(), forced.exitCode(), forced.stderr());
        assertTrue(forced.stdout().contains("mode=FULL_REBUILD"), forced.stdout());
        assertTrue(forced.stdout().contains("fullRebuildReason=FORCED"), forced.stdout());
    }

    @Test
    void officialLauncherForwardsGlobalLayoutFlagsBeforeASyncWithoutForcing() {
        String projectId = registerFixtureProject();
        assertEquals(CliExitCode.SUCCESS.code(), launch("--data-dir", data().toString(), "sync", "--project", projectId).exitCode());

        Invocation json = launch("--data-dir", data().toString(), "--json", "sync", "--project", projectId);
        assertEquals(CliExitCode.SUCCESS.code(), json.exitCode(), json.stderr());
        assertTrue(json.stdout().contains("\"mode\":\"INCREMENTAL\""), json.stdout());

        Invocation config = launch(
                "--data-dir", data().toString(),
                "--config-dir", tempDir.resolve("config").toString(),
                "sync", "--project", projectId);
        assertEquals(CliExitCode.SUCCESS.code(), config.exitCode(), config.stderr());
        assertTrue(config.stdout().contains("mode=INCREMENTAL"), config.stdout());

        Invocation db = launch(
                "--data-dir", data().toString(),
                "--db", data().resolve("morpheus.db").toString(),
                "sync", "--project", projectId);
        assertEquals(CliExitCode.SUCCESS.code(), db.exitCode(), db.stderr());
        assertTrue(db.stdout().contains("mode=INCREMENTAL"), db.stdout());
    }

    @Test
    void detectsAndParsesNativeMcpStdioCommandWithoutProtocolOutput() {
        assertTrue(McpLaunchOptions.isMcpCommand(new String[]{"--db", "state.db", "mcp", "--stdio"}));
        assertFalse(McpLaunchOptions.isMcpCommand(new String[]{"requirements", "find"}));

        Properties properties = properties();
        McpLaunchOptions options = McpLaunchOptions.parse(
                new String[]{"mcp", "--stdio", "--db", "state.db"}, Map.of(), properties);

        assertEquals(Path.of("state.db").toAbsolutePath().normalize(), options.layout().databasePath());
    }

    @Test
    void detectsAndParsesNativeApiCommandWithLoopbackDefaults() {
        assertTrue(ApiLaunchOptions.isApiCommand(new String[]{"--db", "state.db", "api"}));
        assertFalse(ApiLaunchOptions.isApiCommand(new String[]{"mcp", "--stdio"}));

        ApiLaunchOptions defaults = ApiLaunchOptions.parse(
                new String[]{"api", "--db", "state.db"}, Map.of(), properties());
        assertEquals("127.0.0.1", defaults.host());
        assertEquals(8765, defaults.port());
        assertEquals(Path.of("state.db").toAbsolutePath().normalize(), defaults.layout().databasePath());

        ApiLaunchOptions explicit = ApiLaunchOptions.parse(
                new String[]{"--db=state.db", "api", "--host=localhost", "--port=9876"}, Map.of(), properties());
        assertEquals("localhost", explicit.host());
        assertEquals(9876, explicit.port());
    }

    @Test
    void officialHelpDocumentsMcpHeadlessApiAndOptionalMinosModes() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();

        int exit;
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            exit = MorpheusMain.run(new String[]{"help"}, out, err, Map.of(), properties());
        }

        assertEquals(CliExitCode.SUCCESS.code(), exit);
        String help = output.toString(StandardCharsets.UTF_8);
        assertTrue(help.contains("mcp --stdio"));
        assertTrue(help.contains("api [--host HOST] [--port PORT]"));
        assertTrue(help.contains("/api/v1"));
        assertTrue(help.contains("minos-status"));
        assertTrue(help.contains("external-references list"));
        assertTrue(help.contains("MORPHEUS_MINOS_JAR"));
        assertTrue(help.contains("server identity create --principal NAME --role READ|WRITE|ADMIN [--expires-at ISO-8601]"));
        assertTrue(help.contains("server identity rotate --principal NAME [--expires-at ISO-8601|never]"));
        assertTrue(help.contains("at least one active ADMIN identity"));
        assertTrue(help.contains("--expires-at never makes the credential permanent"));
        assertTrue(errors.toString(StandardCharsets.UTF_8).isEmpty());
    }

    @Test
    void helpIsDetectedAfterSkippingAValueConsumingGlobalFlag() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();

        int exit;
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            exit = MorpheusMain.run(new String[]{"--data-dir", "data", "help"}, out, err, Map.of(), properties());
        }

        assertEquals(CliExitCode.SUCCESS.code(), exit);
        String help = output.toString(StandardCharsets.UTF_8);
        assertTrue(help.contains("mcp --stdio"));
        assertTrue(help.contains("api [--host HOST] [--port PORT]"));
    }

    @Test
    void emptyArgumentsAreTreatedAsAnImplicitHelpRequestWithoutForcingSync() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();

        int exit;
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            exit = MorpheusMain.run(new String[0], out, err, Map.of(), properties());
        }

        assertEquals(CliExitCode.SUCCESS.code(), exit);
        String help = output.toString(StandardCharsets.UTF_8);
        assertTrue(help.contains("mcp --stdio"));
        assertTrue(help.contains("api [--host HOST] [--port PORT]"));
    }

    @Test
    void rejectsUnsupportedMcpModesAndJsonWrapper() {
        Properties properties = properties();
        assertThrows(IllegalArgumentException.class,
                () -> McpLaunchOptions.parse(new String[]{"mcp"}, Map.of(), properties));
        assertThrows(IllegalArgumentException.class,
                () -> McpLaunchOptions.parse(new String[]{"mcp", "--http"}, Map.of(), properties));
        assertThrows(IllegalArgumentException.class,
                () -> McpLaunchOptions.parse(new String[]{"mcp", "--stdio", "--json"}, Map.of(), properties));
    }

    @Test
    void rejectsInvalidApiPortJsonAndUnknownArguments() {
        Properties properties = properties();
        assertThrows(IllegalArgumentException.class,
                () -> ApiLaunchOptions.parse(new String[]{"api", "--port", "0"}, Map.of(), properties));
        assertThrows(IllegalArgumentException.class,
                () -> ApiLaunchOptions.parse(new String[]{"api", "--port", "70000"}, Map.of(), properties));
        assertThrows(IllegalArgumentException.class,
                () -> ApiLaunchOptions.parse(new String[]{"api", "--json"}, Map.of(), properties));
        assertThrows(IllegalArgumentException.class,
                () -> ApiLaunchOptions.parse(new String[]{"api", "--tls"}, Map.of(), properties));
    }

    private Path data() {
        return tempDir.resolve("data");
    }

    private String registerFixtureProject() {
        Invocation add = launch("--data-dir", data().toString(), "projects", "add", "--workspace", fixture().toString());
        assertEquals(CliExitCode.SUCCESS.code(), add.exitCode(), add.stderr());
        return add.stdout().lines()
                .filter(line -> line.startsWith("projectId="))
                .findFirst()
                .map(line -> line.substring("projectId=".length()).trim())
                .orElseThrow(() -> new AssertionError("missing projectId in output: " + add.stdout()));
    }

    private Path fixture() {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path fromRoot = current.resolve("experiments/m0/fixtures/openspec-basic");
        if (Files.isDirectory(fromRoot)) {
            return fromRoot;
        }
        Path fromModule = current.resolve("../experiments/m0/fixtures").normalize().resolve("openspec-basic");
        if (Files.isDirectory(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("M0 fixture not found from " + current);
    }

    private Invocation launch(String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
            int exitCode = MorpheusMain.run(args, out, err, Map.of(), properties());
            return new Invocation(
                    exitCode,
                    outBytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"),
                    errBytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"));
        }
    }

    private record Invocation(int exitCode, String stdout, String stderr) {}

    private Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("user.home", Path.of("home").toAbsolutePath().toString());
        properties.setProperty("os.name", "Linux");
        return properties;
    }
}
