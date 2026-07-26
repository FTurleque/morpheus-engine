package com.morpheus.application.composition;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryReport;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecification;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.scenario.ScenarioId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiProviderCompositionServiceTest {

    @TempDir
    Path workspace;

    @Test
    void higherPrecedenceWinsAndConflictIsExplicit() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        var high = content(projectId, new ProviderId("alpha"), "payments", "payments/reject", "High statement", true);
        var low = content(projectId, new ProviderId("beta"), "payments", "payments/reject", "Low statement", true);

        var result = new MultiProviderCompositionService().compose(
                ProviderReadRequest.all(workspace, projectId),
                unusedResolver(),
                List.of(source(high, 100, true), source(low, 50, false)));

        assertEquals("High statement", result.content().requirements().getFirst().statement());
        assertEquals(1, result.report().conflicts().size());
        assertEquals(ProviderConflictResolution.RESOLVED_BY_PRECEDENCE, result.report().conflicts().getFirst().resolution());
        assertEquals("alpha", result.report().conflicts().getFirst().winner().orElseThrow().providerId().value());
        assertTrue(result.content().diagnostics().stream().anyMatch(item ->
                item.code() == DiagnosticCode.PROVIDER_COMPOSITION_CONFLICT));
    }

    @Test
    void divergentEqualPrecedenceIsUnresolvedAndBlocksPublicationByDiagnostic() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        var alpha = content(projectId, new ProviderId("alpha"), "payments", "payments/reject", "One", true);
        var beta = content(projectId, new ProviderId("beta"), "payments", "payments/reject", "Two", true);

        var result = new MultiProviderCompositionService().compose(
                ProviderReadRequest.all(workspace, projectId),
                unusedResolver(),
                List.of(source(alpha, 100, true), source(beta, 100, true)));

        assertTrue(result.report().hasUnresolvedConflicts());
        assertTrue(result.report().conflicts().stream().anyMatch(item ->
                item.resolution() == ProviderConflictResolution.UNRESOLVED_EQUAL_PRECEDENCE));
        assertTrue(result.content().diagnostics().stream().anyMatch(item ->
                item.code() == DiagnosticCode.PROVIDER_COMPOSITION_CONFLICT
                        && item.severity() == com.morpheus.domain.diagnostic.DiagnosticSeverity.ERROR));
    }

    @Test
    void losingSpecificationIdentityIsRemappedForUniqueRequirementAndScenario() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        ProviderId highProvider = new ProviderId("alpha");
        ProviderId lowProvider = new ProviderId("beta");
        var high = content(projectId, highProvider, "payments", null, null, false);
        var low = content(projectId, lowProvider, "payments", "payments/unique-low", "Low only", true);

        SpecificationId highSpecificationId = high.specifications().getFirst().id();
        RequirementId lowRequirementId = low.requirements().getFirst().id();

        var result = new MultiProviderCompositionService().compose(
                ProviderReadRequest.all(workspace, projectId),
                unusedResolver(),
                List.of(source(high, 100, true), source(low, 50, false)));

        Requirement requirement = result.content().requirements().getFirst();
        Scenario scenario = result.content().scenarios().getFirst();
        assertEquals(lowRequirementId, requirement.id());
        assertEquals(highSpecificationId, requirement.specificationId());
        assertEquals(lowRequirementId, scenario.requirementId().orElseThrow());
    }

    @Test
    void absentOptionalProviderDoesNotEraseSuccessfulProvider() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        var alpha = content(projectId, new ProviderId("alpha"), "payments", "payments/reject", "One", false);
        SpecificationContentReader absent = new SpecificationContentReader() {
            @Override
            public ProviderId providerId() {
                return new ProviderId("optional");
            }

            @Override
            public ProviderReadResult read(ProviderReadRequest request, EntityIdentityResolver identityResolver) {
                return new ProviderReadResult(
                        providerId(),
                        Optional.empty(),
                        List.of(ReadCategoryReport.of(ReadCategory.REQUIREMENTS, ReadCategoryStatus.ABSENT, 0)),
                        List.of());
            }
        };

        var result = new MultiProviderCompositionService().compose(
                ProviderReadRequest.all(workspace, projectId),
                unusedResolver(),
                List.of(source(alpha, 100, true), new ProviderCompositionSource(absent, 50, false)));

        assertEquals(1, result.content().requirements().size());
        assertEquals(2, result.report().contributions().size());
        assertFalse(result.content().diagnostics().stream().anyMatch(item ->
                item.code() == DiagnosticCode.REQUIRED_PROVIDER_UNAVAILABLE));
    }

    private ProviderCompositionSource source(NormalizedProjectContent content, int precedence, boolean required) {
        ProviderId providerId = content.specifications().getFirst().provenance().providerId();
        SpecificationContentReader reader = new SpecificationContentReader() {
            @Override
            public ProviderId providerId() {
                return providerId;
            }

            @Override
            public ProviderReadResult read(ProviderReadRequest request, EntityIdentityResolver identityResolver) {
                return new ProviderReadResult(
                        providerId,
                        Optional.of(content),
                        List.of(
                                ReadCategoryReport.of(ReadCategory.CURRENT_SPECIFICATIONS, ReadCategoryStatus.READ, content.specifications().size()),
                                ReadCategoryReport.of(ReadCategory.REQUIREMENTS, content.requirements().isEmpty() ? ReadCategoryStatus.ABSENT : ReadCategoryStatus.READ, content.requirements().size()),
                                ReadCategoryReport.of(ReadCategory.SCENARIOS, content.scenarios().isEmpty() ? ReadCategoryStatus.ABSENT : ReadCategoryStatus.READ, content.scenarios().size())),
                        List.of());
            }
        };
        return new ProviderCompositionSource(reader, precedence, required);
    }

    private NormalizedProjectContent content(
            ProjectSpecificationId projectId,
            ProviderId providerId,
            String specificationKey,
            String requirementKey,
            String statement,
            boolean scenario) {
        SourceLocator source = SourceLocator.file(providerId.value() + "/spec.md");
        Evidence specificationEvidence = evidence(source);
        SpecificationId specificationId = new SpecificationId(DomainIdentity.generate());
        Specification specification = new Specification(
                specificationId,
                projectId,
                specificationKey,
                "Payments",
                Optional.empty(),
                provenance(providerId, source, "specification:" + specificationKey, specificationEvidence.id()));

        if (requirementKey == null) {
            return new NormalizedProjectContent(
                    project(projectId),
                    List.of(specification),
                    List.of(),
                    List.of(),
                    List.of(specificationEvidence),
                    List.of());
        }

        Evidence requirementEvidence = evidence(source);
        RequirementId requirementId = new RequirementId(DomainIdentity.generate());
        Requirement requirement = new Requirement(
                requirementId,
                specificationId,
                Optional.of(requirementKey),
                "Reject invalid",
                statement,
                provenance(providerId, source, "requirement:" + requirementKey, requirementEvidence.id()));
        if (!scenario) {
            return new NormalizedProjectContent(
                    project(projectId),
                    List.of(specification),
                    List.of(requirement),
                    List.of(),
                    List.of(specificationEvidence, requirementEvidence),
                    List.of());
        }

        Evidence scenarioEvidence = evidence(source);
        Scenario scenarioValue = new Scenario(
                new ScenarioId(DomainIdentity.generate()),
                Optional.of(requirementId),
                "Rejected card",
                List.of("invalid card"),
                "submit payment",
                "payment is rejected",
                provenance(providerId, source, "scenario:" + requirementKey, scenarioEvidence.id()));
        return new NormalizedProjectContent(
                project(projectId),
                List.of(specification),
                List.of(requirement),
                List.of(scenarioValue),
                List.of(specificationEvidence, requirementEvidence, scenarioEvidence),
                List.of());
    }

    private ProjectSpecification project(ProjectSpecificationId projectId) {
        return new ProjectSpecification(projectId, "project", SourceLocator.file(workspace.toString()));
    }

    private Evidence evidence(SourceLocator source) {
        return new Evidence(EvidenceId.generate(), source, Optional.empty(), Optional.empty());
    }

    private Provenance provenance(ProviderId providerId, SourceLocator source, String externalId, EvidenceId evidenceId) {
        return new Provenance(
                providerId,
                Optional.of("test"),
                source,
                Optional.of(externalId),
                Optional.empty(),
                evidenceId);
    }

    private EntityIdentityResolver unusedResolver() {
        return (providerId, entityType, externalId) -> DomainIdentity.generate();
    }
}
