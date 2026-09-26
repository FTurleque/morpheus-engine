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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(sync.stdout().contains("PRECEDENCE_RECORDED"), sync.stdout());

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

    /** Only sync reads --revision; status and conflicts used to accept it and ignore it, exit code 0. */
    @Test
    void anOptionTheActionDoesNotReadIsRefusedBeforeTheProjectIsLookedUp() {
        Path data = tempDirectory.resolve("composition-options");
        String projectId = ProjectSpecificationId.generate().toString();

        for (String action : java.util.List.of("status", "conflicts")) {
            Invocation refused = invokeWithData(data, "composition", action, "--project", projectId, "--revision", "r1");
            assertEquals(2, refused.exitCode(), refused.stderr());
            assertTrue(refused.stderr().contains("unknown option: --revision"), refused.stderr());
        }
        Invocation misspelledAction = invokeWithData(
                data, "composition", "statsu", "--project", projectId, "--revision", "r1");
        assertEquals(2, misspelledAction.exitCode(), misspelledAction.stderr());
        assertTrue(misspelledAction.stderr().contains("unknown composition action: statsu"), misspelledAction.stderr());
        Invocation withoutIt = invokeWithData(data, "composition", "status", "--project", projectId);
        assertEquals(4, withoutIt.exitCode(), withoutIt.stderr());
        assertTrue(withoutIt.stderr().contains("project has no ACTIVE snapshot"), withoutIt.stderr());
    }

    /**
     * A workspace that only the structured-markdown provider supports publishes under its registered root.
     *
     * <p>The markdown reader used to publish its specification file as the project root, so the registered
     * workspace root and the published one never matched and every such sync ended on a store collision.</p>
     */
    @Test
    void syncsAMarkdownOnlyWorkspaceUnderItsRegisteredRoot() throws Exception {
        Path workspace = tempDirectory.resolve("markdown-only");
        Path specification = workspace.resolve("morpheus/specification.md");
        Files.createDirectories(specification.getParent());
        Files.copy(fixture("openspec-basic").resolve("morpheus/specification.md"), specification);
        Path data = tempDirectory.resolve("markdown-only-data");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        register(data, projectId, workspace);

        Invocation sync = invokeWithData(
                data, "--json", "composition", "sync", "--project", projectId.toString());

        assertEquals(0, sync.exitCode(), sync.stderr());
        assertTrue(sync.stdout().contains("\"primaryProviderId\":\"structured-markdown\""), sync.stdout());
        assertFalse(sync.stderr().contains("collision"), sync.stderr());
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
