package com.morpheus.application.quality;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.acceptance.AcceptanceCriterionId;
import com.morpheus.domain.acceptance.VerificationStatus;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import com.morpheus.store.memory.MemorySnapshotBusinessContentStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcceptanceQualityServiceTest {
    private static final Instant T0 = Instant.parse("2026-07-26T12:00:00Z");

    @Test
    void evaluatesAllVerificationStatesWithoutConflatingUnknownAndFailed() {
        Fixture fixture = fixture();
        var core = new MemorySpecificationKnowledgeStore();
        var contentStore = new MemorySnapshotBusinessContentStore(core, core);
        seed(core, contentStore, fixture, fixture.criteria());

        AcceptanceCoverageAssessment assessment = new AcceptanceQualityService(core, contentStore)
                .assessActive(fixture.projectId())
                .orElseThrow();

        assertEquals(AcceptanceCoverageStatus.EVALUATED, assessment.status());
        assertEquals(5, assessment.totalCriteria());
        assertEquals(1, assessment.verifiedCriteria());
        assertEquals(1, assessment.partiallyVerifiedCriteria());
        assertEquals(1, assessment.failedCriteria());
        assertEquals(1, assessment.notVerifiedCriteria());
        assertEquals(1, assessment.unknownCriteria());
        assertEquals(0.2, assessment.verifiedCoverageRatio());
        assertEquals(4, assessment.findings().size());
        assertTrue(assessment.findings().stream().anyMatch(finding ->
                finding.code() == QualityFindingCode.ACCEPTANCE_CRITERION_FAILED
                        && finding.severity() == DiagnosticSeverity.ERROR));
        assertTrue(assessment.findings().stream().anyMatch(finding ->
                finding.code() == QualityFindingCode.ACCEPTANCE_CRITERION_UNKNOWN
                        && finding.severity() == DiagnosticSeverity.INFO));
        assertTrue(assessment.findings().stream().noneMatch(finding ->
                finding.details().get("verificationStatus").equals(VerificationStatus.VERIFIED.name())));
    }

    @Test
    void noCriteriaIsAvailableButEmptyRatherThanUnavailable() {
        Fixture fixture = fixture();
        var core = new MemorySpecificationKnowledgeStore();
        var contentStore = new MemorySnapshotBusinessContentStore(core, core);
        seed(core, contentStore, fixture, List.of());

        AcceptanceCoverageAssessment assessment = new AcceptanceQualityService(core, contentStore)
                .assessActive(fixture.projectId())
                .orElseThrow();

        assertEquals(AcceptanceCoverageStatus.NO_CRITERIA, assessment.status());
        assertEquals(0, assessment.totalCriteria());
        assertEquals(1.0, assessment.verifiedCoverageRatio());
        assertTrue(assessment.findings().isEmpty());
    }

    private void seed(
            MemorySpecificationKnowledgeStore core,
            MemorySnapshotBusinessContentStore contentStore,
            Fixture fixture,
            List<AcceptanceCriterion> criteria) {
        core.putProject(new ProjectStoreEntry(fixture.projectId(), SourceLocator.file("workspace")));
        core.putSpecificationVersion(new SpecificationVersion(
                fixture.versionId(),
                fixture.projectId(),
                Optional.of(1L),
                Optional.of("provider-v1"),
                Optional.of("revision-1"),
                T0,
                Optional.empty()));
        core.putSnapshot(new KnowledgeSnapshotMetadata(
                fixture.snapshotId(),
                fixture.projectId(),
                Optional.empty(),
                KnowledgeSnapshotState.READY,
                Optional.of("revision-1"),
                T0));
        core.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(fixture.snapshotId(), fixture.versionId()));
        contentStore.putSnapshotContent(new SnapshotBusinessContent(
                fixture.snapshotId(),
                fixture.versionId(),
                List.of(),
                List.of(),
                List.of(fixture.change()),
                List.of(),
                List.of(),
                List.of(),
                criteria,
                fixture.evidence()));
        core.activateSnapshot(fixture.snapshotId(), Optional.empty());
    }

    private Fixture fixture() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        Evidence source = evidence("source");
        Evidence partialProof = evidence("partial");
        Evidence verifiedProof = evidence("verified");
        Evidence failedProof = evidence("failed");
        Provenance provenance = provenance(source);
        ChangeProposal change = new ChangeProposal(
                ChangeId.generate(),
                projectId,
                Optional.of("quality-change"),
                "Quality change",
                "Exercise acceptance verification states",
                List.of(),
                List.of(),
                List.of(),
                provenance);
        RequirementId requirementId = RequirementId.generate();
        List<AcceptanceCriterion> criteria = List.of(
                criterion(requirementId, change.id(), "unknown", VerificationStatus.UNKNOWN, List.of(), provenance),
                criterion(requirementId, change.id(), "not-verified", VerificationStatus.NOT_VERIFIED, List.of(), provenance),
                criterion(requirementId, change.id(), "partial", VerificationStatus.PARTIALLY_VERIFIED, List.of(partialProof.id()), provenance),
                criterion(requirementId, change.id(), "verified", VerificationStatus.VERIFIED, List.of(verifiedProof.id()), provenance),
                criterion(requirementId, change.id(), "failed", VerificationStatus.FAILED, List.of(failedProof.id()), provenance));
        return new Fixture(
                projectId,
                snapshotId,
                versionId,
                change,
                criteria,
                List.of(source, partialProof, verifiedProof, failedProof));
    }

    private AcceptanceCriterion criterion(
            RequirementId requirementId,
            ChangeId changeId,
            String key,
            VerificationStatus status,
            List<EvidenceId> evidenceIds,
            Provenance provenance) {
        return new AcceptanceCriterion(
                AcceptanceCriterionId.generate(),
                Optional.of(requirementId),
                Optional.of(changeId),
                "Criterion " + key,
                "Condition " + key,
                status,
                evidenceIds,
                provenance);
    }

    private Evidence evidence(String key) {
        return new Evidence(
                EvidenceId.generate(),
                SourceLocator.file("evidence/" + key + ".txt"),
                Optional.empty(),
                Optional.of("sha256:" + key));
    }

    private Provenance provenance(Evidence evidence) {
        return new Provenance(
                new ProviderId("test-provider"),
                Optional.of("1"),
                evidence.source(),
                Optional.of("source"),
                Optional.of("revision-1"),
                evidence.id());
    }

    private record Fixture(
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            ChangeProposal change,
            List<AcceptanceCriterion> criteria,
            List<Evidence> evidence) {
    }
}
