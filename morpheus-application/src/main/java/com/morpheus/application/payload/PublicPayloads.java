package com.morpheus.application.payload;

import com.morpheus.application.foundation.PayloadMap;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.version.EntityVersion;

import java.util.Map;

/**
 * The public shape of each domain type more than one transport shows: its keys, and the value of each.
 *
 * <p>A transport puts its own envelope around a shape (a page, a detail member, the MCP text content, the HTTP
 * {@code data} member) and never redeclares the shape. An optional identifier or key that is absent is written as the
 * empty string, an identifier as its canonical text, an enumerated state as its name.</p>
 */
public final class PublicPayloads {

    private static final String ID = "id";
    private static final String TITLE = "title";
    private static final String KEY = "key";
    private static final String CHANGE_ID = "changeId";
    private static final String REQUIREMENT_ID = "requirementId";

    private PublicPayloads() {
    }

    public static Map<String, Object> specification(Specification item) {
        return PayloadMap.of(
                ID, item.id().toString(),
                "projectId", item.projectId().toString(),
                KEY, item.key(),
                TITLE, item.title(),
                "description", item.description().orElse(""));
    }

    public static Map<String, Object> requirement(Requirement item) {
        return PayloadMap.of(
                ID, item.id().toString(),
                "specificationId", item.specificationId().toString(),
                KEY, item.key().orElse(""),
                TITLE, item.title(),
                "statement", item.statement());
    }

    /** One version of a requirement, with the requirement it carries. */
    public static Map<String, Object> requirementVersion(EntityVersion<Requirement> version) {
        return PayloadMap.of(
                "entityVersionId", version.id().toString(),
                "specificationVersionId", version.specificationVersionId().toString(),
                "temporalState", version.temporalState().name(),
                "requirement", requirement(version.content()));
    }

    public static Map<String, Object> scenario(Scenario item) {
        return PayloadMap.of(
                ID, item.id().toString(),
                REQUIREMENT_ID, item.requirementId().map(Object::toString).orElse(""),
                TITLE, item.title(),
                "preconditions", item.preconditions(),
                "action", item.action(),
                "expectedOutcome", item.expectedOutcome());
    }

    public static Map<String, Object> change(ChangeProposal item) {
        return PayloadMap.of(
                ID, item.id().toString(),
                "projectId", item.projectId().toString(),
                KEY, item.key().orElse(""),
                TITLE, item.title(),
                "intent", item.intent(),
                "scope", item.scope(),
                "outOfScope", item.outOfScope(),
                "risks", item.risks());
    }

    public static Map<String, Object> constraint(Constraint item) {
        return PayloadMap.of(
                ID, item.id().toString(),
                CHANGE_ID, item.changeId().toString(),
                "statement", item.statement());
    }

    public static Map<String, Object> acceptanceCriterion(AcceptanceCriterion item) {
        return PayloadMap.of(
                ID, item.id().toString(),
                REQUIREMENT_ID, item.requirementId().map(Object::toString).orElse(""),
                CHANGE_ID, item.changeId().map(Object::toString).orElse(""),
                TITLE, item.title(),
                "condition", item.condition(),
                "verificationStatus", item.verificationStatus().name(),
                "verificationEvidenceIds", item.verificationEvidenceIds().stream().map(Object::toString).toList(),
                "sourceEvidenceId", item.provenance().evidenceId().toString());
    }

    public static Map<String, Object> decision(DesignDecision item) {
        return PayloadMap.of(
                ID, item.id().toString(),
                CHANGE_ID, item.changeId().toString(),
                TITLE, item.title(),
                "decision", item.decision());
    }

    public static Map<String, Object> task(ImplementationTask item) {
        return PayloadMap.of(
                ID, item.id().toString(),
                CHANGE_ID, item.changeId().toString(),
                KEY, item.key().orElse(""),
                TITLE, item.title(),
                "completed", item.completed());
    }
}
