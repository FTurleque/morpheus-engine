package com.morpheus.application.query.dsl;

import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.portfolio.CrossProjectReference;
import com.morpheus.domain.portfolio.PortfolioMembership;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.task.ImplementationTask;

import java.util.List;

/** Maps published domain facts into provider-neutral query rows. */
final class QueryRowMapper {

    QueryRow requirement(ProjectSpecificationId projectId, Requirement item) {
        return row(QueryEntityType.REQUIREMENT, projectId, item.id(), List.of(
                QueryCell.scalar("id", item.id()), QueryCell.scalar("projectId", projectId),
                QueryCell.scalar("specificationId", item.specificationId()), QueryCell.optional("key", item.key()),
                QueryCell.scalar("title", item.title()), QueryCell.scalar("statement", item.statement()),
                QueryCell.scalar("providerId", item.provenance().providerId().value())));
    }

    QueryRow specification(ProjectSpecificationId projectId, Specification item) {
        return row(QueryEntityType.SPECIFICATION, projectId, item.id(), List.of(
                QueryCell.scalar("id", item.id()), QueryCell.scalar("projectId", projectId), QueryCell.scalar("key", item.key()),
                QueryCell.scalar("title", item.title()), QueryCell.optional("description", item.description()),
                QueryCell.scalar("providerId", item.provenance().providerId().value())));
    }

    QueryRow scenario(ProjectSpecificationId projectId, Scenario item) {
        return row(QueryEntityType.SCENARIO, projectId, item.id(), List.of(
                QueryCell.scalar("id", item.id()), QueryCell.scalar("projectId", projectId), QueryCell.optional("requirementId", item.requirementId()),
                QueryCell.scalar("title", item.title()), new QueryCell("preconditions", item.preconditions()), QueryCell.scalar("action", item.action()),
                QueryCell.scalar("expectedOutcome", item.expectedOutcome()), QueryCell.scalar("providerId", item.provenance().providerId().value())));
    }

    QueryRow change(ProjectSpecificationId projectId, ChangeProposal item) {
        return row(QueryEntityType.CHANGE, projectId, item.id(), List.of(
                QueryCell.scalar("id", item.id()), QueryCell.scalar("projectId", projectId), QueryCell.optional("key", item.key()),
                QueryCell.scalar("title", item.title()), QueryCell.scalar("intent", item.intent()), new QueryCell("scope", item.scope()),
                new QueryCell("outOfScope", item.outOfScope()), new QueryCell("risks", item.risks()),
                QueryCell.scalar("providerId", item.provenance().providerId().value())));
    }

    QueryRow constraint(ProjectSpecificationId projectId, Constraint item) {
        return row(QueryEntityType.CONSTRAINT, projectId, item.id(), List.of(
                QueryCell.scalar("id", item.id()), QueryCell.scalar("projectId", projectId), QueryCell.scalar("changeId", item.changeId()),
                QueryCell.scalar("statement", item.statement()), QueryCell.scalar("applicability", item.applicability().name()),
                QueryCell.scalar("severity", item.severity().name()), QueryCell.scalar("satisfaction", item.satisfaction().name()),
                QueryCell.scalar("blockingMode", item.blockingPolicy().mode().name()),
                QueryCell.scalar("providerId", item.provenance().providerId().value())));
    }

    QueryRow decision(ProjectSpecificationId projectId, DesignDecision item) {
        return row(QueryEntityType.DESIGN_DECISION, projectId, item.id(), List.of(
                QueryCell.scalar("id", item.id()), QueryCell.scalar("projectId", projectId), QueryCell.scalar("changeId", item.changeId()),
                QueryCell.scalar("title", item.title()), QueryCell.scalar("decision", item.decision()),
                QueryCell.scalar("providerId", item.provenance().providerId().value())));
    }

    QueryRow task(ProjectSpecificationId projectId, ImplementationTask item) {
        return row(QueryEntityType.TASK, projectId, item.id(), List.of(
                QueryCell.scalar("id", item.id()), QueryCell.scalar("projectId", projectId), QueryCell.scalar("changeId", item.changeId()),
                QueryCell.optional("key", item.key()), QueryCell.scalar("title", item.title()), QueryCell.scalar("completed", item.completed()),
                QueryCell.scalar("providerId", item.provenance().providerId().value())));
    }

    QueryRow acceptance(ProjectSpecificationId projectId, AcceptanceCriterion item) {
        return row(QueryEntityType.ACCEPTANCE_CRITERION, projectId, item.id(), List.of(
                QueryCell.scalar("id", item.id()), QueryCell.scalar("projectId", projectId), QueryCell.optional("requirementId", item.requirementId()),
                QueryCell.optional("changeId", item.changeId()), QueryCell.scalar("title", item.title()), QueryCell.scalar("condition", item.condition()),
                QueryCell.scalar("verificationStatus", item.verificationStatus().name()),
                QueryCell.scalar("providerId", item.provenance().providerId().value())));
    }

    QueryRow evidence(ProjectSpecificationId projectId, Evidence item) {
        return row(QueryEntityType.EVIDENCE, projectId, item.id(), List.of(
                QueryCell.scalar("id", item.id()), QueryCell.scalar("projectId", projectId), QueryCell.scalar("source", item.source()),
                QueryCell.optional("range", item.range()), QueryCell.optional("excerptHash", item.excerptHash())));
    }

    QueryRow membership(PortfolioMembership item) {
        return new QueryRow(QueryEntityType.PORTFOLIO_MEMBERSHIP, item.projectId().toString(), item.projectId().toString(), List.of(
                QueryCell.scalar("portfolioId", item.portfolioId()), QueryCell.scalar("projectId", item.projectId()),
                QueryCell.scalar("displayName", item.displayName()), QueryCell.optional("workspace", item.workspace()),
                QueryCell.optional("repository", item.repository()), new QueryCell("providers", item.providers().stream().map(provider -> provider.value()).sorted().toList()),
                QueryCell.scalar("status", item.status().name())));
    }

    QueryRow reference(CrossProjectReference item) {
        return new QueryRow(QueryEntityType.PORTFOLIO_REFERENCE, item.source().projectId().toString(), item.id().toString(), List.of(
                QueryCell.scalar("id", item.id()), QueryCell.scalar("portfolioId", item.portfolioId()),
                QueryCell.scalar("projectId", item.source().projectId()), QueryCell.scalar("sourceProjectId", item.source().projectId()),
                QueryCell.scalar("sourceType", item.source().entityType()), QueryCell.scalar("sourceId", item.source().entityId()),
                QueryCell.scalar("targetProjectId", item.target().projectId()), QueryCell.scalar("targetType", item.target().entityType()),
                QueryCell.scalar("targetId", item.target().entityId()), QueryCell.scalar("relation", item.relation()),
                QueryCell.scalar("providerId", item.providerId().value()), QueryCell.optional("sourceLocator", item.sourceLocator()),
                QueryCell.optional("evidenceId", item.evidenceId())));
    }

    private QueryRow row(QueryEntityType type, ProjectSpecificationId projectId, Object entityId, List<QueryCell> cells) {
        return new QueryRow(type, projectId.toString(), entityId.toString(), cells);
    }
}
