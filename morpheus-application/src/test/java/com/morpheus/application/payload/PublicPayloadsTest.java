package com.morpheus.application.payload;

import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.acceptance.AcceptanceCriterionId;
import com.morpheus.domain.acceptance.VerificationStatus;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.decision.DesignDecisionId;
import com.morpheus.domain.evidence.EvidenceId;
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
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.version.EntityVersion;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersionId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublicPayloadsTest {

    private static final EvidenceId EVIDENCE = EvidenceId.generate();
    private static final Provenance PROVENANCE = new Provenance(new ProviderId("test"), Optional.empty(),
            SourceLocator.file("spec.md"), Optional.empty(), Optional.empty(), EVIDENCE);
    private static final ProjectSpecificationId PROJECT = ProjectSpecificationId.generate();
    private static final SpecificationId SPECIFICATION = SpecificationId.generate();
    private static final RequirementId REQUIREMENT = RequirementId.generate();
    private static final ChangeId CHANGE = ChangeId.generate();

    @Test
    void aSpecificationShowsItsIdentifiersKeyTitleAndDescription() {
        Specification item = new Specification(SPECIFICATION, PROJECT, "auth", "Auth", Optional.empty(), PROVENANCE);

        assertShape(PublicPayloads.specification(item),
                "id", SPECIFICATION.toString(), "projectId", PROJECT.toString(), "key", "auth", "title", "Auth",
                "description", "");
    }

    @Test
    void aRequirementWritesAnAbsentKeyAsTheEmptyString() {
        assertShape(PublicPayloads.requirement(requirement()),
                "id", REQUIREMENT.toString(), "specificationId", SPECIFICATION.toString(), "key", "",
                "title", "Login", "statement", "The user logs in.");
    }

    @Test
    void aRequirementVersionCarriesTheRequirementShape() {
        EntityVersionId versionId = EntityVersionId.generate();
        SpecificationVersionId specificationVersionId = SpecificationVersionId.generate();
        EntityVersion<Requirement> version = new EntityVersion<>(
                versionId, REQUIREMENT.value(), specificationVersionId, TemporalState.CURRENT, requirement());

        assertShape(PublicPayloads.requirementVersion(version),
                "entityVersionId", versionId.toString(), "specificationVersionId", specificationVersionId.toString(),
                "temporalState", "CURRENT", "requirement", PublicPayloads.requirement(requirement()));
    }

    @Test
    void aScenarioShowsItsRequirementAndSteps() {
        ScenarioId id = ScenarioId.generate();
        Scenario item = new Scenario(id, Optional.of(REQUIREMENT), "Valid login", List.of("a user exists"),
                "the user logs in", "a session opens", PROVENANCE);

        assertShape(PublicPayloads.scenario(item),
                "id", id.toString(), "requirementId", REQUIREMENT.toString(), "title", "Valid login",
                "preconditions", List.of("a user exists"), "action", "the user logs in",
                "expectedOutcome", "a session opens");
    }

    @Test
    void aChangeShowsItsProjectAndItsLists() {
        ChangeProposal item = new ChangeProposal(CHANGE, PROJECT, Optional.of("add-remember-me"), "Remember me",
                "Keep a session", List.of("session"), List.of("sso"), List.of("theft"), PROVENANCE);

        assertShape(PublicPayloads.change(item),
                "id", CHANGE.toString(), "projectId", PROJECT.toString(), "key", "add-remember-me",
                "title", "Remember me", "intent", "Keep a session", "scope", List.of("session"),
                "outOfScope", List.of("sso"), "risks", List.of("theft"));
    }

    @Test
    void aConstraintShowsItsChangeAndStatement() {
        ConstraintId id = ConstraintId.generate();
        Constraint item = Constraint.withUnknownSemantics(id, CHANGE, "Tokens expire.", PROVENANCE);

        assertShape(PublicPayloads.constraint(item),
                "id", id.toString(), "changeId", CHANGE.toString(), "statement", "Tokens expire.");
    }

    @Test
    void anAcceptanceCriterionWritesAnAbsentReferenceAsTheEmptyString() {
        AcceptanceCriterionId id = AcceptanceCriterionId.generate();
        AcceptanceCriterion item = new AcceptanceCriterion(id, Optional.empty(), Optional.of(CHANGE), "Expires",
                "after 30 days", VerificationStatus.NOT_VERIFIED, List.of(), PROVENANCE);

        assertShape(PublicPayloads.acceptanceCriterion(item),
                "id", id.toString(), "requirementId", "", "changeId", CHANGE.toString(), "title", "Expires",
                "condition", "after 30 days", "verificationStatus", "NOT_VERIFIED",
                "verificationEvidenceIds", List.of(), "sourceEvidenceId", EVIDENCE.toString());
    }

    @Test
    void aDecisionShowsItsChangeTitleAndDecision() {
        DesignDecisionId id = DesignDecisionId.generate();
        DesignDecision item = new DesignDecision(id, CHANGE, "Storage", "Store a hashed token.", PROVENANCE);

        assertShape(PublicPayloads.decision(item),
                "id", id.toString(), "changeId", CHANGE.toString(), "title", "Storage",
                "decision", "Store a hashed token.");
    }

    @Test
    void aTaskShowsWhetherItIsCompleted() {
        TaskId id = TaskId.generate();
        ImplementationTask item = new ImplementationTask(id, CHANGE, Optional.of("1.1"), "Add the cookie", true,
                PROVENANCE);

        assertShape(PublicPayloads.task(item),
                "id", id.toString(), "changeId", CHANGE.toString(), "key", "1.1", "title", "Add the cookie",
                "completed", true);
    }

    private static Requirement requirement() {
        return new Requirement(REQUIREMENT, SPECIFICATION, Optional.empty(), "Login", "The user logs in.", PROVENANCE);
    }

    private static void assertShape(Map<String, Object> shape, Object... expected) {
        List<String> keys = new java.util.ArrayList<>();
        for (int index = 0; index < expected.length; index += 2) {
            keys.add((String) expected[index]);
            assertEquals(expected[index + 1], shape.get(expected[index]), "value of " + expected[index]);
        }
        assertEquals(keys, List.copyOf(shape.keySet()));
    }
}
