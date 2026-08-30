package com.morpheus.application.ingestion;

import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecification;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.scenario.ScenarioId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

class NormalizedScenarioIdentityTest {

    @Test
    void rejectsDuplicateScenarioIdentityBeforePersistenceProjection() {
        SourceLocator source = SourceLocator.file("spec.md");
        Evidence evidence = new Evidence(EvidenceId.generate(), source, Optional.empty(), Optional.empty());
        Provenance provenance = new Provenance(
                new ProviderId("test-provider"),
                Optional.of("1"),
                source,
                Optional.of("scenario:duplicate"),
                Optional.empty(),
                evidence.id());
        ProjectSpecification project = new ProjectSpecification(
                ProjectSpecificationId.generate(), "project", SourceLocator.file("workspace"));
        ScenarioId id = ScenarioId.generate();
        Scenario first = new Scenario(id, Optional.empty(), "First", List.of(), "when", "then", provenance);
        Scenario second = new Scenario(id, Optional.empty(), "Second", List.of(), "when", "then", provenance);

        assertThrows(IllegalArgumentException.class, () -> new NormalizedProjectContent(
                project,
                List.of(),
                List.of(),
                List.of(first, second),
                List.of(evidence),
                List.of()));
    }
}
