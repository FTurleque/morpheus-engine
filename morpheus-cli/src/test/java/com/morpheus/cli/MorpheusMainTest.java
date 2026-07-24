package com.morpheus.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusMainTest {
    @Test
    void officialLauncherForcesCliSyncToMatchFullSnapshotExecution() {
        String[] normalized = MorpheusMain.normalizeForExecution(new String[]{
                "--data-dir", "data", "sync", "--project", "01900000-0000-7000-8000-000000000001"
        });
        assertTrue(Arrays.asList(normalized).contains("--force"));
    }

    @Test
    void explicitForceIsNotDuplicatedAndOtherCommandsAreUntouched() {
        String[] forced = MorpheusMain.normalizeForExecution(new String[]{"sync", "--project", "x", "--force"});
        assertArrayEquals(new String[]{"sync", "--project", "x", "--force"}, forced);

        String[] query = MorpheusMain.normalizeForExecution(new String[]{"--json", "projects", "list"});
        assertArrayEquals(new String[]{"--json", "projects", "list"}, query);
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
    void officialHelpDocumentsMcpAndHeadlessApiModes() {
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
        assertTrue(errors.toString(StandardCharsets.UTF_8).isEmpty());
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

    private Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("user.home", Path.of("home").toAbsolutePath().toString());
        properties.setProperty("os.name", "Linux");
        return properties;
    }
}
