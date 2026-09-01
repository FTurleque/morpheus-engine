package com.morpheus.architecture;

import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationAttempt;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationCommand;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationPolicy;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationResultState;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleOperationalState;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.lifecycle.mutation.ControlledChangeLifecycleMutationService;
import com.morpheus.application.lifecycle.mutation.RegisteredProjectWriteCapabilityResolver;
import com.morpheus.application.orchestration.ChangeTransitionEvaluationService;
import com.morpheus.application.orchestration.ChangeTransitionEvaluationState;
import com.morpheus.application.provider.ProviderSelectionPolicy;
import com.morpheus.application.provider.SpecificationProviderRegistry;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleIdempotencyKey;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleMutationId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleRevision;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import com.morpheus.provider.openspec.OpenSpecSpecificationProvider;
import com.morpheus.provider.synthetic.SyntheticSpecificationProvider;
import com.morpheus.store.memory.MemoryChangeLifecycleMutationStore;
import com.morpheus.store.memory.MemorySnapshotBusinessContentStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemoryTraceabilityStore;
import com.morpheus.store.sqlite.SqliteChangeLifecycleMutationStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlledLifecycleMutationContractTest {
    private static final Instant T0 = Instant.parse("2026-07-26T14:00:00Z");
    private static final ProviderId WRITE_PROVIDER = new ProviderId("m17-write-fixture");

    @TempDir
    Path tempDir;

    @Test
    void allowedEvaluationAloneNeverMutatesAndExplicitCommandIsIdempotent() {
        Fixture fixture = fixture();
        var evaluation = fixture.transitionService().evaluateActive(
                        fixture.projectId(),
                        ChangeLifecycle.of(fixture.changeId(), ChangeLifecycleState.DRAFT),
                        ChangeLifecycleState.PROPOSED,
                        com.morpheus.application.lifecycle.ChangeLifecyclePolicy.forwardOnly(),
                        Optional.empty())
                .orElseThrow();

        assertEquals(ChangeTransitionEvaluationState.ALLOWED, evaluation.state());
        assertTrue(fixture.mutations().findState(fixture.projectId(), fixture.changeId()).isEmpty(),
                "read-only evaluation must not create operational lifecycle state");
        assertTrue(fixture.mutations().listAudit(fixture.projectId(), fixture.changeId()).isEmpty());

        ChangeLifecycleMutationCommand command = command(
                fixture.projectId(), fixture.changeId(), "m17-idempotent", 0, ChangeLifecycleState.PROPOSED, true);
        var applied = fixture.service().apply(command, ChangeLifecycleMutationPolicy.strict());
        assertEquals(ChangeLifecycleMutationResultState.APPLIED, applied.state());
        assertEquals(1, applied.lifecycleState().orElseThrow().revision().value());
        assertEquals(ChangeLifecycleState.PROPOSED, applied.lifecycleState().orElseThrow().lifecycle().state());
        assertEquals(1, fixture.mutations().listAudit(fixture.projectId(), fixture.changeId()).size());

        ChangeLifecycleMutationCommand retry = new ChangeLifecycleMutationCommand(
                ChangeLifecycleMutationId.generate(),
                command.idempotencyKey(),
                command.projectId(),
                command.changeId(),
                command.expectedRevision(),
                command.targetState(),
                command.targetAbandonmentReason(),
                command.confirmed(),
                command.actor(),
                T0.plusSeconds(30));
        var retried = fixture.service().apply(retry, ChangeLifecycleMutationPolicy.strict());
        assertEquals(ChangeLifecycleMutationResultState.ALREADY_APPLIED, retried.state());
        assertEquals(1, fixture.mutations().listAudit(fixture.projectId(), fixture.changeId()).size(),
                "idempotent retry must not duplicate audit");
        assertEquals(1, retried.lifecycleState().orElseThrow().revision().value());
    }

    @Test
    void capabilityConfirmationAndStaleRevisionAreIndependentGuards() {
        Fixture fixture = fixture();
        ChangeLifecycleMutationCommand confirmed = command(
                fixture.projectId(), fixture.changeId(), "m17-guards", 0, ChangeLifecycleState.PROPOSED, true);

        var deniedService = new ControlledChangeLifecycleMutationService(
                fixture.transitionService(),
                fixture.mutations(),
                projectId -> ChangeWriteCapabilityObservation.denied("WRITE_CHANGE unavailable"));
        assertEquals(ChangeLifecycleMutationResultState.NOT_AUTHORIZED,
                deniedService.apply(confirmed, ChangeLifecycleMutationPolicy.strict()).state());
        assertTrue(fixture.mutations().findState(fixture.projectId(), fixture.changeId()).isEmpty());

        ChangeLifecycleMutationCommand unconfirmed = command(
                fixture.projectId(), fixture.changeId(), "m17-confirm", 0, ChangeLifecycleState.PROPOSED, false);
        assertEquals(ChangeLifecycleMutationResultState.REQUIRES_CONFIRMATION,
                fixture.service().apply(unconfirmed, ChangeLifecycleMutationPolicy.strict()).state());
        assertTrue(fixture.mutations().findState(fixture.projectId(), fixture.changeId()).isEmpty());

        assertEquals(ChangeLifecycleMutationResultState.APPLIED,
                fixture.service().apply(confirmed, ChangeLifecycleMutationPolicy.strict()).state());
        ChangeLifecycleMutationCommand stale = command(
                fixture.projectId(), fixture.changeId(), "m17-stale", 0, ChangeLifecycleState.SPECIFIED, true);
        assertEquals(ChangeLifecycleMutationResultState.CONFLICT,
                fixture.service().apply(stale, ChangeLifecycleMutationPolicy.strict()).state());
        assertEquals(1, fixture.mutations().listAudit(fixture.projectId(), fixture.changeId()).size());
    }

    @Test
    void idempotencyKeyCannotBeReusedForDifferentLogicalCommand() {
        Fixture fixture = fixture();
        ChangeLifecycleMutationCommand first = command(
                fixture.projectId(), fixture.changeId(), "same-key", 0, ChangeLifecycleState.PROPOSED, true);
        assertEquals(ChangeLifecycleMutationResultState.APPLIED,
                fixture.service().apply(first, ChangeLifecycleMutationPolicy.strict()).state());

        ChangeLifecycleMutationCommand different = command(
                fixture.projectId(), fixture.changeId(), "same-key", 0, ChangeLifecycleState.ABANDONED, true);
        var result = fixture.service().apply(different, ChangeLifecycleMutationPolicy.strict());
        assertEquals(ChangeLifecycleMutationResultState.CONFLICT, result.state());
        assertEquals(1, fixture.mutations().listAudit(fixture.projectId(), fixture.changeId()).size());
    }

    @Test
    void sqlitePersistsStateAuditIdempotencyAndRejectsStaleWriterAcrossConnections() {
        Path database = tempDir.resolve("m17-lifecycle.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        ChangeId changeId = ChangeId.generate();
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database)) {
            snapshots.putProject(new ProjectStoreEntry(projectId, new SourceLocator("file", tempDir.toString())));
        }

        ChangeLifecycleMutationAttempt first = attempt(
                projectId, changeId, "sqlite-first", 0, ChangeLifecycleState.DRAFT, ChangeLifecycleState.PROPOSED);
        try (var writerA = new SqliteChangeLifecycleMutationStore(database);
             var writerB = new SqliteChangeLifecycleMutationStore(database)) {
            assertEquals(com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationPersistenceState.APPLIED,
                    writerA.apply(first).state());
            assertEquals(1, writerA.findState(projectId, changeId).orElseThrow().revision().value());

            ChangeLifecycleMutationAttempt stale = attempt(
                    projectId, changeId, "sqlite-stale", 0, ChangeLifecycleState.DRAFT, ChangeLifecycleState.PROPOSED);
            assertEquals(com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationPersistenceState.CONFLICT,
                    writerB.apply(stale).state());
            assertEquals(1, writerB.listAudit(projectId, changeId).size());

            ChangeLifecycleMutationAttempt retry = new ChangeLifecycleMutationAttempt(
                    ChangeLifecycleMutationId.generate(),
                    first.idempotencyKey(),
                    first.commandFingerprint(),
                    first.projectId(),
                    first.changeId(),
                    first.fromState(),
                    first.targetState(),
                    first.targetAbandonmentReason(),
                    first.expectedRevision(),
                    first.actor(),
                    first.providerId(),
                    first.reason(),
                    T0.plusSeconds(5));
            assertEquals(com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationPersistenceState.ALREADY_APPLIED,
                    writerB.apply(retry).state());
            assertEquals(1, writerB.listAudit(projectId, changeId).size());
        }

        try (var reopened = new SqliteChangeLifecycleMutationStore(database)) {
            ChangeLifecycleOperationalState state = reopened.findState(projectId, changeId).orElseThrow();
            assertEquals(ChangeLifecycleState.PROPOSED, state.lifecycle().state());
            assertEquals(1, state.revision().value());
            assertEquals(1, reopened.listAudit(projectId, changeId).size());
            assertTrue(reopened.findByIdempotencyKey(projectId, first.idempotencyKey()).isPresent());
        }
    }

    @Test
    void writeCapabilityRequiresExplicitProviderCapabilityAndRejectsReadOnlyOpenSpec() {
        MemorySpecificationKnowledgeStore projects = new MemorySpecificationKnowledgeStore();

        Path synthetic = fixturePath("synthetic-basic");
        ProjectSpecificationId syntheticProject = ProjectSpecificationId.generate();
        projects.putProject(new ProjectStoreEntry(
                syntheticProject, new SourceLocator("file", synthetic.toAbsolutePath().toString())));
        var syntheticResolver = new RegisteredProjectWriteCapabilityResolver(
                projects,
                new SpecificationProviderRegistry(
                        List.of(new SyntheticSpecificationProvider()),
                        new ProviderSelectionPolicy()));
        var allowed = syntheticResolver.resolve(syntheticProject);
        assertTrue(allowed.writeAllowed());
        assertEquals(SyntheticSpecificationProvider.ID, allowed.providerId().orElseThrow());

        Path openspec = fixturePath("openspec-basic");
        ProjectSpecificationId openspecProject = ProjectSpecificationId.generate();
        projects.putProject(new ProjectStoreEntry(
                openspecProject, new SourceLocator("file", openspec.toAbsolutePath().toString())));
        var openspecResolver = new RegisteredProjectWriteCapabilityResolver(
                projects,
                new SpecificationProviderRegistry(
                        List.of(new OpenSpecSpecificationProvider()),
                        new ProviderSelectionPolicy()));
        var denied = openspecResolver.resolve(openspecProject);
        assertFalse(denied.writeAllowed());
        assertTrue(denied.reason().contains("WRITE_CHANGE"));
    }

    private Fixture fixture() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        ChangeId changeId = ChangeId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        Evidence evidence = new Evidence(
                EvidenceId.generate(),
                SourceLocator.file("m17/change.md"),
                Optional.empty(),
                Optional.of("sha256:m17"));
        Provenance provenance = new Provenance(
                WRITE_PROVIDER,
                Optional.of("1"),
                SourceLocator.file("m17/change.md"),
                Optional.of("change:m17"),
                Optional.of("revision-m17"),
                evidence.id());
        ChangeProposal change = new ChangeProposal(
                changeId,
                projectId,
                Optional.of("m17-controlled-write"),
                "Controlled lifecycle write",
                "Prove explicit controlled mutation",
                List.of(), List.of(), List.of(), provenance);

        MemorySpecificationKnowledgeStore core = new MemorySpecificationKnowledgeStore();
        MemorySnapshotBusinessContentStore content = new MemorySnapshotBusinessContentStore(core, core);
        MemoryTraceabilityStore traceability = new MemoryTraceabilityStore(core);
        core.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace-m17")));
        core.putSpecificationVersion(new SpecificationVersion(
                versionId, projectId, Optional.of(1L), Optional.of("m17-provider"),
                Optional.of("revision-m17"), T0, Optional.empty()));
        core.putSnapshot(new KnowledgeSnapshotMetadata(
                snapshotId, projectId, Optional.empty(), KnowledgeSnapshotState.READY,
                Optional.of("revision-m17"), T0));
        core.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionId));
        content.putSnapshotContent(new SnapshotBusinessContent(
                snapshotId, versionId, List.of(), List.of(), List.of(change), List.of(),
                List.of(), List.of(), List.of(evidence)));
        core.activateSnapshot(snapshotId, Optional.empty());

        MemoryChangeLifecycleMutationStore mutations = new MemoryChangeLifecycleMutationStore(core);
        ChangeTransitionEvaluationService transitions = new ChangeTransitionEvaluationService(
                core, content, core, traceability);
        ControlledChangeLifecycleMutationService service = new ControlledChangeLifecycleMutationService(
                transitions,
                mutations,
                id -> ChangeWriteCapabilityObservation.allowed(WRITE_PROVIDER, "explicit WRITE_CHANGE fixture"),
                com.morpheus.application.lifecycle.ChangeLifecyclePolicy.forwardOnly(),
                Clock.fixed(T0, ZoneOffset.UTC));
        return new Fixture(projectId, changeId, transitions, mutations, service);
    }

    private ChangeLifecycleMutationCommand command(
            ProjectSpecificationId projectId,
            ChangeId changeId,
            String key,
            long expectedRevision,
            ChangeLifecycleState target,
            boolean confirmed) {
        return new ChangeLifecycleMutationCommand(
                ChangeLifecycleMutationId.generate(),
                new ChangeLifecycleIdempotencyKey(key),
                projectId,
                changeId,
                new ChangeLifecycleRevision(expectedRevision),
                target,
                target == ChangeLifecycleState.ABANDONED
                        ? Optional.of(com.morpheus.domain.change.lifecycle.ChangeAbandonmentReason.NO_LONGER_NEEDED)
                        : Optional.empty(),
                confirmed,
                "m17-test",
                T0);
    }

    private ChangeLifecycleMutationAttempt attempt(
            ProjectSpecificationId projectId,
            ChangeId changeId,
            String key,
            long expectedRevision,
            ChangeLifecycleState from,
            ChangeLifecycleState target) {
        return new ChangeLifecycleMutationAttempt(
                ChangeLifecycleMutationId.generate(),
                new ChangeLifecycleIdempotencyKey(key),
                "fingerprint-" + key,
                projectId,
                changeId,
                from,
                target,
                Optional.empty(),
                new ChangeLifecycleRevision(expectedRevision),
                "m17-test",
                WRITE_PROVIDER,
                "M17 persistence contract",
                T0);
    }

    private Path fixturePath(String name) {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("experiments/m0/fixtures").resolve(name);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("fixture not found: " + name);
    }

    private record Fixture(
            ProjectSpecificationId projectId,
            ChangeId changeId,
            ChangeTransitionEvaluationService transitionService,
            MemoryChangeLifecycleMutationStore mutations,
            ControlledChangeLifecycleMutationService service) {
    }
}
