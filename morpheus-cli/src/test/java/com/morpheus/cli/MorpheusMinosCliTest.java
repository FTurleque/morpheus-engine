package com.morpheus.cli;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.reference.ExternalReferenceResolutionState;
import com.morpheus.domain.reference.ExternalReferenceTarget;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.store.sqlite.SqliteExternalReferenceStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusMinosCliTest {
    @TempDir
    Path tempDirectory;

    @Test
    void minosStatusIsDisabledByDefaultWithoutBreakingLauncher() {
        Invocation invocation = invoke("--json", "minos-status");

        assertEquals(0, invocation.exitCode(), invocation.stderr());
        assertTrue(invocation.stdout().contains("\"state\":\"DISABLED\""), invocation.stdout());
        assertTrue(invocation.stdout().contains("\"system\":\"MINOS\""), invocation.stdout());
    }

    /** The status record freezes its details with Map.copyOf, whose iteration order is salted once per JVM. */
    @Test
    void minosStatusPrintsItsDetailsInKeyOrderWhateverTheOrderOfTheStatusMap() {
        MorpheusExternalIntegrationCli cli = new MorpheusExternalIntegrationCli(
                new com.morpheus.application.reference.ExternalReferenceResolverRegistry(java.util.List.of()),
                () -> new com.morpheus.application.reference.ExternalIntegrationStatus(
                        "MINOS", "DISABLED", false, "m", java.util.Map.of("zeta", "6", "alpha", "1", "mu", "4", "beta", "2", "omega", "5", "kappa", "3")));
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        Properties properties = new Properties();
        properties.setProperty("user.home", tempDirectory.resolve("home").toString());
        int exit;
        try (PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)) {
            exit = cli.run(new String[]{"--data-dir", tempDirectory.resolve("status").toString(), "minos-status"},
                    out, err, Map.of(), properties);
        }

        assertEquals(0, exit);
        assertEquals("""
                system=MINOS
                state=DISABLED
                configured=false
                message=m
                alpha=1
                beta=2
                kappa=3
                mu=4
                omega=5
                zeta=6
                """, outBytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    @Test
    void listAndResolveWorkWithoutMinosAndNeverPersistNoResolverObservation() {
        Path database = tempDirectory.resolve("m12-cli.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        DomainIdentity ownerId = DomainIdentity.generate();
        ExternalReference reference = reference(ownerId);
        seed(database, projectId, snapshotId, reference);

        Invocation list = invoke("--db", database.toString(), "--json", "external-references", "list",
                "--project", projectId.toString(), "--owner", ownerId.toString());
        assertEquals(0, list.exitCode(), list.stderr());
        assertTrue(list.stdout().contains(reference.id().toString()), list.stdout());

        Invocation resolve = invoke("--db", database.toString(), "--json", "external-references", "resolve",
                "--project", projectId.toString(), "--reference", reference.id().toString());
        assertEquals(0, resolve.exitCode(), resolve.stderr());
        assertTrue(resolve.stdout().contains("\"persisted\":false"), resolve.stdout());
        assertTrue(resolve.stdout().contains("NO_RESOLVER"), resolve.stdout());

        try (var references = new SqliteExternalReferenceStore(database)) {
            assertEquals(reference, references.findReference(snapshotId, reference.id()).orElseThrow());
            assertEquals(ExternalReferenceResolutionState.UNVALIDATED,
                    references.findReference(snapshotId, reference.id()).orElseThrow().resolutionState());
        }
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
            return new Invocation(exit,
                    outBytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"),
                    errBytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"));
        }
    }

    private void seed(
            Path database,
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId snapshotId,
            ExternalReference reference) {
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var references = new SqliteExternalReferenceStore(database)) {
            snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace-" + projectId)));
            snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                    snapshotId, projectId, Optional.empty(), KnowledgeSnapshotState.READY,
                    Optional.of("rev"), Instant.parse("2026-07-24T12:00:00Z")));
            snapshots.activateSnapshot(snapshotId, Optional.empty());
            references.putReference(snapshotId, reference);
        }
    }

    private ExternalReference reference(DomainIdentity ownerId) {
        return ExternalReference.unvalidated(
                ExternalReferenceId.generate(),
                ownerId,
                new ExternalReferenceTarget(
                        "MINOS", Optional.of("morpheus-engine"), "SYMBOL",
                        "symbol:RequirementService", Optional.empty()),
                Optional.empty());
    }

    private record Invocation(int exitCode, String stdout, String stderr) {
    }
}
