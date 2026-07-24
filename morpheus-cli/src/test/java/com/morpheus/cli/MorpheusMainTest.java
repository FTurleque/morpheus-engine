package com.morpheus.cli;

import org.junit.jupiter.api.Test;

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

        Properties properties = new Properties();
        properties.setProperty("user.home", Path.of("home").toAbsolutePath().toString());
        properties.setProperty("os.name", "Linux");
        McpLaunchOptions options = McpLaunchOptions.parse(
                new String[]{"mcp", "--stdio", "--db", "state.db"}, Map.of(), properties);

        assertEquals(Path.of("state.db").toAbsolutePath().normalize(), options.layout().databasePath());
    }

    @Test
    void rejectsUnsupportedMcpModesAndJsonWrapper() {
        Properties properties = new Properties();
        properties.setProperty("user.home", Path.of("home").toAbsolutePath().toString());
        properties.setProperty("os.name", "Linux");

        assertThrows(IllegalArgumentException.class,
                () -> McpLaunchOptions.parse(new String[]{"mcp"}, Map.of(), properties));
        assertThrows(IllegalArgumentException.class,
                () -> McpLaunchOptions.parse(new String[]{"mcp", "--http"}, Map.of(), properties));
        assertThrows(IllegalArgumentException.class,
                () -> McpLaunchOptions.parse(new String[]{"mcp", "--stdio", "--json"}, Map.of(), properties));
    }
}
