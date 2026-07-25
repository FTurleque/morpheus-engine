package com.morpheus.application.traceability;

import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.acceptance.AcceptanceCriterionId;
import com.morpheus.domain.acceptance.VerificationStatus;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecification;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcceptanceTraceabilityDerivationTest {
    private static final Instant OBSERVED_AT = Instant.parse("2026-07-26T10:00:00Z");

    @Test
    void derivesOnlyExplicitAcceptanceOwnershipAndVerificationEvidenceLinks() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        Evidence specificationEvidence = evidence("specification");
        Evidence requirementEvidence = evidence("requirement");
        Evidence changeEvidence = evidence("change");
        Evidence criterionEvidence = evidence("criterion");
        Evidence verificationEvidence = evidence("verification-result");

        Specification specification = new Specification(
                SpecificationId.generate(),
                projectId,
                "billing",
                "Billing",
                Optional.empty(),
                provenance(specificationEvidence, "SPEC-BILLING"));
        Requirement requirement = new Requirement(
                RequirementId.generate(),
                specification.id(),
                Optional.of("billing/retention"),
                "Invoice retention",
                "Invoices SHALL remain available for the legal period.",
                provenance(requirementEvidence, "REQ-RETENTION"));
        ChangeProposal change = new ChangeProposal(
                ChangeId.generate(),
                projectId,
                Optional.of("extend-retention"),
                "Extend retention",
                "Support an extended legal period.",
                List.of(),
                List.of(),
                List.of(),
                provenance(changeEvidence, "CHANGE-RETENTION"));
        AcceptanceCriterion criterion = new AcceptanceCriterion(
                AcceptanceCriterionId.generate(),
                Optional.of(requirement.id()),
                Optional.of(change.id()),
                "Retention is honored",
                "The invoice remains queryable throughout the configured retention period.",
                VerificationStatus.VERIFIED,
                List.of(verificationEvidence.id()),
                provenance(criterionEvidence, "AC-RETENTION"));

        NormalizedProjectContent content = new NormalizedProjectContent(
                new ProjectSpecification(projectId, "acceptance-project", SourceLocator.file("workspace")),
                List.of(specification),
                List.of(requirement),
                List.of(),
                List.of(change),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(criterion),
                List.of(
                        specificationEvidence,
                        requirementEvidence,
                        changeEvidence,
                        criterionEvidence,
                        verificationEvidence),
                List.of());

        List<TraceabilityLink> links = new DeterministicTraceabilityDerivationService().derive(
                content,
                ignored -> Optional.of(TraceabilityLinkId.generate()),
                OBSERVED_AT);

        assertEquals(4, links.size());

        TraceabilityLink requirementToCriterion = find(
                links,
                TraceabilityEntityKind.REQUIREMENT,
                requirement.id().toString(),
                TraceabilityEntityKind.ACCEPTANCE_CRITERION,
                criterion.id().toString());
        assertEquals(TraceabilityRelationType.VERIFIED_BY, requirementToCriterion.relationType());
        assertEquals(java.util.Set.of(criterionEvidence.id()), requirementToCriterion.evidenceIds());

        TraceabilityLink changeToCriterion = find(
                links,
                TraceabilityEntityKind.CHANGE,
                change.id().toString(),
                TraceabilityEntityKind.ACCEPTANCE_CRITERION,
                criterion.id().toString());
        assertEquals(TraceabilityRelationType.VERIFIED_BY, changeToCriterion.relationType());
        assertEquals(java.util.Set.of(criterionEvidence.id()), changeToCriterion.evidenceIds());

        TraceabilityLink criterionToEvidence = find(
                links,
                TraceabilityEntityKind.ACCEPTANCE_CRITERION,
                criterion.id().toString(),
                TraceabilityEntityKind.EVIDENCE,
                verificationEvidence.id().toString());
        assertEquals(TraceabilityRelationType.VERIFIED_BY, criterionToEvidence.relationType());
        assertEquals(java.util.Set.of(verificationEvidence.id()), criterionToEvidence.evidenceIds());

        assertTrue(links.stream().noneMatch(link ->
                link.target().kind() == TraceabilityEntityKind.EVIDENCE
                        && link.target().identity().equals(criterionEvidence.id().value())));
    }

    @Test
    void notVerifiedCriterionDoesNotInventVerificationEvidenceLink() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        Evidence specificationEvidence = evidence("specification");
        Evidence requirementEvidence = evidence("requirement");
        Evidence criterionEvidence = evidence("criterion");
        Specification specification = new Specification(
                SpecificationId.generate(),
                projectId,
                "auth",
                "Authentication",
                Optional.empty(),
                provenance(specificationEvidence, "SPEC-AUTH"));
        Requirement requirement = new Requirement(
                RequirementId.generate(),
                specification.id(),
                Optional.of("auth/session"),
                "Session security",
                "Sessions SHALL expire.",
                provenance(requirementEvidence, "REQ-AUTH"));
        AcceptanceCriterion criterion = new AcceptanceCriterion(
                AcceptanceCriterionId.generate(),
                Optional.of(requirement.id()),
                Optional.empty(),
                "Expiration accepted",
                "An expired session is rejected.",
                VerificationStatus.NOT_VERIFIED,
                List.of(),
                provenance(criterionEvidence, "AC-AUTH"));

        NormalizedProjectContent content = new NormalizedProjectContent(
                new ProjectSpecification(projectId, "auth-project", SourceLocator.file("workspace")),
                List.of(specification),
                List.of(requirement),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(criterion),
                List.of(specificationEvidence, requirementEvidence, criterionEvidence),
                List.of());

        List<TraceabilityLink> links = new DeterministicTraceabilityDerivationService().derive(
                content,
                ignored -> Optional.of(TraceabilityLinkId.generate()),
                OBSERVED_AT);

        assertTrue(links.stream().noneMatch(link -> link.target().kind() == TraceabilityEntityKind.EVIDENCE));
    }

    private TraceabilityLink find(
            List<TraceabilityLink> links,
            TraceabilityEntityKind sourceKind,
            String sourceId,
            TraceabilityEntityKind targetKind,
            String targetId) {
        return links.stream()
                .filter(link -> link.source().kind() == sourceKind)
                .filter(link -> link.source().identity().toString().equals(sourceId))
                .filter(link -> link.target().kind() == targetKind)
                .filter(link -> link.target().identity().toString().equals(targetId))
                .findFirst()
                .orElseThrow();
    }

    private static Evidence evidence(String name) {
        return new Evidence(
                EvidenceId.generate(),
                SourceLocator.file("evidence/" + name + ".txt"),
                Optional.empty(),
                Optional.of("sha256:" + name));
    }

    private static Provenance provenance(Evidence evidence, String externalId) {
        return new Provenance(
                new ProviderId("test-provider"),
                Optional.of("1"),
                evidence.source(),
                Optional.of(externalId),
                Optional.of("revision-1"),
                evidence.id());
    }
}
