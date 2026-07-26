package com.morpheus.cli;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusCompositionCliTest {
    @TempDir
    Path tempDirectory;

    @Test
    void syncsTwoRealProvidersAndQueriesPersistedConflicts() {
        Path data = tempDirectory.resolve("composition-data");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        register(data, projectId, fixture("openspec-basic"));

        Invocation sync = invokeWithData(
                data, "--json", "composition", "sync", "--project", projectId.toString(), "--revision", "m18-test");
        assertEquals(0, sync.exitCode(), sync.stderr());
        assertTrue(sync.stdout().contains("\"primaryProviderId\":\"openspec\""), sync.stdout());
        assertTrue(sync.stdout().contains("\"providerId\":\"structured-markdown\""), sync.stdout());
        assertTrue(sync.stdout().contains("auth-session/session-expiration"), sync.stdout());
        assertTrue(sync.stdout().contains("SELECTED_BY_PRECEDENCE"), sync.stdout());

        Invocation status = invokeWithData(
                data, "--json", "composition", "status", "--project", projectId.toString());
        assertEquals(0, status.exitCode(), status.stderr());
        assertTrue(status.stdout().contains("\"providers\""), status.stdout());
        assertTrue(status.stdout().contains("\"conflicts\""), status.stdout());

        Invocation conflicts = invokeWithData(
                data, "--json", "composition", "conflicts", "--project", projectId.toString());
        assertEquals(0, conflicts.exitCode(), conflicts.stderr());
        assertTrue(conflicts.stdout().contains("\"logicalKey\":\"auth-session/session-expiration\""), conflicts.stdout());
        assertTrue(conflicts.stdout().contains("\"evidenceId\""), conflicts.stdout());
    }

    private void register(Path data, ProjectSpecificationId projectId, Path workspace) {
        CliLayout layout = CliLayout.resolve(
                Optional.of(data), Optional.empty(), Optional.empty(), Map.of(), properties());
        try (CliRuntime runtime = new CliRuntime(layout.databasePath())) {
            runtime.snapshots.putProject(new ProjectStoreEntry(
                    projectId,
                    SourceLocator.file(workspace.toAbsolutePath().normalize().toString())));
        }
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
        try (PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
            int exitCode = MorpheusMain.run(args, out, err, Map.of(), properties());
            return new Invocation(
                    exitCode,
                    outBytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"),
                    errBytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"));
        }
    }

    private Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("os.name", System.getProperty("os.name", "Windows"));
        properties.setProperty("user.home", tempDirectory.resolve("home").toString());
        return properties;
    }

    private Path fixture(String name) {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("experiments/m0/fixtures").resolve(name);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("M0 fixture not found: " + name);
    }

    private record Invocation(int exitCode, String stdout, String stderr) {
    }
}
