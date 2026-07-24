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
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusJarvisOrchestrationCliTest {
    @TempDir
    Path tempDirectory;

    @Test
    void stateCommandKeepsLifecycleUnavailableUnlessExplicitlySupplied() {
        Seed seed = seed(tempDirectory.resolve("state-data"));

        Invocation unavailable = invokeWithData(
                seed.data(), "--json", "change-orchestration", "state",
                "--project", seed.projectId(), "--change", seed.changeId());
        assertEquals(0, unavailable.exitCode(), unavailable.stderr());
        assertTrue(unavailable.stdout().contains("\"source\":\"UNAVAILABLE\""), unavailable.stdout());
        assertTrue(unavailable.stdout().contains("\"state\":null"), unavailable.stdout());
        assertTrue(unavailable.stdout().contains("\"nextAllowedTransitions\":[]"), unavailable.stdout());
        assertTrue(unavailable.stdout().contains("\"persisted\":false"), unavailable.stdout());

        Invocation draft = invokeWithData(
                seed.data(), "--json", "change-orchestration", "state",
                "--project", seed.projectId(), "--change", seed.changeId(), "--lifecycle", "DRAFT");
        assertEquals(0, draft.exitCode(), draft.stderr());
        assertTrue(draft.stdout().contains("\"source\":\"CALLER_SUPPLIED\""), draft.stdout());
        assertTrue(draft.stdout().contains("\"nextAllowedTransitions\":[\"PROPOSED\""), draft.stdout());
    }

    @Test
    void transitionCheckReportsAllowedUnknownAndRequiresInput() {
        Seed seed = seed(tempDirectory.resolve("transition-data"));

        Invocation allowed = invokeWithData(
                seed.data(), "--json", "change-orchestration", "transition-check",
                "--project", seed.projectId(), "--change", seed.changeId(),
                "--from", "DRAFT", "--to", "PROPOSED");
        assertEquals(0, allowed.exitCode(), allowed.stderr());
        assertTrue(allowed.stdout().contains("\"state\":\"ALLOWED\""), allowed.stdout());

        Invocation unknown = invokeWithData(
                seed.data(), "--json", "change-orchestration", "transition-check",
                "--project", seed.projectId(), "--change", seed.changeId(),
                "--from", "PROPOSED", "--to", "SPECIFIED");
        assertEquals(0, unknown.exitCode(), unknown.stderr());
        assertTrue(unknown.stdout().contains("\"state\":\"UNKNOWN\""), unknown.stdout());
        assertTrue(unknown.stdout().contains("acceptanceCriteriaDefined"), unknown.stdout());

        Invocation requiresInput = invokeWithData(
                seed.data(), "--json", "change-orchestration", "transition-check",
                "--project", seed.projectId(), "--change", seed.changeId(),
                "--from", "DRAFT", "--to", "ABANDONED");
        assertEquals(0, requiresInput.exitCode(), requiresInput.stderr());
        assertTrue(requiresInput.stdout().contains("\"state\":\"REQUIRES_INPUT\""), requiresInput.stdout());
        assertTrue(requiresInput.stdout().contains("ABANDONMENT_REASON_REQUIRED"), requiresInput.stdout());
    }

    private Seed seed(Path data) {
        Path fixture = fixture("openspec-basic");
        Invocation add = invokeWithData(data, "projects", "add", "--workspace", fixture.toString());
        assertEquals(0, add.exitCode(), add.stderr());
        String projectId = value(add.stdout(), "projectId");
        Invocation sync = invokeWithData(data, "sync", "--project", projectId);
        assertEquals(0, sync.exitCode(), sync.stderr());
        Invocation changes = invokeWithData(data, "changes", "list", "--project", projectId);
        assertEquals(0, changes.exitCode(), changes.stderr());
        return new Seed(data, projectId, firstIdLine(changes.stdout()));
    }

    private Invocation invokeWithData(Path data, String... command) {
        String[] args = new String[command.length + 2];
        args[0] = "--data-dir";
        args[1] = data.toString();
        System.arraycopy(command, 0, args, 2, command.length);
        return invoke(args);
    }

    private Invocation invoke(String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        Properties properties = new Properties();
        properties.setProperty("os.name", System.getProperty("os.name", "Windows"));
        properties.setProperty("user.home", tempDirectory.resolve("home").toString());
        try (PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
            int exitCode = MorpheusMain.run(args, out, err, Map.of(), properties);
            return new Invocation(
                    exitCode,
                    outBytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"),
                    errBytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"));
        }
    }

    private Path fixture(String name) {
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

    private String value(String output, String key) {
        String prefix = key + "=";
        return output.lines().filter(line -> line.startsWith(prefix)).findFirst()
                .map(line -> line.substring(prefix.length()).trim())
                .orElseThrow(() -> new AssertionError("missing " + key + " in output: " + output));
    }

    private String firstIdLine(String output) {
        return output.lines()
                .filter(line -> !line.startsWith("snapshotId="))
                .filter(line -> !line.isBlank())
                .findFirst()
                .map(line -> line.split("\\t", 2)[0])
                .orElseThrow(() -> new AssertionError("missing item line in output: " + output));
    }

    private record Seed(Path data, String projectId, String changeId) {
    }

    private record Invocation(int exitCode, String stdout, String stderr) {
    }
}
