package com.morpheus.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.application.product.ProductMetadata;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MorpheusProductCliTest {
    @TempDir
    Path tempDir;

    @Test
    void versionIsResolvedFromSharedProductMetadataBeforeIntegrations() {
        Result result = run("version");
        assertEquals(CliExitCode.SUCCESS.code(), result.exitCode());
        assertTrue(result.out().contains("MORPHEUS " + ProductMetadata.version()));
        assertTrue(result.err().isEmpty());
    }

    @Test
    void productInfoExposesTheSharedVersionAndChannel() {
        Result result = run("--json", "product-info");
        assertEquals(CliExitCode.SUCCESS.code(), result.exitCode());
        assertTrue(result.out().contains("\"version\":\"" + ProductMetadata.version() + "\""));
        assertTrue(result.out().contains("\"updateChannel\":\"stable\""));
    }

    @Test
    void updateCheckIsExplicitAndDoesNotApplyAnything() throws Exception {
        String availableVersion = nextPatchVersion(ProductMetadata.version());
        Path manifest = tempDir.resolve("stable.properties");
        Files.writeString(manifest, String.join("\n",
                "version=" + availableVersion,
                "channel=stable",
                "artifactUri=https://example.invalid/morpheus-" + availableVersion + ".zip",
                "sha256=cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                ""));

        Result result = run("update-check", "--manifest", manifest.toString());

        assertEquals(CliExitCode.SUCCESS.code(), result.exitCode());
        assertTrue(result.out().contains("availableVersion=" + availableVersion));
        assertTrue(result.out().contains("updateAvailable=true"));
        assertTrue(result.out().contains("action=none"));
        assertTrue(result.err().isEmpty());
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
        return new Result(
                exit,
                output.toString(StandardCharsets.UTF_8),
                errors.toString(StandardCharsets.UTF_8));
    }

    private String nextPatchVersion(String version) {
        String core = version.split("[+-]", 2)[0];
        String[] parts = core.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Expected semantic product version, got " + version);
        }
        int patch = Integer.parseInt(parts[2]);
        return parts[0] + "." + parts[1] + "." + (patch + 1);
    }

    private record Result(int exitCode, String out, String err) {
    }
}
