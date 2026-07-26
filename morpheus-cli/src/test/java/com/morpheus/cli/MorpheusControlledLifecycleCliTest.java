package com.morpheus.cli;

import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.provider.synthetic.SyntheticSpecificationContentReader;
import com.morpheus.store.sqlite.SqliteChangeLifecycleMutationStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusControlledLifecycleCliTest {
    @TempDir
    Path tempDirectory;

    @Test
    void explicitApplyCommandRequiresProductionWriteCapabilityAndDoesNotMutateWhenDenied() {
        Seed seed = seed(tempDirectory.resolve("m17-cli-data"));

        Invocation denied = invokeWithData(
                seed.data(),
                "--json", "lifecycle", "apply",
                "--project", seed.projectId(),
                "--change", seed.changeId(),
                "--expected-revision", "0",
                "--to", "PROPOSED",
                "--idempotency-key", "m17-cli-denied",
                "--actor", "m17-cli-test",
                "--confirm");

        assertEquals(CliExitCode.STATE_ERROR.code(), denied.exitCode(), denied.stderr());
        assertTrue(denied.stdout().contains("\"state\":\"NOT_AUTHORIZED\""), denied.stdout());
        assertTrue(denied.stdout().contains("WRITE_CHANGE"), denied.stdout());

        try (var mutations = new SqliteChangeLifecycleMutationStore(seed.database())) {
            assertTrue(mutations.listAudit(
                    ProjectSpecificationId.parse(seed.projectId()), ChangeId.parse(seed.changeId())).isEmpty());
        }
    }

    @Test
    void commandRequiresExpectedRevisionIdempotencyActorAndConfirmationSyntaxIsExplicit() {
        Seed seed = seed(tempDirectory.resolve("m17-cli-invalid"));
        Invocation missingRevision = invokeWithData(
                seed.data(),
                "lifecycle", "apply",
                "--project", seed.projectId(),
                "--change", seed.changeId(),
                "--to", "PROPOSED",
                "--idempotency-key", "m17-cli-missing",
                "--actor", "m17-cli-test",
                "--confirm");
        assertEquals(CliExitCode.USAGE.code(), missingRevision.exitCode());
        assertTrue(missingRevision.stderr().contains("--expected-revision is required"), missingRevision.stderr());
    }

    private Seed seed(Path data) {
        Properties properties = properties();
        CliLayout layout = CliLayout.resolve(
                Optional.of(data), Optional.empty(), Optional.empty(), Map.of(), properties);
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        Path fixture = fixture("synthetic-basic");
        try (CliRuntime runtime = new CliRuntime(layout.databasePath())) {
            var normalized = new SyntheticSpecificationContentReader()
                    .read(
                            ProviderReadRequest.all(fixture, projectId),
                            new PersistentEntityIdentityResolver(runtime.identities))
                    .content()
                    .orElseThrow();
            new ProjectSnapshotImportService(
                    runtime.snapshots,
                    runtime.requirements,
                    runtime.content,
                    runtime.traceability)
                    .publishFull(normalized, Optional.of("m17-cli-test"), Instant.parse("2026-07-26T15:30:00Z"));
            return new Seed(data, layout.databasePath(), projectId.toString(), normalized.changes().getFirst().id().toString());
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

    private record Seed(Path data, Path database, String projectId, String changeId) {
    }

    private record Invocation(int exitCode, String stdout, String stderr) {
    }
}
