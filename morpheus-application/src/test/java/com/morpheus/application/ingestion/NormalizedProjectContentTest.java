package com.morpheus.application.ingestion;

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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NormalizedProjectContentTest {

    @Test
    void acceptsCoherentProjectSpecificationRequirementAndEvidenceGraph() {
        Fixture fixture = fixture();

        assertDoesNotThrow(() -> new NormalizedProjectContent(
                fixture.project,
                List.of(fixture.specification),
                List.of(fixture.requirement),
                List.of(),
                List.of(fixture.evidence),
                List.of()));
    }

    @Test
    void rejectsRequirementReferencingUnknownSpecification() {
        Fixture fixture = fixture();
        Requirement invalid = new Requirement(
                fixture.requirement.id(),
                SpecificationId.generate(),
                fixture.requirement.key(),
                fixture.requirement.title(),
                fixture.requirement.statement(),
                fixture.requirement.provenance());

        assertThrows(IllegalArgumentException.class, () -> new NormalizedProjectContent(
                fixture.project,
                List.of(fixture.specification),
                List.of(invalid),
                List.of(),
                List.of(fixture.evidence),
                List.of()));
    }

    @Test
    void rejectsProvenanceReferencingUnknownEvidence() {
        Fixture fixture = fixture();
        Provenance invalidProvenance = new Provenance(
                fixture.requirement.provenance().providerId(),
                fixture.requirement.provenance().providerVersion(),
                fixture.requirement.provenance().source(),
                fixture.requirement.provenance().externalId(),
                fixture.requirement.provenance().sourceRevision(),
                EvidenceId.generate());
        Requirement invalid = new Requirement(
                fixture.requirement.id(),
                fixture.requirement.specificationId(),
                fixture.requirement.key(),
                fixture.requirement.title(),
                fixture.requirement.statement(),
                invalidProvenance);

        assertThrows(IllegalArgumentException.class, () -> new NormalizedProjectContent(
                fixture.project,
                List.of(fixture.specification),
                List.of(invalid),
                List.of(),
                List.of(fixture.evidence),
                List.of()));
    }

    private Fixture fixture() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SourceLocator source = SourceLocator.file("openspec/specs/auth-session/spec.md");
        Evidence evidence = new Evidence(EvidenceId.generate(), source, Optional.empty(), Optional.empty());
        Provenance provenance = new Provenance(
                new ProviderId("test-provider"),
                Optional.of("1.0"),
                source,
                Optional.of("external"),
                Optional.empty(),
                evidence.id());
        ProjectSpecification project = new ProjectSpecification(
                projectId,
                "test-project",
                SourceLocator.file("workspace"));
        Specification specification = new Specification(
                SpecificationId.generate(),
                projectId,
                "auth-session",
                "Authentication Session Specification",
                Optional.empty(),
                provenance);
        Requirement requirement = new Requirement(
                RequirementId.generate(),
                specification.id(),
                Optional.of("auth-session/session-expiration"),
                "Session expiration",
                "The system SHALL expire an inactive session.",
                provenance);
        return new Fixture(project, specification, requirement, evidence);
    }

    private record Fixture(
            ProjectSpecification project,
            Specification specification,
            Requirement requirement,
            Evidence evidence) {
    }
}
