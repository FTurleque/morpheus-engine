package com.morpheus.mcp;

import com.morpheus.application.foundation.PayloadMap;
import com.morpheus.application.quality.ChangeCompletenessAssessment;
import com.morpheus.application.quality.QualityFinding;
import com.morpheus.application.query.SnapshotPage;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.task.ImplementationTask;

import java.util.List;

/**
 * The public shape of each domain type the catalogue tools answer with, written once for all of them.
 *
 * <p>The HTTP adapter writes the same keys and values for the same types in its own services.</p>
 */
final class McpContentPayloads {

    private McpContentPayloads() {
    }

    static Object page(SnapshotPage<?> page, List<?> items) {
        return PagedEnvelope.snapshotPage(page, items);
    }

    static Object specification(Specification item) {
        return PayloadMap.of(
                "id", item.id().toString(),
                "projectId", item.projectId().toString(),
                "key", item.key(),
                "title", item.title(),
                "description", item.description().orElse(""));
    }

    static Object requirement(Requirement item) {
        return PayloadMap.of(
                "id", item.id().toString(),
                "specificationId", item.specificationId().toString(),
                "key", item.key().orElse(""),
                "title", item.title(),
                "statement", item.statement());
    }

    static Object change(ChangeProposal item) {
        return PayloadMap.of(
                "id", item.id().toString(),
                "projectId", item.projectId().toString(),
                "key", item.key().orElse(""),
                "title", item.title(),
                "intent", item.intent(),
                "scope", item.scope(),
                "outOfScope", item.outOfScope(),
                "risks", item.risks());
    }

    static Object constraint(Constraint item) {
        return PayloadMap.of("id", item.id().toString(), "changeId", item.changeId().toString(), "statement", item.statement());
    }

    static Object acceptanceCriterion(AcceptanceCriterion item) {
        return PayloadMap.of(
                "id", item.id().toString(),
                "requirementId", item.requirementId().map(Object::toString).orElse(""),
                "changeId", item.changeId().map(Object::toString).orElse(""),
                "title", item.title(),
                "condition", item.condition(),
                "verificationStatus", item.verificationStatus().name(),
                "verificationEvidenceIds", item.verificationEvidenceIds().stream().map(Object::toString).toList(),
                "sourceEvidenceId", item.provenance().evidenceId().toString());
    }

    static Object decision(DesignDecision item) {
        return PayloadMap.of(
                "id", item.id().toString(), "changeId", item.changeId().toString(),
                "title", item.title(), "decision", item.decision());
    }

    static Object task(ImplementationTask item) {
        return PayloadMap.of(
                "id", item.id().toString(), "changeId", item.changeId().toString(),
                "key", item.key().orElse(""), "title", item.title(), "completed", item.completed());
    }

    static Object scenario(Scenario item) {
        return PayloadMap.of(
                "id", item.id().toString(),
                "requirementId", item.requirementId().map(Object::toString).orElse(""),
                "title", item.title(),
                "preconditions", item.preconditions(),
                "action", item.action(),
                "expectedOutcome", item.expectedOutcome());
    }

    static Object lifecycleFacts(ChangeCompletenessAssessment assessment) {
        var facts = assessment.lifecycleFacts();
        return PayloadMap.of(
                "requirementsIdentified", facts.requirementsIdentified().name(),
                "criticalConstraintsKnown", facts.criticalConstraintsKnown().name(),
                "acceptanceCriteriaDefined", facts.acceptanceCriteriaDefined().name(),
                "designRequired", facts.designRequired().name(),
                "designDecisionsAvailable", facts.designDecisionsAvailable().name(),
                "planPresent", facts.planPresent().name(),
                "knownBlocker", facts.knownBlocker().name(),
                "blockingAcceptanceCriterionFailed", facts.blockingAcceptanceCriterionFailed().name(),
                "blockingAcceptanceCriterionUnverified", facts.blockingAcceptanceCriterionUnverified().name());
    }

    static Object finding(QualityFinding finding) {
        return PayloadMap.of(
                "code", finding.code().name(),
                "severity", finding.severity().name(),
                "evidenceKind", finding.evidenceKind().name(),
                "subjectKind", finding.subject().kind().name(),
                "subjectId", finding.subject().identity().toString(),
                "message", finding.message(),
                "details", finding.details(),
                "confidence", finding.confidence().map(Object::toString).orElse(""),
                "evidenceIds", finding.evidenceIds().stream().map(Object::toString).toList());
    }
}
