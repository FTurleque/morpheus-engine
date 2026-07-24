package com.morpheus.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusNexusCliTest {
    @TempDir
    Path tempDirectory;

    @Test
    void nexusStatusIsDisabledByDefaultWithoutBreakingLauncher() {
        Invocation invocation = invoke("--json", "nexus-status");
        assertEquals(0, invocation.exitCode(), invocation.stderr());
        assertTrue(invocation.stdout().contains("\"state\":\"DISABLED\""), invocation.stdout());
        assertTrue(invocation.stdout().contains("\"system\":\"NEXUS\""), invocation.stdout());
    }

    private Invocation invoke(String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        Properties properties = new Properties();
        properties.setProperty("user.home", tempDirectory.resolve("home").toString());
        properties.setProperty("os.name", System.getProperty("os.name", "Windows"));
        try (PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
            int exit = MorpheusMain.run(args, out, err, Map.of(), properties);
            return new Invocation(
                    exit,
                    outBytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"),
                    errBytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"));
        }
    }

    private record Invocation(int exitCode, String stdout, String stderr) {
    }
}
