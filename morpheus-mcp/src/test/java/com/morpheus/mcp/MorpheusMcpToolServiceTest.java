package com.morpheus.mcp;

import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.decision.DesignDecisionId;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
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
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.task.TaskId;
import com.morpheus.store.sqlite.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteTraceabilityStore;
import com.morpheus.store.sqlite.SqliteVersionedRequirementStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusMcpToolServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void executesEntireReadOnlyM10CatalogWithoutInventingUnavailableSemantics() {
        Path database = tempDirectory.resolve("morpheus.db");
        Fixture fixture = publish(database);
        MorpheusMcpToolService service = new MorpheusMcpToolService(database);
        String projectId = fixture.project.id().toString();
        String changeId = fixture.change.id().toString();

        assertContains(service.execute("get_current_specification", Map.of("projectId", projectId)), fixture.specification.title());
        assertContains(service.execute("find_requirements", Map.of("projectId", projectId, "query", "session")), fixture.requirement.title());
        assertContains(service.execute("get_change", Map.of("projectId", projectId, "changeId", changeId)), fixture.change.title());
        assertContains(service.execute("list_changes", Map.of("projectId", projectId)), fixture.change.title());
        assertContains(service.execute("get_constraints", Map.of("projectId", projectId, "changeId", changeId)), fixture.constraint.statement());

        String acceptance = service.execute("get_acceptance_criteria", Map.of("projectId", projectId, "changeId", changeId));
        assertContains(acceptance, "UNAVAILABLE_IN_NORMALIZED_MODEL");
        assertContains(acceptance, "\"criteria\":[]");
        assertFalse(acceptance.contains(fixture.scenario.expectedOutcome()), "scenario must never be relabeled as acceptance criterion");

        assertContains(service.execute("get_design_decisions", Map.of("projectId", projectId, "changeId", changeId)), fixture.decision.title());
        assertContains(service.execute("get_implementation_tasks", Map.of("projectId", projectId, "changeId", changeId)), fixture.task.title());
        assertContains(service.execute("trace_requirement", Map.of(
                "projectId", projectId,
                "requirementId", fixture.requirement.id().toString(),
                "depth", 2)), fixture.requirement.title());
        assertContains(service.execute("get_change_context", Map.of(
                "projectId", projectId,
                "changeId", changeId,
                "depth", 2)), fixture.change.title());
        assertContains(service.execute("get_specification_context", Map.of(
                "projectId", projectId,
                "specificationId", fixture.specification.id().toString())), fixture.scenario.title());

        String status = service.execute("get_change_status", Map.of("projectId", projectId, "changeId", changeId));
        assertContains(status, "UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT");
        assertContains(status, "\"lifecycleState\":\"UNAVAILABLE\"");

        String blockers = service.execute("get_blocking_conditions", Map.of("projectId", projectId, "changeId", changeId));
        assertContains(blockers, "observableFacts");
        assertContains(blockers, "unavailableFacts");

        String sync = service.execute("get_sync_status", Map.of("projectId", projectId));
        assertContains(sync, projectId);
        assertContains(sync, "state");
    }

    private Fixture publish(Path database) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SpecificationId specificationId = SpecificationId.generate();
        RequirementId requirementId = RequirementId.generate();
        ChangeId changeId = ChangeId.generate();
        SourceLocator source = SourceLocator.file("openspec/specs/auth/spec.md");
        Evidence evidence = new Evidence(EvidenceId.generate(), source, Optional.empty(), Optional.empty());
        Provenance provenance = new Provenance(
                new ProviderId("m10-test"), Optional.of("1.0"), source,
                Optional.of("fixture"), Optional.empty(), evidence.id());
        ProjectSpecification project = new ProjectSpecification(projectId, "M10 fixture", SourceLocator.file("workspace"));
        Specification specification = new Specification(
                specificationId, projectId, "auth", "Authentication", Optional.of("Authentication behavior"), provenance);
        Requirement requirement = new Requirement(
                requirementId, specificationId, Optional.of("auth/session"),
                "Session expiration", "The system SHALL expire inactive sessions.", provenance);
        Scenario scenario = new Scenario(
                ScenarioId.generate(), Optional.of(requirementId), "Expire idle session",
                List.of("A session is authenticated"), "The inactivity timeout elapses",
                "The session is invalidated", provenance);
        ChangeProposal change = new ChangeProposal(
                changeId, projectId, Optional.of("tighten-session"), "Tighten session expiration",
                "Reduce exposure from idle sessions.", List.of("Session timeout"), List.of(), List.of(), provenance);
        Constraint constraint = new Constraint(
                ConstraintId.generate(), changeId, "Timeout configuration must remain explicit.", provenance);
        DesignDecision decision = new DesignDecision(
                DesignDecisionId.generate(), changeId, "Use explicit timeout", "Store timeout as configuration.", provenance);
        ImplementationTask task = new ImplementationTask(
                TaskId.generate(), changeId, Optional.of("TASK-1"), "Implement timeout handling", false, provenance);
        NormalizedProjectContent content = new NormalizedProjectContent(
                project,
                List.of(specification),
                List.of(requirement),
                List.of(scenario),
                List.of(change),
                List.of(constraint),
                List.of(decision),
                List.of(task),
                List.of(evidence),
                List.of());

        try (SqliteSpecificationKnowledgeStore snapshots = new SqliteSpecificationKnowledgeStore(database);
             SqliteVersionedRequirementStore requirements = new SqliteVersionedRequirementStore(database);
             SqliteSnapshotBusinessContentStore businessContent = new SqliteSnapshotBusinessContentStore(database);
             SqliteTraceabilityStore traceability = new SqliteTraceabilityStore(database)) {
            new ProjectSnapshotImportService(snapshots, requirements, businessContent, traceability)
                    .publishFull(content, Optional.of("m10-test"), Instant.parse("2026-07-24T10:00:00Z"));
        }
        return new Fixture(project, specification, requirement, scenario, change, constraint, decision, task);
    }

    private void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "expected <" + expected + "> in: " + actual);
    }

    private record Fixture(
            ProjectSpecification project,
            Specification specification,
            Requirement requirement,
            Scenario scenario,
            ChangeProposal change,
            Constraint constraint,
            DesignDecision decision,
            ImplementationTask task) {
    }
}
