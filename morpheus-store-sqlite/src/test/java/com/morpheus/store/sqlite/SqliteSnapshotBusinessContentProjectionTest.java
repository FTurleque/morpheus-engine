package com.morpheus.store.sqlite;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.acceptance.AcceptanceCriterionId;
import com.morpheus.domain.acceptance.VerificationStatus;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.decision.DesignDecisionId;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.evidence.SourceRange;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.scenario.ScenarioId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.task.TaskId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteSnapshotBusinessContentProjectionTest {
    private static final Instant T0 = Instant.parse("2026-09-07T12:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void completeProjectionRoundTripsThroughExtractedReaderAndWriter() {
        Path database = tempDir.resolve("complete-projection.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        RequirementId requirementId = RequirementId.generate();

        Evidence evidence = new Evidence(
                EvidenceId.generate(),
                SourceLocator.file("specs/billing.md"),
                Optional.of(new SourceRange(10, 18)),
                Optional.of("sha256:billing"));
        Evidence verificationEvidence = new Evidence(
                EvidenceId.generate(),
                SourceLocator.file("tests/billing.txt"),
                Optional.empty(),
                Optional.empty());
        Provenance provenance = new Provenance(
                new ProviderId("test-provider"),
                Optional.of("1.0"),
                SourceLocator.file("specs/billing.md"),
                Optional.of("SOURCE-1"),
                Optional.of("revision-1"),
                evidence.id());

        Specification specification = new Specification(
                SpecificationId.generate(), projectId, "billing", "Billing", Optional.of("Billing rules"), provenance);
        Scenario scenario = new Scenario(
                ScenarioId.generate(), Optional.of(requirementId), "Pay invoice",
                List.of("account exists", "invoice is open"), "user pays", "invoice is paid", provenance);
        ChangeProposal change = new ChangeProposal(
                ChangeId.generate(), projectId, Optional.of("CHG-1"), "Harden billing", "Make billing deterministic",
                List.of("billing", "api"), List.of("mobile"), List.of("migration", "rollback"), provenance);
        Constraint constraint = new Constraint(
                ConstraintId.generate(), change.id(), "audit history must be preserved", provenance);
        DesignDecision decision = new DesignDecision(
                DesignDecisionId.generate(), change.id(), "Explicit state", "Persist transitions", provenance);
        ImplementationTask task = new ImplementationTask(
                TaskId.generate(), change.id(), Optional.of("TASK-1"), "Implement state", false, provenance);
        AcceptanceCriterion verified = new AcceptanceCriterion(
                AcceptanceCriterionId.generate(), Optional.of(requirementId), Optional.empty(),
                "Retention honored", "invoice remains available", VerificationStatus.VERIFIED,
                List.of(verificationEvidence.id()), provenance);
        AcceptanceCriterion changeCriterion = new AcceptanceCriterion(
                AcceptanceCriterionId.generate(), Optional.empty(), Optional.of(change.id()),
                "Flow verified", "new flow passes", VerificationStatus.NOT_VERIFIED, List.of(), provenance);

        SnapshotBusinessContent expected = new SnapshotBusinessContent(
                snapshotId,
                versionId,
                List.of(specification),
                List.of(scenario),
                List.of(change),
                List.of(constraint),
                List.of(decision),
                List.of(task),
                List.of(verified, changeCriterion),
                List.of(evidence, verificationEvidence));

        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var store = new SqliteSnapshotBusinessContentStore(database)) {
            snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
            snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                    snapshotId, projectId, Optional.empty(), KnowledgeSnapshotState.READY,
                    Optional.of("revision-1"), T0));
            versions.putSpecificationVersion(new SpecificationVersion(
                    versionId, projectId, Optional.of(1L), Optional.of("provider-v1"),
                    Optional.of("revision-1"), T0, Optional.empty()));
            versions.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionId));

            assertTrue(store.findSnapshotContent(snapshotId).isEmpty());
            store.putSnapshotContent(expected);
            store.putSnapshotContent(expected);
            assertEquals(expected, store.findSnapshotContent(snapshotId).orElseThrow());
        }

        try (var reopened = new SqliteSnapshotBusinessContentStore(database)) {
            assertEquals(expected, reopened.findSnapshotContent(snapshotId).orElseThrow());
        }
    }
}
