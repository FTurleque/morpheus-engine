package com.morpheus.application.query.dsl;

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
import com.morpheus.domain.portfolio.CrossProjectReference;
import com.morpheus.domain.portfolio.CrossProjectReferenceId;
import com.morpheus.domain.portfolio.PortfolioEntityRef;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.portfolio.PortfolioMembership;
import com.morpheus.domain.portfolio.PortfolioMembershipStatus;
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
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.task.TaskId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryRowMapperTest {
    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
    private final QueryRowMapper mapper = new QueryRowMapper();

    @Test
    void mapsEveryPublishedBusinessEntityFamily() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        Evidence evidence = new Evidence(
                EvidenceId.generate(),
                SourceLocator.file("specs/billing.md"),
                Optional.of(new SourceRange(10, 12)),
                Optional.of("sha256:billing"));
        Provenance provenance = new Provenance(
                new ProviderId("openspec"),
                Optional.of("1.0.0"),
                SourceLocator.file("specs/billing.md"),
                Optional.of("SOURCE-1"),
                Optional.of("revision-1"),
                evidence.id());

        Specification specification = new Specification(
                SpecificationId.generate(), projectId, "billing", "Billing", Optional.of("Billing rules"), provenance);
        Requirement requirement = new Requirement(
                RequirementId.generate(), specification.id(), Optional.of("REQ-1"), "Pay invoice", "Invoice can be paid", provenance);
        Scenario scenario = new Scenario(
                ScenarioId.generate(), Optional.of(requirement.id()), "Pay invoice", List.of("invoice is open"),
                "user pays", "invoice is paid", provenance);
        ChangeProposal change = new ChangeProposal(
                ChangeId.generate(), projectId, Optional.of("CHG-1"), "Harden billing", "Make billing deterministic",
                List.of("billing"), List.of("mobile"), List.of("migration"), provenance);
        Constraint constraint = new Constraint(
                ConstraintId.generate(), change.id(), "audit history must be preserved", provenance);
        DesignDecision decision = new DesignDecision(
                DesignDecisionId.generate(), change.id(), "Explicit state", "Persist state transitions", provenance);
        ImplementationTask task = new ImplementationTask(
                TaskId.generate(), change.id(), Optional.of("TASK-1"), "Implement state", false, provenance);
        AcceptanceCriterion acceptance = new AcceptanceCriterion(
                AcceptanceCriterionId.generate(), Optional.of(requirement.id()), Optional.of(change.id()),
                "Payment verified", "payment completes", VerificationStatus.VERIFIED, List.of(evidence.id()), provenance);

        assertRow(QueryEntityType.REQUIREMENT, mapper.requirement(projectId, requirement));
        assertRow(QueryEntityType.SPECIFICATION, mapper.specification(projectId, specification));
        assertRow(QueryEntityType.SCENARIO, mapper.scenario(projectId, scenario));
        assertRow(QueryEntityType.CHANGE, mapper.change(projectId, change));
        assertRow(QueryEntityType.CONSTRAINT, mapper.constraint(projectId, constraint));
        assertRow(QueryEntityType.DESIGN_DECISION, mapper.decision(projectId, decision));
        assertRow(QueryEntityType.TASK, mapper.task(projectId, task));
        assertRow(QueryEntityType.ACCEPTANCE_CRITERION, mapper.acceptance(projectId, acceptance));
        assertRow(QueryEntityType.EVIDENCE, mapper.evidence(projectId, evidence));

        PortfolioId portfolioId = PortfolioId.generate();
        PortfolioMembership membership = new PortfolioMembership(
                portfolioId,
                projectId,
                "Billing",
                Optional.of(SourceLocator.file("workspace/billing")),
                Optional.empty(),
                Set.of(new ProviderId("openspec"), new ProviderId("markdown")),
                PortfolioMembershipStatus.ACTIVE,
                NOW,
                NOW);
        QueryRow membershipRow = mapper.membership(membership);
        assertRow(QueryEntityType.PORTFOLIO_MEMBERSHIP, membershipRow);
        assertEquals("Billing", membershipRow.cell("displayName").orElseThrow().first().orElseThrow());
        assertEquals(List.of("markdown", "openspec"), membershipRow.cell("providers").orElseThrow().values());

        ProjectSpecificationId targetProjectId = ProjectSpecificationId.generate();
        CrossProjectReference reference = new CrossProjectReference(
                CrossProjectReferenceId.generate(),
                portfolioId,
                new PortfolioEntityRef(projectId, "requirement", requirement.id()),
                new PortfolioEntityRef(targetProjectId, "requirement", RequirementId.generate()),
                "depends-on",
                new ProviderId("openspec"),
                Optional.of(SourceLocator.file("specs/billing.md")),
                Optional.of(evidence.id()),
                NOW);
        QueryRow referenceRow = mapper.reference(reference);
        assertRow(QueryEntityType.PORTFOLIO_REFERENCE, referenceRow);
        assertEquals("depends-on", referenceRow.cell("relation").orElseThrow().first().orElseThrow());
    }

    private void assertRow(QueryEntityType expectedType, QueryRow row) {
        assertEquals(expectedType, row.entityType());
        assertTrue(row.cells().size() >= 5);
    }
}
