package com.morpheus.architecture;

import com.morpheus.application.analysis.ChangeAnalysisResult;
import com.morpheus.application.analysis.ChangeAnalysisService;
import com.morpheus.application.analysis.ChangeAnalysisWarningCode;
import com.morpheus.application.analysis.DependencyImpactDirection;
import com.morpheus.application.analysis.ProposedChangeSet;
import com.morpheus.application.analysis.RequirementChangeField;
import com.morpheus.application.analysis.compact.CompactChangeAnalysisViewService;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.decision.DesignDecisionId;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementDelta;
import com.morpheus.domain.requirement.RequirementDeltaId;
import com.morpheus.domain.requirement.RequirementDeltaKind;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.scenario.ScenarioId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.task.TaskId;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityLinkOrigin;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.domain.traceability.TraceabilityResolutionState;
import com.morpheus.domain.version.EntityVersion;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import com.morpheus.store.memory.MemorySnapshotBusinessContentStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemoryTraceabilityStore;
import com.morpheus.store.sqlite.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteTraceabilityStore;
import com.morpheus.store.sqlite.SqliteVersionedRequirementStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeAnalysisContractTest {
    private static final Instant T0 = Instant.parse("2026-07-24T08:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void comparesCurrentAndProposedWithoutMutatingPublishedBaseline() {
        Fixture fixture = Fixture.create();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(snapshots, snapshots);
        var traceability = new MemoryTraceabilityStore(snapshots);
        populate(snapshots, snapshots, content, traceability, fixture, true);

        ChangeAnalysisResult result = service(snapshots, content, snapshots, traceability)
                .analyzeActive(fixture.proposal(), 2)
                .orElseThrow();

        assertEquals(fixture.snapshotId(), result.baselineSnapshot().id());
        assertEquals(fixture.change(), result.change());
        assertEquals(3, result.requirementImpacts().size());

        var modified = impact(result, fixture.modifiedRequirementId());
        assertEquals(RequirementDeltaKind.MODIFIED, modified.delta().kind());
        assertEquals(
                Set.of(RequirementChangeField.TITLE, RequirementChangeField.STATEMENT, RequirementChangeField.SCENARIOS),
                modified.changedFields());
        assertEquals("Current statement A", modified.currentRequirement().orElseThrow().entityVersion().content().statement());
        assertEquals("Proposed statement A", modified.delta().statement().orElseThrow());
        assertEquals(1, modified.currentScenarios().size());
        assertEquals(1, modified.proposedScenarios().size());

        var added = impact(result, fixture.addedRequirementId());
        assertEquals(Set.of(RequirementChangeField.PRESENCE), added.changedFields());
        assertTrue(added.currentRequirement().isEmpty());
        assertTrue(added.warnings().stream().anyMatch(warning ->
                warning.code() == ChangeAnalysisWarningCode.PROPOSED_ONLY_REQUIREMENT_TRACEABILITY_UNAVAILABLE));

        var removed = impact(result, fixture.removedRequirementId());
        assertEquals(Set.of(RequirementChangeField.PRESENCE), removed.changedFields());
        assertTrue(removed.currentRequirement().isPresent());
        assertTrue(removed.proposedScenarios().isEmpty());

        assertEquals(
                "Current statement A",
                snapshots.currentRequirement(fixture.snapshotId(), fixture.modifiedRequirementId().value())
                        .orElseThrow().entityVersion().content().statement());
        assertEquals("UNAVAILABLE_IN_NORMALIZED_MODEL", result.acceptanceCoverageStatus().name());
        assertTrue(result.warnings().stream().anyMatch(warning ->
                warning.code() == ChangeAnalysisWarningCode.ACCEPTANCE_CRITERIA_UNAVAILABLE));
    }

    @Test
    void expandsOnlyExplicitDependsOnAndKeepsShortestBoundedExplanationPaths() {
        Fixture fixture = Fixture.create();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(snapshots, snapshots);
        var traceability = new MemoryTraceabilityStore(snapshots);
        populate(snapshots, snapshots, content, traceability, fixture, true);
        ChangeAnalysisService service = service(snapshots, content, snapshots, traceability);

        ChangeAnalysisResult depthTwo = service.analyzeActive(fixture.proposal(), 2).orElseThrow();
        assertTrue(depthTwo.dependencyImpacts().stream().anyMatch(impact ->
                impact.originRequirementId().equals(fixture.modifiedRequirementId())
                        && impact.direction() == DependencyImpactDirection.DEPENDENCY
                        && impact.impactedEntity().identity().equals(fixture.dependencyRequirementId().value())
                        && impact.depth() == 1));
        assertTrue(depthTwo.dependencyImpacts().stream().anyMatch(impact ->
                impact.originRequirementId().equals(fixture.modifiedRequirementId())
                        && impact.direction() == DependencyImpactDirection.DEPENDENCY
                        && impact.impactedEntity().identity().equals(fixture.transitiveDependencyRequirementId().value())
                        && impact.depth() == 2));
        assertTrue(depthTwo.dependencyImpacts().stream().anyMatch(impact ->
                impact.originRequirementId().equals(fixture.modifiedRequirementId())
                        && impact.direction() == DependencyImpactDirection.DEPENDENT
                        && impact.impactedEntity().identity().equals(fixture.dependentRequirementId().value())
                        && impact.depth() == 1));
        assertTrue(depthTwo.dependencyImpacts().stream().flatMap(impact -> impact.path().steps().stream())
                .allMatch(step -> step.link().relationType() == TraceabilityRelationType.DEPENDS_ON));
        assertTrue(depthTwo.warnings().stream().anyMatch(warning ->
                warning.code() == ChangeAnalysisWarningCode.TRACEABILITY_PATH_PARTIALLY_RESOLVED));

        ChangeAnalysisResult depthOne = service.analyzeActive(fixture.proposal(), 1).orElseThrow();
        assertFalse(depthOne.dependencyImpacts().stream().anyMatch(impact ->
                impact.impactedEntity().identity().equals(fixture.transitiveDependencyRequirementId().value())));
        assertThrows(IllegalArgumentException.class, () -> service.analyzeActive(fixture.proposal(), 0));
    }

    @Test
    void summaryCountsFunctionalDocumentaryAndDependencyScopeDeterministically() {
        Fixture fixture = Fixture.create();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(snapshots, snapshots);
        var traceability = new MemoryTraceabilityStore(snapshots);
        populate(snapshots, snapshots, content, traceability, fixture, true);

        ChangeAnalysisResult result = service(snapshots, content, snapshots, traceability)
                .analyzeActive(fixture.proposal(), 2)
                .orElseThrow();

        assertEquals(1, result.summary().addedRequirements());
        assertEquals(1, result.summary().modifiedRequirements());
        assertEquals(1, result.summary().removedRequirements());
        assertEquals(3, result.summary().affectedRequirements());
        assertEquals(3, result.summary().changedDocumentaryFields());
        assertEquals(1, result.summary().currentScenarios());
        assertEquals(2, result.summary().proposedScenarios());
        assertEquals(1, result.summary().constraints());
        assertEquals(1, result.summary().designDecisions());
        assertEquals(1, result.summary().implementationTasks());
        assertEquals(2, result.summary().dependencies());
        assertEquals(1, result.summary().dependents());
        assertEquals(3, result.summary().warnings());
    }

    @Test
    void inconsistentDeltaSemanticsRemainExplicitInsteadOfBeingInventedAway() {
        Fixture fixture = Fixture.create();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(snapshots, snapshots);
        var traceability = new MemoryTraceabilityStore(snapshots);
        populate(snapshots, snapshots, content, traceability, fixture, true);

        RequirementId missingModified = RequirementId.generate();
        RequirementId missingRemoved = RequirementId.generate();
        ProposedChangeSet inconsistent = new ProposedChangeSet(
                fixture.change(),
                List.of(
                        delta(fixture.changeId(), RequirementDeltaKind.ADDED, fixture.modifiedRequirementId(),
                                Optional.of("REQ-A"), "Already current", Optional.of("Still current"), List.of(), fixture.provenance()),
                        delta(fixture.changeId(), RequirementDeltaKind.MODIFIED, missingModified,
                                Optional.of("REQ-X"), "Missing modified", Optional.of("Proposed X"), List.of(), fixture.provenance()),
                        delta(fixture.changeId(), RequirementDeltaKind.REMOVED, missingRemoved,
                                Optional.of("REQ-Y"), "Missing removed", Optional.empty(), List.of(), fixture.provenance())),
                List.of(),
                List.of(),
                List.of());

        ChangeAnalysisResult result = service(snapshots, content, snapshots, traceability)
                .analyzeActive(inconsistent, 1)
                .orElseThrow();
        Set<ChangeAnalysisWarningCode> codes = result.warnings().stream()
                .map(warning -> warning.code())
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(codes.contains(ChangeAnalysisWarningCode.ADDED_REQUIREMENT_ALREADY_CURRENT));
        assertTrue(codes.contains(ChangeAnalysisWarningCode.MODIFIED_REQUIREMENT_BASELINE_MISSING));
        assertTrue(codes.contains(ChangeAnalysisWarningCode.REMOVED_REQUIREMENT_BASELINE_MISSING));
    }

    @Test
    void activeAndRetiredAreAllowedWhileReadyAndCrossProjectAreRejected() {
        Fixture fixture = Fixture.create();
        var snapshots = new MemorySpecificationKnowledgeStore();
        var content = new MemorySnapshotBusinessContentStore(snapshots, snapshots);
        var traceability = new MemoryTraceabilityStore(snapshots);
        populate(snapshots, snapshots, content, traceability, fixture, false);
        ChangeAnalysisService service = service(snapshots, content, snapshots, traceability);

        assertTrue(service.analyzeActive(fixture.proposal(), 1).isEmpty());
        assertThrows(KnowledgeStoreException.class, () ->
                service.analyzeSnapshot(fixture.snapshotId(), fixture.proposal(), 1));

        snapshots.activateSnapshot(fixture.snapshotId(), Optional.empty());
        assertEquals(KnowledgeSnapshotState.ACTIVE,
                service.analyzeSnapshot(fixture.snapshotId(), fixture.proposal(), 1).baselineSnapshot().state());

        KnowledgeSnapshotId successorId = KnowledgeSnapshotId.generate();
        SpecificationVersionId successorVersionId = SpecificationVersionId.generate();
        snapshots.putSnapshot(readySnapshot(successorId, fixture.projectId(), Optional.of(fixture.snapshotId()), "revision-2", T0.plusSeconds(60)));
        snapshots.putSpecificationVersion(new SpecificationVersion(
                successorVersionId,
                fixture.projectId(),
                Optional.of(2L),
                Optional.of("provider-v1"),
                Optional.of("revision-2"),
                T0.plusSeconds(60),
                Optional.of(fixture.versionId())));
        snapshots.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(successorId, successorVersionId));
        content.putSnapshotContent(new SnapshotBusinessContent(
                successorId,
                successorVersionId,
                fixture.baselineContent().specifications(),
                fixture.baselineContent().scenarios(),
                List.of(), List.of(), List.of(), List.of(), fixture.baselineContent().evidence()));
        putCurrentRequirement(snapshots, successorId, successorVersionId, EntityVersionId.generate(),
                fixture.modifiedRequirementId(), fixture.specificationId(), "REQ-A", "Current A", "Current statement A", fixture.provenance());
        snapshots.activateSnapshot(successorId, Optional.of(fixture.snapshotId()));

        assertEquals(KnowledgeSnapshotState.RETIRED,
                service.analyzeSnapshot(fixture.snapshotId(), fixture.proposal(), 1).baselineSnapshot().state());

        ChangeProposal otherProjectChange = new ChangeProposal(
                ChangeId.generate(), ProjectSpecificationId.generate(), Optional.empty(), "Other", "Other intent",
                List.of(), List.of(), List.of(), fixture.provenance());
        ProposedChangeSet otherProject = new ProposedChangeSet(otherProjectChange, List.of(), List.of(), List.of(), List.of());
        assertThrows(KnowledgeStoreException.class, () -> service.analyzeSnapshot(fixture.snapshotId(), otherProject, 1));
    }

    @Test
    void memoryAndSqliteProduceExactlySameAnalysis() {
        Fixture fixture = Fixture.create();
        var memorySnapshots = new MemorySpecificationKnowledgeStore();
        var memoryContent = new MemorySnapshotBusinessContentStore(memorySnapshots, memorySnapshots);
        var memoryTraceability = new MemoryTraceabilityStore(memorySnapshots);
        populate(memorySnapshots, memorySnapshots, memoryContent, memoryTraceability, fixture, true);
        ChangeAnalysisResult memory = service(memorySnapshots, memoryContent, memorySnapshots, memoryTraceability)
                .analyzeActive(fixture.proposal(), 2).orElseThrow();

        Path database = tempDir.resolve("m8-analysis-equivalence.db");
        ChangeAnalysisResult sqlite;
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            populate(snapshots, requirements, content, traceability, fixture, true);
            sqlite = service(snapshots, content, requirements, traceability)
                    .analyzeActive(fixture.proposal(), 2).orElseThrow();
        }

        assertEquals(memory, sqlite);
    }

    @Test
    void sqliteReopenAndCompactJsonRemainByteDeterministic() {
        Fixture fixture = Fixture.create();
        Path database = tempDir.resolve("m8-analysis-reopen.db");
        ChangeAnalysisResult before;
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            populate(snapshots, requirements, content, traceability, fixture, true);
            before = service(snapshots, content, requirements, traceability)
                    .analyzeActive(fixture.proposal(), 2).orElseThrow();
        }

        ChangeAnalysisResult after;
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var requirements = new SqliteVersionedRequirementStore(database);
             var content = new SqliteSnapshotBusinessContentStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            after = service(snapshots, content, requirements, traceability)
                    .analyzeActive(fixture.proposal(), 2).orElseThrow();
        }

        assertEquals(before, after);
        CompactChangeAnalysisViewService compact = new CompactChangeAnalysisViewService();
        assertEquals(compact.toView(before), compact.toView(after));
        assertEquals(compact.toJson(before), compact.toJson(after));
        assertArrayEquals(compact.toUtf8(before), compact.toUtf8(after));
        assertTrue(compact.toJson(before).contains("\"operation\":\"analyze_change\""));
        assertTrue(compact.toJson(before).contains("\"acceptanceCoverageStatus\":\"UNAVAILABLE_IN_NORMALIZED_MODEL\""));
    }

    private RequirementChangeImpactView impact(ChangeAnalysisResult result, RequirementId requirementId) {
        var impact = result.requirementImpacts().stream()
                .filter(candidate -> candidate.delta().requirementId().equals(requirementId))
                .findFirst()
                .orElseThrow();
        return new RequirementChangeImpactView(impact);
    }

    private ChangeAnalysisService service(
            SpecificationKnowledgeStore snapshots,
            SnapshotBusinessContentStore content,
            VersionedRequirementStore requirements,
            TraceabilityStore traceability) {
        return new ChangeAnalysisService(snapshots, content, requirements, traceability);
    }

    private void populate(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore requirements,
            SnapshotBusinessContentStore content,
            TraceabilityStore traceability,
            Fixture fixture,
            boolean activate) {
        snapshots.putProject(new ProjectStoreEntry(fixture.projectId(), SourceLocator.file("workspace-m8")));
        snapshots.putSnapshot(readySnapshot(
                fixture.snapshotId(), fixture.projectId(), Optional.empty(), "revision-1", T0));
        requirements.putSpecificationVersion(new SpecificationVersion(
                fixture.versionId(), fixture.projectId(), Optional.of(1L), Optional.of("provider-v1"),
                Optional.of("revision-1"), T0, Optional.empty()));
        requirements.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(fixture.snapshotId(), fixture.versionId()));
        content.putSnapshotContent(fixture.baselineContent());

        putCurrentRequirement(requirements, fixture.snapshotId(), fixture.versionId(), fixture.modifiedVersionId(),
                fixture.modifiedRequirementId(), fixture.specificationId(), "REQ-A", "Current A", "Current statement A", fixture.provenance());
        putCurrentRequirement(requirements, fixture.snapshotId(), fixture.versionId(), fixture.removedVersionId(),
                fixture.removedRequirementId(), fixture.specificationId(), "REQ-B", "Current B", "Current statement B", fixture.provenance());
        putCurrentRequirement(requirements, fixture.snapshotId(), fixture.versionId(), fixture.dependencyVersionId(),
                fixture.dependencyRequirementId(), fixture.specificationId(), "REQ-C", "Dependency C", "Dependency C statement", fixture.provenance());
        putCurrentRequirement(requirements, fixture.snapshotId(), fixture.versionId(), fixture.dependentVersionId(),
                fixture.dependentRequirementId(), fixture.specificationId(), "REQ-D", "Dependent D", "Dependent D statement", fixture.provenance());
        putCurrentRequirement(requirements, fixture.snapshotId(), fixture.versionId(), fixture.transitiveDependencyVersionId(),
                fixture.transitiveDependencyRequirementId(), fixture.specificationId(), "REQ-F", "Dependency F", "Dependency F statement", fixture.provenance());

        traceability.putLink(fixture.snapshotId(), link(
                fixture.modifiedToDependencyLinkId(), requirementRef(fixture.modifiedRequirementId()),
                requirementRef(fixture.dependencyRequirementId()), TraceabilityResolutionState.RESOLVED, fixture.evidenceId(), T0.plusSeconds(1)));
        traceability.putLink(fixture.snapshotId(), link(
                fixture.dependencyToTransitiveLinkId(), requirementRef(fixture.dependencyRequirementId()),
                requirementRef(fixture.transitiveDependencyRequirementId()), TraceabilityResolutionState.UNRESOLVED, fixture.evidenceId(), T0.plusSeconds(2)));
        traceability.putLink(fixture.snapshotId(), link(
                fixture.dependentToModifiedLinkId(), requirementRef(fixture.dependentRequirementId()),
                requirementRef(fixture.modifiedRequirementId()), TraceabilityResolutionState.RESOLVED, fixture.evidenceId(), T0.plusSeconds(3)));

        if (activate) {
            snapshots.activateSnapshot(fixture.snapshotId(), Optional.empty());
        }
    }

    private void putCurrentRequirement(
            VersionedRequirementStore requirements,
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            EntityVersionId entityVersionId,
            RequirementId requirementId,
            SpecificationId specificationId,
            String key,
            String title,
            String statement,
            Provenance provenance) {
        Requirement requirement = new Requirement(
                requirementId, specificationId, Optional.of(key), title, statement, provenance);
        requirements.putRequirementVersion(new RequirementVersionRecord(
                snapshotId,
                new EntityVersion<>(entityVersionId, requirementId.value(), versionId, TemporalState.CURRENT, requirement)));
    }

    private TraceabilityLink link(
            TraceabilityLinkId id,
            TraceabilityEntityRef source,
            TraceabilityEntityRef target,
            TraceabilityResolutionState resolution,
            EvidenceId evidenceId,
            Instant observedAt) {
        return new TraceabilityLink(
                id, source, TraceabilityRelationType.DEPENDS_ON, target,
                TraceabilityLinkOrigin.EXPLICIT, resolution, Optional.empty(), Set.of(evidenceId), observedAt);
    }

    private TraceabilityEntityRef requirementRef(RequirementId requirementId) {
        return new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, requirementId.value());
    }

    private KnowledgeSnapshotMetadata readySnapshot(
            KnowledgeSnapshotId id,
            ProjectSpecificationId projectId,
            Optional<KnowledgeSnapshotId> predecessor,
            String revision,
            Instant createdAt) {
        return new KnowledgeSnapshotMetadata(
                id, projectId, predecessor, KnowledgeSnapshotState.READY, Optional.of(revision), createdAt);
    }

    private static RequirementDelta delta(
            ChangeId changeId,
            RequirementDeltaKind kind,
            RequirementId requirementId,
            Optional<String> key,
            String title,
            Optional<String> statement,
            List<Scenario> scenarios,
            Provenance provenance) {
        return new RequirementDelta(
                RequirementDeltaId.generate(), changeId, kind, "core", requirementId,
                key, title, statement, scenarios, provenance);
    }

    private record RequirementChangeImpactView(com.morpheus.application.analysis.RequirementChangeImpact delegate) {
        private RequirementDelta delta() { return delegate.delta(); }
        private Optional<RequirementVersionRecord> currentRequirement() { return delegate.currentRequirement(); }
        private List<Scenario> currentScenarios() { return delegate.currentScenarios(); }
        private List<Scenario> proposedScenarios() { return delegate.proposedScenarios(); }
        private Set<RequirementChangeField> changedFields() { return delegate.changedFields(); }
        private List<com.morpheus.application.analysis.ChangeAnalysisWarning> warnings() { return delegate.warnings(); }
    }

    private record Fixture(
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            SpecificationId specificationId,
            ChangeId changeId,
            RequirementId modifiedRequirementId,
            RequirementId removedRequirementId,
            RequirementId dependencyRequirementId,
            RequirementId dependentRequirementId,
            RequirementId transitiveDependencyRequirementId,
            RequirementId addedRequirementId,
            EntityVersionId modifiedVersionId,
            EntityVersionId removedVersionId,
            EntityVersionId dependencyVersionId,
            EntityVersionId dependentVersionId,
            EntityVersionId transitiveDependencyVersionId,
            EvidenceId evidenceId,
            Provenance provenance,
            ChangeProposal change,
            ProposedChangeSet proposal,
            SnapshotBusinessContent baselineContent,
            TraceabilityLinkId modifiedToDependencyLinkId,
            TraceabilityLinkId dependencyToTransitiveLinkId,
            TraceabilityLinkId dependentToModifiedLinkId) {

        private static Fixture create() {
            ProjectSpecificationId projectId = ProjectSpecificationId.generate();
            KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
            SpecificationVersionId versionId = SpecificationVersionId.generate();
            SpecificationId specificationId = SpecificationId.generate();
            ChangeId changeId = ChangeId.generate();
            RequirementId modifiedRequirementId = RequirementId.generate();
            RequirementId removedRequirementId = RequirementId.generate();
            RequirementId dependencyRequirementId = RequirementId.generate();
            RequirementId dependentRequirementId = RequirementId.generate();
            RequirementId transitiveDependencyRequirementId = RequirementId.generate();
            RequirementId addedRequirementId = RequirementId.generate();
            EntityVersionId modifiedVersionId = EntityVersionId.generate();
            EntityVersionId removedVersionId = EntityVersionId.generate();
            EntityVersionId dependencyVersionId = EntityVersionId.generate();
            EntityVersionId dependentVersionId = EntityVersionId.generate();
            EntityVersionId transitiveDependencyVersionId = EntityVersionId.generate();
            EvidenceId evidenceId = EvidenceId.generate();
            Provenance provenance = new Provenance(
                    new ProviderId("m8-test"), Optional.of("1"), SourceLocator.file("specs/core.md"),
                    Optional.of("M8"), Optional.of("revision-1"), evidenceId);
            Evidence evidence = new Evidence(
                    evidenceId, SourceLocator.file("specs/core.md"), Optional.empty(), Optional.of("sha256:m8"));
            Specification specification = new Specification(
                    specificationId, projectId, "core", "Core", Optional.of("M8 baseline"), provenance);
            Scenario currentScenario = new Scenario(
                    ScenarioId.generate(), Optional.of(modifiedRequirementId), "Current scenario",
                    List.of("baseline"), "act", "current outcome", provenance);
            SnapshotBusinessContent baselineContent = new SnapshotBusinessContent(
                    snapshotId, versionId, List.of(specification), List.of(currentScenario),
                    List.of(), List.of(), List.of(), List.of(), List.of(evidence));

            ChangeProposal change = new ChangeProposal(
                    changeId, projectId, Optional.of("CHG-M8"), "Analyze change", "Explain documentary impact",
                    List.of("requirements", "dependencies"), List.of("code"), List.of("missing traceability"), provenance);
            Scenario proposedModifiedScenario = new Scenario(
                    ScenarioId.generate(), Optional.of(modifiedRequirementId), "Current scenario",
                    List.of("baseline"), "act", "proposed outcome", provenance);
            Scenario proposedAddedScenario = new Scenario(
                    ScenarioId.generate(), Optional.of(addedRequirementId), "Added scenario",
                    List.of("proposal"), "act", "new outcome", provenance);
            RequirementDelta modified = delta(
                    changeId, RequirementDeltaKind.MODIFIED, modifiedRequirementId,
                    Optional.of("REQ-A"), "Proposed A", Optional.of("Proposed statement A"),
                    List.of(proposedModifiedScenario), provenance);
            RequirementDelta removed = delta(
                    changeId, RequirementDeltaKind.REMOVED, removedRequirementId,
                    Optional.of("REQ-B"), "Current B", Optional.empty(), List.of(), provenance);
            RequirementDelta added = delta(
                    changeId, RequirementDeltaKind.ADDED, addedRequirementId,
                    Optional.of("REQ-E"), "Added E", Optional.of("Added statement E"),
                    List.of(proposedAddedScenario), provenance);
            Constraint constraint = new Constraint(
                    ConstraintId.generate(), changeId, "Must preserve deterministic analysis", provenance);
            DesignDecision decision = new DesignDecision(
                    DesignDecisionId.generate(), changeId, "Use explicit dependency paths", "Traverse DEPENDS_ON only", provenance);
            ImplementationTask task = new ImplementationTask(
                    TaskId.generate(), changeId, Optional.of("TASK-M8"), "Implement change analysis", false, provenance);
            ProposedChangeSet proposal = new ProposedChangeSet(
                    change, List.of(added, modified, removed), List.of(constraint), List.of(decision), List.of(task));

            return new Fixture(
                    projectId, snapshotId, versionId, specificationId, changeId,
                    modifiedRequirementId, removedRequirementId, dependencyRequirementId,
                    dependentRequirementId, transitiveDependencyRequirementId, addedRequirementId,
                    modifiedVersionId, removedVersionId, dependencyVersionId, dependentVersionId,
                    transitiveDependencyVersionId, evidenceId, provenance, change, proposal, baselineContent,
                    TraceabilityLinkId.generate(), TraceabilityLinkId.generate(), TraceabilityLinkId.generate());
        }
    }
}
