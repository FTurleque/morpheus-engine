package com.morpheus.architecture;

import com.morpheus.application.query.ChangeContextResult;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.RequirementSearchPage;
import com.morpheus.application.query.RequirementSearchQuery;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.query.compact.CompactChangeContextView;
import com.morpheus.application.query.compact.CompactQueryViewService;
import com.morpheus.application.query.compact.CompactRequirementSearchView;
import com.morpheus.application.query.compact.CompactTraceRequirementView;
import com.morpheus.application.query.compact.CompactWarningCode;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.application.traceability.ExternalTraceabilityAvailability;
import com.morpheus.application.traceability.ExternalTraceabilityView;
import com.morpheus.application.traceability.TraceRequirementResult;
import com.morpheus.application.traceability.TraceabilitySubgraph;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.decision.DesignDecisionId;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.evidence.SourceRange;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.reference.ExternalReferenceResolutionReason;
import com.morpheus.domain.reference.ExternalReferenceResolutionState;
import com.morpheus.domain.reference.ExternalReferenceTarget;
import com.morpheus.domain.reference.ResolvedExternalTarget;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
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
import com.morpheus.store.sqlite.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteVersionedRequirementStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactQueryViewContractTest {
    private static final Instant T0 = Instant.parse("2026-07-23T17:45:00Z");
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    @TempDir
    Path tempDir;

    @Test
    void memoryAndSqliteProduceTheSameCompactRequirementSearchIncludingReopen() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        SpecificationId specificationId = SpecificationId.generate();
        Evidence firstEvidence = evidence("specs/first.md", 2, 4, "sha256:first");
        Evidence secondEvidence = evidence("specs/second.md", 8, 9, "sha256:second");
        RequirementVersionRecord first = requirement(
                snapshotId, versionId, specificationId, RequirementId.generate(), EntityVersionId.generate(), firstEvidence.id(),
                "REQ-1", "First requirement", "first statement");
        RequirementVersionRecord second = requirement(
                snapshotId, versionId, specificationId, RequirementId.generate(), EntityVersionId.generate(), secondEvidence.id(),
                "REQ-2", "Second requirement", "second statement");
        SnapshotBusinessContent content = content(snapshotId, versionId, List.of(firstEvidence, secondEvidence));

        MemorySpecificationKnowledgeStore memoryCore = new MemorySpecificationKnowledgeStore();
        MemorySnapshotBusinessContentStore memoryContent = new MemorySnapshotBusinessContentStore(memoryCore, memoryCore);
        KnowledgeSnapshotMetadata active = seed(memoryCore, memoryCore, memoryContent, projectId, snapshotId, versionId, content);
        RequirementSearchQuery query = new RequirementSearchQuery("  REQUIREMENT  ");
        RequirementSearchPage page = new RequirementSearchPage(
                active,
                query,
                List.of(second, first),
                new PageRequest(0, 10),
                2,
                false);
        CompactRequirementSearchView memoryView = new CompactQueryViewService(memoryContent).requirementSearch(page);

        Path database = tempDir.resolve("compact-query.db");
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var versions = new SqliteVersionedRequirementStore(database);
             var sqliteContent = new SqliteSnapshotBusinessContentStore(database)) {
            KnowledgeSnapshotMetadata sqliteActive = seed(
                    snapshots, versions, sqliteContent, projectId, snapshotId, versionId, content);
            assertEquals(active, sqliteActive);
        }

        CompactRequirementSearchView sqliteView;
        try (var reopened = new SqliteSnapshotBusinessContentStore(database)) {
            sqliteView = new CompactQueryViewService(reopened).requirementSearch(page);
        }

        assertEquals(memoryView, sqliteView);
        assertEquals("find_requirements", memoryView.metadata().operation());
        assertEquals(1, memoryView.metadata().schemaVersion());
        assertEquals("requirement", memoryView.searchText());
        assertEquals(2, memoryView.page().totalMatches());
        assertEquals(2, memoryView.evidence().size());
        assertTrue(memoryView.warnings().isEmpty());
        assertTrue(memoryView.requirements().get(0).id().compareTo(memoryView.requirements().get(1).id()) < 0);
        assertEquals(TemporalState.CURRENT.name(), memoryView.requirements().getFirst().temporalState());
        assertEquals(json.toJson(memoryView), json.toJson(sqliteView));
        assertEquals(json.toJson(memoryView), json.toJson(memoryView));
    }

    @Test
    void missingEvidenceProducesAWarningWithoutHidingTheRequirement() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        KnowledgeSnapshotMetadata snapshot = activeSnapshot(snapshotId, projectId);
        EvidenceId missingEvidence = EvidenceId.generate();
        RequirementVersionRecord record = requirement(
                snapshotId,
                versionId,
                SpecificationId.generate(),
                RequirementId.generate(),
                EntityVersionId.generate(),
                missingEvidence,
                "REQ-MISSING-EVIDENCE",
                "Requirement remains visible",
                "statement");
        SnapshotBusinessContent content = content(snapshotId, versionId, List.of());
        RequirementSearchPage page = new RequirementSearchPage(
                snapshot,
                RequirementSearchQuery.all(),
                List.of(record),
                new PageRequest(0, 10),
                1,
                false);

        CompactRequirementSearchView view = new CompactQueryViewService(new FixedContentStore(content))
                .requirementSearch(page);

        assertEquals(1, view.requirements().size());
        assertTrue(view.evidence().isEmpty());
        assertEquals(List.of(CompactWarningCode.EVIDENCE_NOT_FOUND), warningCodes(view.warnings()));
        assertEquals(missingEvidence.toString(), view.warnings().getFirst().details().get("evidenceId"));
    }

    @Test
    void compactChangeContextPreservesBusinessFactsVersionsProvenanceAndEvidence() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        SpecificationId specificationId = SpecificationId.generate();
        KnowledgeSnapshotMetadata snapshot = activeSnapshot(snapshotId, projectId);
        Evidence evidence = evidence("specs/change.md", 10, 20, "sha256:change");
        Provenance provenance = provenance(evidence.id(), "CHANGE-1");
        ChangeId changeId = ChangeId.generate();
        ChangeProposal change = new ChangeProposal(
                changeId,
                projectId,
                Optional.of("CHANGE-1"),
                "Compact change",
                "Expose compact context",
                List.of("query"),
                List.of("ranking"),
                List.of("schema drift"),
                provenance);
        Constraint constraint = new Constraint(ConstraintId.generate(), changeId, "Must be deterministic", provenance);
        DesignDecision decision = new DesignDecision(
                DesignDecisionId.generate(), changeId, "Canonical JSON", "Use typed DTOs", provenance);
        ImplementationTask task = new ImplementationTask(
                TaskId.generate(), changeId, Optional.of("TASK-1"), "Implement compact view", false, provenance);
        RequirementVersionRecord requirement = requirement(
                snapshotId,
                versionId,
                specificationId,
                RequirementId.generate(),
                EntityVersionId.generate(),
                evidence.id(),
                "REQ-COMPACT",
                "Compact context",
                "MORPHEUS exposes deterministic compact context");
        TraceabilityEntityRef changeRef = ref(TraceabilityEntityKind.CHANGE, changeId.value());
        TraceabilityEntityRef requirementRef = ref(
                TraceabilityEntityKind.REQUIREMENT, requirement.entityVersion().content().id().value());
        TraceabilityLink affects = link(
                changeRef, TraceabilityRelationType.AFFECTS, requirementRef,
                TraceabilityResolutionState.RESOLVED, evidence.id());
        TraceabilitySubgraph graph = new TraceabilitySubgraph(
                changeRef, List.of(changeRef, requirementRef), List.of(affects));
        ChangeContextResult result = new ChangeContextResult(
                snapshot,
                changeId,
                Optional.of(change),
                List.of(affects),
                List.of(requirement),
                List.of(constraint),
                List.of(decision),
                List.of(task),
                graph,
                List.of());
        SnapshotBusinessContent content = new SnapshotBusinessContent(
                snapshotId,
                versionId,
                List.of(),
                List.of(),
                List.of(change),
                List.of(constraint),
                List.of(decision),
                List.of(task),
                List.of(evidence));

        CompactChangeContextView view = new CompactQueryViewService(new FixedContentStore(content)).changeContext(result);

        assertEquals("get_change_context", view.metadata().operation());
        assertEquals(changeId.toString(), view.changeId());
        assertEquals("Compact change", view.change().orElseThrow().title());
        assertEquals(1, view.affectedRequirements().size());
        assertEquals(requirement.entityVersion().id().toString(), view.affectedRequirements().getFirst().entityVersionId());
        assertEquals(versionId.toString(), view.affectedRequirements().getFirst().specificationVersionId());
        assertEquals(1, view.constraints().size());
        assertEquals(1, view.designDecisions().size());
        assertEquals(1, view.implementationTasks().size());
        assertEquals(evidence.id().toString(), view.change().orElseThrow().provenance().evidenceId());
        assertEquals(1, view.evidence().size());
        assertTrue(view.warnings().isEmpty());
    }

    @Test
    void compactChangeContextEmitsOnlyFactBasedWarningsAndKeepsResolvedExternalClean() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        KnowledgeSnapshotMetadata snapshot = activeSnapshot(snapshotId, projectId);
        Evidence evidence = evidence("specs/warnings.md", 1, 1, "sha256:warnings");
        ChangeId changeId = ChangeId.generate();
        TraceabilityEntityRef root = ref(TraceabilityEntityKind.CHANGE, changeId.value());
        TraceabilityEntityRef missingRequirement = ref(TraceabilityEntityKind.REQUIREMENT, DomainIdentity.generate());
        TraceabilityLink affects = link(
                root, TraceabilityRelationType.AFFECTS, missingRequirement,
                TraceabilityResolutionState.RESOLVED, evidence.id());

        List<ExternalTraceabilityView> externalViews = new ArrayList<>();
        List<TraceabilityLink> links = new ArrayList<>();
        List<TraceabilityEntityRef> nodes = new ArrayList<>(List.of(root, missingRequirement));

        externalViews.add(externalView(root, evidence.id(), ExternalTraceabilityAvailability.REFERENCE_UNVALIDATED, 1));
        externalViews.add(externalView(root, evidence.id(), ExternalTraceabilityAvailability.REFERENCE_UNRESOLVED, 2));
        externalViews.add(externalView(root, evidence.id(), ExternalTraceabilityAvailability.REFERENCE_STALE, 3));
        externalViews.add(externalView(root, evidence.id(), ExternalTraceabilityAvailability.REFERENCE_RESOLVED, 4));
        externalViews.add(externalView(root, evidence.id(), ExternalTraceabilityAvailability.BROKEN_REFERENCE, 5));
        for (ExternalTraceabilityView external : externalViews) {
            links.add(external.link());
            nodes.add(external.link().target());
        }
        links.add(affects);

        ChangeContextResult result = new ChangeContextResult(
                snapshot,
                changeId,
                Optional.empty(),
                List.of(affects),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new TraceabilitySubgraph(root, nodes, links),
                externalViews);
        SnapshotBusinessContent content = content(snapshotId, versionId, List.of(evidence));

        CompactChangeContextView view = new CompactQueryViewService(new FixedContentStore(content)).changeContext(result);

        assertEquals(
                Set.of(
                        CompactWarningCode.CHANGE_NOT_FOUND,
                        CompactWarningCode.AFFECTED_REQUIREMENT_UNRESOLVED,
                        CompactWarningCode.EXTERNAL_REFERENCE_UNVALIDATED,
                        CompactWarningCode.EXTERNAL_REFERENCE_UNRESOLVED,
                        CompactWarningCode.EXTERNAL_REFERENCE_STALE,
                        CompactWarningCode.EXTERNAL_REFERENCE_BROKEN),
                Set.copyOf(warningCodes(view.warnings())));
        assertEquals(6, view.warnings().size());
        assertTrue(view.warnings().stream().allMatch(warning -> warning.severity() == DiagnosticSeverity.WARNING));
        assertEquals(5, view.externalReferences().size());
        assertTrue(view.externalReferences().stream()
                .anyMatch(reference -> reference.availability().equals(ExternalTraceabilityAvailability.REFERENCE_RESOLVED.name())));
        assertFalse(view.warnings().stream().anyMatch(warning -> warning.code().name().equals("EXTERNAL_REFERENCE_RESOLVED")));
    }

    @Test
    void compactTraceRequirementPreservesBoundedGraphVersionAndReferencedEvidence() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        KnowledgeSnapshotMetadata snapshot = activeSnapshot(snapshotId, projectId);
        Evidence evidence = evidence("specs/trace.md", 3, 6, "sha256:trace");
        RequirementVersionRecord requirement = requirement(
                snapshotId,
                versionId,
                SpecificationId.generate(),
                RequirementId.generate(),
                EntityVersionId.generate(),
                evidence.id(),
                "REQ-TRACE",
                "Trace requirement",
                "Trace must remain bounded");
        TraceabilityEntityRef requirementRef = ref(
                TraceabilityEntityKind.REQUIREMENT, requirement.entityVersion().content().id().value());
        TraceabilityEntityRef scenarioRef = ref(TraceabilityEntityKind.SCENARIO, DomainIdentity.generate());
        TraceabilityLink refines = link(
                scenarioRef, TraceabilityRelationType.REFINES, requirementRef,
                TraceabilityResolutionState.RESOLVED, evidence.id());
        TraceRequirementResult result = new TraceRequirementResult(
                snapshot,
                requirement,
                new TraceabilitySubgraph(requirementRef, List.of(scenarioRef, requirementRef), List.of(refines)),
                List.of());
        SnapshotBusinessContent content = content(snapshotId, versionId, List.of(evidence));

        CompactTraceRequirementView view = new CompactQueryViewService(new FixedContentStore(content)).traceRequirement(result);

        assertEquals("trace_requirement", view.metadata().operation());
        assertEquals(requirement.entityVersion().id().toString(), view.requirement().entityVersionId());
        assertEquals(versionId.toString(), view.requirement().specificationVersionId());
        assertEquals(TemporalState.CURRENT.name(), view.requirement().temporalState());
        assertEquals(2, view.nodes().size());
        assertEquals(1, view.links().size());
        assertEquals(evidence.id().toString(), view.links().getFirst().evidenceIds().getFirst());
        assertEquals(1, view.evidence().size());
        assertTrue(view.warnings().isEmpty());
    }

    @Test
    void compactProjectionRejectsPublishedResultWithoutBusinessContentProjection() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        KnowledgeSnapshotMetadata snapshot = activeSnapshot(snapshotId, projectId);
        EvidenceId evidenceId = EvidenceId.generate();
        RequirementVersionRecord record = requirement(
                snapshotId, versionId, SpecificationId.generate(), RequirementId.generate(), EntityVersionId.generate(), evidenceId,
                "REQ-NO-CONTENT", "No content", "no projection");
        RequirementSearchPage page = new RequirementSearchPage(
                snapshot,
                RequirementSearchQuery.all(),
                List.of(record),
                new PageRequest(0, 10),
                1,
                false);

        assertThrows(
                KnowledgeStoreException.class,
                () -> new CompactQueryViewService(new FixedContentStore(null)).requirementSearch(page));
    }

    private KnowledgeSnapshotMetadata seed(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore versions,
            SnapshotBusinessContentStore contentStore,
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            SnapshotBusinessContent content) {
        snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace-compact")));
        KnowledgeSnapshotMetadata ready = new KnowledgeSnapshotMetadata(
                snapshotId,
                projectId,
                Optional.empty(),
                KnowledgeSnapshotState.READY,
                Optional.of("revision-compact"),
                T0);
        snapshots.putSnapshot(ready);
        versions.putSpecificationVersion(new SpecificationVersion(
                versionId,
                projectId,
                Optional.of(1L),
                Optional.of("provider-v1"),
                Optional.of("revision-compact"),
                T0,
                Optional.empty()));
        versions.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionId));
        contentStore.putSnapshotContent(content);
        return snapshots.activateSnapshot(snapshotId, Optional.empty());
    }

    private KnowledgeSnapshotMetadata activeSnapshot(
            KnowledgeSnapshotId snapshotId,
            ProjectSpecificationId projectId) {
        return new KnowledgeSnapshotMetadata(
                snapshotId,
                projectId,
                Optional.empty(),
                KnowledgeSnapshotState.ACTIVE,
                Optional.of("revision-compact"),
                T0);
    }

    private SnapshotBusinessContent content(
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            List<Evidence> evidence) {
        return new SnapshotBusinessContent(
                snapshotId,
                versionId,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                evidence);
    }

    private Evidence evidence(String source, int start, int end, String hash) {
        return new Evidence(
                EvidenceId.generate(),
                SourceLocator.file(source),
                Optional.of(new SourceRange(start, end)),
                Optional.of(hash));
    }

    private RequirementVersionRecord requirement(
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            SpecificationId specificationId,
            RequirementId requirementId,
            EntityVersionId entityVersionId,
            EvidenceId evidenceId,
            String key,
            String title,
            String statement) {
        Requirement requirement = new Requirement(
                requirementId,
                specificationId,
                Optional.of(key),
                title,
                statement,
                provenance(evidenceId, key));
        return new RequirementVersionRecord(
                snapshotId,
                new EntityVersion<>(
                        entityVersionId,
                        requirementId.value(),
                        versionId,
                        TemporalState.CURRENT,
                        requirement));
    }

    private Provenance provenance(EvidenceId evidenceId, String externalId) {
        return new Provenance(
                new ProviderId("compact-test"),
                Optional.of("1"),
                SourceLocator.file("specs/compact.md"),
                Optional.of(externalId),
                Optional.of("source-revision-compact"),
                evidenceId);
    }

    private TraceabilityEntityRef ref(TraceabilityEntityKind kind, DomainIdentity identity) {
        return new TraceabilityEntityRef(kind, identity);
    }

    private TraceabilityLink link(
            TraceabilityEntityRef source,
            TraceabilityRelationType relation,
            TraceabilityEntityRef target,
            TraceabilityResolutionState resolution,
            EvidenceId evidenceId) {
        return new TraceabilityLink(
                TraceabilityLinkId.generate(),
                source,
                relation,
                target,
                TraceabilityLinkOrigin.EXPLICIT,
                resolution,
                Optional.empty(),
                Set.of(evidenceId),
                T0.plusSeconds(1));
    }

    private ExternalTraceabilityView externalView(
            TraceabilityEntityRef owner,
            EvidenceId evidenceId,
            ExternalTraceabilityAvailability availability,
            int ordinal) {
        ExternalReferenceId referenceId = ExternalReferenceId.generate();
        ExternalReferenceTarget target = new ExternalReferenceTarget(
                "MINOS",
                Optional.of("morpheus-engine"),
                "CODE_SYMBOL",
                "com.morpheus.Target" + ordinal,
                Optional.of("rev-" + ordinal));
        Optional<ExternalReference> reference;
        TraceabilityResolutionState linkResolution;

        if (availability == ExternalTraceabilityAvailability.BROKEN_REFERENCE) {
            reference = Optional.empty();
            linkResolution = TraceabilityResolutionState.UNRESOLVED;
        } else {
            ExternalReference value = ExternalReference.unvalidated(
                    referenceId,
                    owner.identity(),
                    target,
                    Optional.of(provenance(evidenceId, "external-" + ordinal)));
            value = switch (availability) {
                case REFERENCE_UNVALIDATED -> value;
                case REFERENCE_UNRESOLVED -> value.transition(
                        ExternalReferenceResolutionState.UNRESOLVED,
                        ExternalReferenceResolutionReason.TARGET_UNAVAILABLE,
                        Optional.empty(),
                        T0.plusSeconds(ordinal));
                case REFERENCE_STALE -> value.transition(
                        ExternalReferenceResolutionState.STALE,
                        ExternalReferenceResolutionReason.TARGET_REMOVED,
                        Optional.empty(),
                        T0.plusSeconds(ordinal));
                case REFERENCE_RESOLVED -> value.transition(
                        ExternalReferenceResolutionState.RESOLVED,
                        ExternalReferenceResolutionReason.RESOLVED,
                        Optional.of(new ResolvedExternalTarget(target, Map.of("kind", "symbol"))),
                        T0.plusSeconds(ordinal));
                case BROKEN_REFERENCE -> throw new IllegalStateException("handled above");
            };
            reference = Optional.of(value);
            linkResolution = switch (availability) {
                case REFERENCE_UNVALIDATED, REFERENCE_UNRESOLVED -> TraceabilityResolutionState.UNRESOLVED;
                case REFERENCE_STALE -> TraceabilityResolutionState.PARTIALLY_RESOLVED;
                case REFERENCE_RESOLVED -> TraceabilityResolutionState.RESOLVED;
                case BROKEN_REFERENCE -> throw new IllegalStateException("handled above");
            };
        }

        TraceabilityLink link = new TraceabilityLink(
                TraceabilityLinkId.generate(),
                owner,
                TraceabilityRelationType.LINKS_TO_CODE,
                ref(TraceabilityEntityKind.EXTERNAL_REFERENCE, referenceId.value()),
                TraceabilityLinkOrigin.EXPLICIT,
                linkResolution,
                Optional.empty(),
                Set.of(evidenceId),
                T0.plusSeconds(20 + ordinal));
        return new ExternalTraceabilityView(link, reference, availability);
    }

    private List<CompactWarningCode> warningCodes(
            List<com.morpheus.application.query.compact.CompactQueryTypes.WarningView> warnings) {
        return warnings.stream()
                .map(com.morpheus.application.query.compact.CompactQueryTypes.WarningView::code)
                .toList();
    }

    private static final class FixedContentStore implements SnapshotBusinessContentStore {
        private SnapshotBusinessContent content;

        private FixedContentStore(SnapshotBusinessContent content) {
            this.content = content;
        }

        @Override
        public void putSnapshotContent(SnapshotBusinessContent content) {
            this.content = content;
        }

        @Override
        public Optional<SnapshotBusinessContent> findSnapshotContent(KnowledgeSnapshotId snapshotId) {
            if (content == null || !content.snapshotId().equals(snapshotId)) {
                return Optional.empty();
            }
            return Optional.of(content);
        }
    }
}
