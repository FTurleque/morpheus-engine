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

class MorpheusProviderPluginCliTest {
    @TempDir
    Path tempDir;

    @Test
    void explicitDiscoveryOfMissingOptionalDirectoryIsNonFatalAndJson() {
        Result result = run("--json", "provider-plugins", "discover", "--directory", tempDir.resolve("missing").toString());

        assertEquals(CliExitCode.SUCCESS.code(), result.exitCode());
        assertTrue(result.out().contains("PLUGIN_DIRECTORY_NOT_FOUND"));
        assertTrue(result.out().contains("\"compatibleCount\":0"));
        assertTrue(result.err().isEmpty());
    }

    @Test
    void probeRequiresExplicitPluginAndWorkspace() {
        Result result = run("provider-plugins", "probe", "--directory", tempDir.toString());

        assertEquals(CliExitCode.USAGE.code(), result.exitCode());
        assertTrue(result.err().contains("probe requires --plugin ID and --workspace PATH"));
    }

    @Test
    void probeRequiresTrustedSha256Pin() {
        Result result = run(
                "provider-plugins", "probe",
                "--directory", tempDir.toString(),
                "--plugin", "example",
                "--workspace", tempDir.toString());

        assertEquals(CliExitCode.USAGE.code(), result.exitCode());
        assertTrue(result.err().contains("probe requires --sha256 HEX"));
    }

    @Test
    void probeRejectsMalformedTrustedOnlyPinBeforeDiscovery() {
        Result result = run(
                "provider-plugins", "probe",
                "--directory", tempDir.toString(),
                "--plugin", "example",
                "--workspace", tempDir.toString(),
                "--sha256", "invalid");

        assertEquals(CliExitCode.USAGE.code(), result.exitCode());
        assertTrue(result.err().contains("64 hexadecimal characters"));
    }

    private Result run(String... args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int exit;
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.setProperty("user.home", tempDir.resolve("home").toString());
            properties.setProperty("os.name", "Linux");
            exit = MorpheusMain.run(args, out, err, Map.of(), properties);
        }
        return new Result(exit, output.toString(StandardCharsets.UTF_8), errors.toString(StandardCharsets.UTF_8));
    }

    private record Result(int exitCode, String out, String err) {
    }
}
