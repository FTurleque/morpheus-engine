package com.morpheus.cli;

import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.provider.synthetic.SyntheticSpecificationContentReader;
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

class MorpheusAcceptanceCriteriaCliTest {
    @TempDir
    Path tempDirectory;

    @Test
    void listsExplicitCriteriaGloballyAndByOwnerWithoutSynthesizingScenarios() {
        Seed seed = seed(tempDirectory.resolve("acceptance-data"));

        Invocation all = invokeWithData(
                seed.data(), "--json", "acceptance-criteria", "list",
                "--project", seed.projectId());
        assertEquals(0, all.exitCode(), all.stderr());
        assertTrue(all.stdout().contains("\"totalMatches\":2"), all.stdout());
        assertTrue(all.stdout().contains("\"verificationStatus\":\"VERIFIED\""), all.stdout());
        assertTrue(all.stdout().contains("\"verificationStatus\":\"NOT_VERIFIED\""), all.stdout());
        assertTrue(all.stdout().contains("\"verificationEvidenceIds\""), all.stdout());
        assertTrue(all.stdout().contains("\"sourceEvidenceId\""), all.stdout());
        assertTrue(!all.stdout().contains("Retain invoice"), all.stdout());

        Invocation byChange = invokeWithData(
                seed.data(), "--json", "acceptance-criteria", "list",
                "--project", seed.projectId(), "--change", seed.changeId());
        assertEquals(0, byChange.exitCode(), byChange.stderr());
        assertTrue(byChange.stdout().contains("\"totalMatches\":1"), byChange.stdout());
        assertTrue(byChange.stdout().contains("\"verificationStatus\":\"NOT_VERIFIED\""), byChange.stdout());

        Invocation byRequirement = invokeWithData(
                seed.data(), "--json", "acceptance-criteria", "list",
                "--project", seed.projectId(), "--requirement", seed.requirementId());
        assertEquals(0, byRequirement.exitCode(), byRequirement.stderr());
        assertTrue(byRequirement.stdout().contains("\"totalMatches\":1"), byRequirement.stdout());
        assertTrue(byRequirement.stdout().contains("\"verificationStatus\":\"VERIFIED\""), byRequirement.stdout());
    }

    @Test
    void rejectsAmbiguousOwnerFilters() {
        Seed seed = seed(tempDirectory.resolve("acceptance-invalid-data"));
        Invocation invalid = invokeWithData(
                seed.data(), "acceptance-criteria", "list",
                "--project", seed.projectId(),
                "--change", seed.changeId(),
                "--requirement", seed.requirementId());
        assertEquals(CliExitCode.USAGE.code(), invalid.exitCode());
        assertTrue(invalid.stderr().contains("mutually exclusive"), invalid.stderr());
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
                    .publishFull(normalized, Optional.of("m15-cli-test"), Instant.parse("2026-07-26T00:00:00Z"));
            return new Seed(
                    data,
                    projectId.toString(),
                    normalized.changes().getFirst().id().toString(),
                    normalized.requirements().getFirst().id().toString());
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

    private record Seed(Path data, String projectId, String changeId, String requirementId) {
    }

    private record Invocation(int exitCode, String stdout, String stderr) {
    }
}