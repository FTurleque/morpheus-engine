package com.morpheus.application.analysis;

import com.morpheus.application.analysis.compact.CompactChangeAnalysisView;
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
import com.morpheus.domain.identity.DomainIdentity;
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
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.task.TaskId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
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
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Change analysis against a published baseline, with each port it reads implemented by hand. */
class ChangeAnalysisServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private static final EvidenceId EVIDENCE = EvidenceId.generate();

    private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
    private final KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
    private final SpecificationId specificationId = SpecificationId.generate();
    private final ChangeId changeId = ChangeId.generate();
    private final Snapshots snapshots = new Snapshots();
    private final Contents contents = new Contents();
    private final Versions versions = new Versions();
    private final Links links = new Links();
    private final ChangeAnalysisService service = new ChangeAnalysisService(snapshots, contents, versions, links);

    @Test
    void aModifiedRequirementNamesEveryChangedFieldAndItsDependenciesInBothDirections() {
        publish(KnowledgeSnapshotState.ACTIVE);
        Requirement current = requirement("R-1", "Title", "Statement");
        Requirement dependency = requirement("R-2", "Dependency", "Depends");
        Requirement dependent = requirement("R-3", "Dependent", "Depended");
        versions.current(current, dependency, dependent);
        contents.scenarios.add(scenario(current.id(), "Old scenario"));
        links.add(link(dependent.id(), current.id(), TraceabilityResolutionState.RESOLVED));
        links.add(link(current.id(), dependency.id(), TraceabilityResolutionState.PARTIALLY_RESOLVED));

        RequirementDelta modified = delta(RequirementDeltaKind.MODIFIED, current.id(), "other-spec", "R-1b", "New title",
                Optional.of("New statement"), List.of(scenario(current.id(), "New scenario")));
        ChangeAnalysisResult result = service.analyzeActive(proposal(modified), 3).orElseThrow();

        RequirementChangeImpact impact = result.requirementImpacts().getFirst();
        assertEquals(Set.of(RequirementChangeField.SPECIFICATION, RequirementChangeField.KEY,
                RequirementChangeField.TITLE, RequirementChangeField.STATEMENT, RequirementChangeField.SCENARIOS),
                impact.changedFields());
        assertEquals(1, result.summary().dependencies(), "R-1 depends on R-2");
        assertEquals(1, result.summary().dependents(), "R-3 depends on R-1");
        assertEquals(5, result.summary().changedDocumentaryFields());
        assertEquals(1, result.summary().modifiedRequirements());
        assertTrue(codes(result).contains(ChangeAnalysisWarningCode.TRACEABILITY_PATH_PARTIALLY_RESOLVED),
                () -> "a dependency path through an unresolved link is reported: " + codes(result));
        assertTrue(codes(result).contains(ChangeAnalysisWarningCode.ACCEPTANCE_CRITERIA_UNAVAILABLE));
        assertEquals(result.warnings().size(), result.summary().warnings());

        CompactChangeAnalysisViewService views = new CompactChangeAnalysisViewService();
        CompactChangeAnalysisView view = views.toView(result);
        assertEquals(2, view.dependencyImpacts().size());
        assertEquals(views.toJson(result), new String(views.toUtf8(result), java.nio.charset.StandardCharsets.UTF_8),
                "the JSON text and its UTF-8 bytes are the same canonical document");
        assertTrue(views.toJson(result).contains("\"analyze_change\""), "the view names its query");
    }

    @Test
    void eachDeltaKindReportsWhatTheBaselineCannotSupport() {
        publish(KnowledgeSnapshotState.RETIRED);
        Requirement present = requirement("R-1", "Present", "Statement");
        Requirement unchanged = requirement("R-2", "Unchanged", "Statement");
        Requirement removed = requirement("R-4", "Removed", "Statement");
        Requirement unresolvedSpecification = new Requirement(RequirementId.generate(), SpecificationId.generate(),
                Optional.of("R-3"), "Elsewhere", "Statement", provenance());
        versions.current(present, unchanged, unresolvedSpecification, removed);

        ChangeAnalysisResult result = service.analyzeSnapshot(snapshotId, proposal(
                delta(RequirementDeltaKind.ADDED, present.id(), "spec", "R-1", "Present", Optional.of("Statement"), List.of()),
                delta(RequirementDeltaKind.ADDED, RequirementId.generate(), "spec", "R-9", "New", Optional.of("New"), List.of()),
                delta(RequirementDeltaKind.MODIFIED, RequirementId.generate(), "spec", "R-8", "Gone", Optional.of("Gone"), List.of()),
                delta(RequirementDeltaKind.MODIFIED, unchanged.id(), "spec", "R-2", "Unchanged", Optional.of("Statement"), List.of()),
                delta(RequirementDeltaKind.MODIFIED, unresolvedSpecification.id(), "spec", "R-3", "Elsewhere",
                        Optional.of("Statement"), List.of()),
                delta(RequirementDeltaKind.REMOVED, removed.id(), "spec", "R-4", "Removed", Optional.empty(), List.of()),
                delta(RequirementDeltaKind.REMOVED, RequirementId.generate(), "spec", "R-7", "Absent", Optional.empty(), List.of())),
                2);

        List<ChangeAnalysisWarningCode> codes = codes(result);
        for (ChangeAnalysisWarningCode expected : List.of(
                ChangeAnalysisWarningCode.ADDED_REQUIREMENT_ALREADY_CURRENT,
                ChangeAnalysisWarningCode.PROPOSED_ONLY_REQUIREMENT_TRACEABILITY_UNAVAILABLE,
                ChangeAnalysisWarningCode.MODIFIED_REQUIREMENT_BASELINE_MISSING,
                ChangeAnalysisWarningCode.MODIFIED_WITHOUT_DOCUMENTARY_CHANGE,
                ChangeAnalysisWarningCode.SPECIFICATION_KEY_UNRESOLVED,
                ChangeAnalysisWarningCode.REMOVED_REQUIREMENT_BASELINE_MISSING)) {
            assertTrue(codes.contains(expected), () -> expected + " is missing from " + codes);
        }
        assertEquals(2, result.summary().addedRequirements());
        assertEquals(3, result.summary().modifiedRequirements());
        assertEquals(2, result.summary().removedRequirements());
        assertEquals(0, result.summary().dependencies());
    }

    @Test
    void aDependencyChainDeeperThanTheRequestedDepthIsReportedAsTruncated() {
        publish(KnowledgeSnapshotState.ACTIVE);
        Requirement first = requirement("R-1", "First", "Statement");
        Requirement second = requirement("R-2", "Second", "Statement");
        Requirement third = requirement("R-3", "Third", "Statement");
        versions.current(first, second, third);
        links.add(link(first.id(), second.id(), TraceabilityResolutionState.RESOLVED));
        links.add(link(second.id(), third.id(), TraceabilityResolutionState.RESOLVED));

        ChangeAnalysisResult result = service.analyzeSnapshot(snapshotId, proposal(delta(RequirementDeltaKind.REMOVED,
                first.id(), "spec", "R-1", "First", Optional.empty(), List.of())), 1);

        ChangeAnalysisWarning truncated = result.warnings().stream()
                .filter(warning -> warning.code() == ChangeAnalysisWarningCode.TRACEABILITY_TRAVERSAL_TRUNCATED)
                .findFirst().orElseThrow();
        assertEquals("DEPTH_BUDGET_REACHED:1", truncated.details().get(ChangeAnalysisWarning.TRUNCATION_REASON));
        assertEquals(1, result.summary().dependencies(), "only the dependency inside the depth is reported");
    }

    @Test
    void anAnalysisOutsideAPublishedBaselineOfItsOwnProjectIsRefusedByName() {
        RequirementDelta removed = delta(RequirementDeltaKind.REMOVED, RequirementId.generate(), "spec", "R-1", "T",
                Optional.empty(), List.of());
        ProposedChangeSet proposal = proposal(removed);

        IllegalArgumentException depth = assertThrows(IllegalArgumentException.class,
                () -> service.analyzeSnapshot(snapshotId, proposal, 0));
        assertEquals("maxDepth must be greater than zero", depth.getMessage());
        assertTrue(service.analyzeActive(proposal, 1).isEmpty(), "no active snapshot, no analysis");

        KnowledgeStoreException unknown = assertThrows(KnowledgeStoreException.class,
                () -> service.analyzeSnapshot(snapshotId, proposal, 1));
        assertTrue(unknown.getMessage().contains("unknown knowledge snapshot"), unknown::getMessage);

        publish(KnowledgeSnapshotState.READY);
        KnowledgeStoreException ready = assertThrows(KnowledgeStoreException.class,
                () -> service.analyzeSnapshot(snapshotId, proposal, 1));
        assertTrue(ready.getMessage().contains("requires an ACTIVE or RETIRED snapshot"), ready::getMessage);

        snapshots.put(new KnowledgeSnapshotMetadata(snapshotId, ProjectSpecificationId.generate(), Optional.empty(),
                KnowledgeSnapshotState.ACTIVE, Optional.empty(), NOW));
        KnowledgeStoreException crossing = assertThrows(KnowledgeStoreException.class,
                () -> service.analyzeSnapshot(snapshotId, proposal, 1));
        assertEquals("change analysis cannot cross project boundaries", crossing.getMessage());

        publish(KnowledgeSnapshotState.ACTIVE);
        contents.published = false;
        KnowledgeStoreException withoutContent = assertThrows(KnowledgeStoreException.class,
                () -> service.analyzeSnapshot(snapshotId, proposal, 1));
        assertTrue(withoutContent.getMessage().contains("has no business-content projection"), withoutContent::getMessage);
    }

    @Test
    void theCompactViewProjectsTheConstraintsDecisionsAndTasksOfTheChange() {
        publish(KnowledgeSnapshotState.ACTIVE);
        Constraint constraint = Constraint.withUnknownSemantics(ConstraintId.generate(), changeId, "Keep it local",
                provenance());
        DesignDecision decision = new DesignDecision(DesignDecisionId.generate(), changeId, "Port", "Use a port",
                provenance());
        ImplementationTask task = new ImplementationTask(TaskId.generate(), changeId, Optional.of("1.1"), "Task", true,
                provenance());
        ChangeProposal change = new ChangeProposal(changeId, projectId, Optional.of("change"), "Change", "Intent",
                List.of(), List.of(), List.of(), provenance());

        CompactChangeAnalysisView view = new CompactChangeAnalysisViewService().toView(service.analyzeActive(
                new ProposedChangeSet(change, List.of(), List.of(constraint), List.of(decision), List.of(task)), 1)
                .orElseThrow());

        assertEquals(constraint.id().toString(), view.constraints().getFirst().id());
        assertEquals("Keep it local", view.constraints().getFirst().statement());
        assertEquals(changeId.toString(), view.designDecisions().getFirst().changeId());
        assertEquals("Use a port", view.designDecisions().getFirst().decision());
        assertEquals(Optional.of("1.1"), view.implementationTasks().getFirst().key());
        assertTrue(view.implementationTasks().getFirst().completed());
        assertEquals("openspec", view.implementationTasks().getFirst().provenance().providerId());
    }

    @Test
    void aProposalOwnsItsItemsAndAddressesEachRequirementOnce() {
        RequirementId requirementId = RequirementId.generate();
        RequirementDelta first = delta(RequirementDeltaKind.MODIFIED, requirementId, "spec", "R-1", "T",
                Optional.of("S"), List.of());
        RequirementDelta second = delta(RequirementDeltaKind.REMOVED, requirementId, "spec", "R-1", "T",
                Optional.empty(), List.of());
        IllegalArgumentException ambiguous = assertThrows(IllegalArgumentException.class,
                () -> proposal(first, second));
        assertTrue(ambiguous.getMessage().contains("multiple deltas for one logical requirement"),
                ambiguous::getMessage);

        RequirementDelta foreign = new RequirementDelta(RequirementDeltaId.generate(), ChangeId.generate(),
                RequirementDeltaKind.ADDED, "spec", RequirementId.generate(), Optional.empty(), "T", Optional.of("S"),
                List.of(), provenance());
        IllegalArgumentException owner = assertThrows(IllegalArgumentException.class, () -> proposal(foreign));
        assertTrue(owner.getMessage().startsWith("requirement delta belongs to another change"), owner::getMessage);
    }

    private void publish(KnowledgeSnapshotState state) {
        snapshots.put(new KnowledgeSnapshotMetadata(snapshotId, projectId, Optional.empty(), state,
                Optional.of("baseline"), NOW));
    }

    private ProposedChangeSet proposal(RequirementDelta... deltas) {
        ChangeProposal change = new ChangeProposal(changeId, projectId, Optional.of("change"), "Change", "Intent",
                List.of(), List.of(), List.of(), provenance());
        return new ProposedChangeSet(change, List.of(deltas), List.of(), List.of(), List.of());
    }

    private RequirementDelta delta(RequirementDeltaKind kind, RequirementId requirementId, String specificationKey,
            String key, String title, Optional<String> statement, List<Scenario> scenarios) {
        return new RequirementDelta(RequirementDeltaId.generate(), changeId, kind, specificationKey, requirementId,
                Optional.of(key), title, statement, scenarios, provenance());
    }

    private Requirement requirement(String key, String title, String statement) {
        return new Requirement(RequirementId.generate(), specificationId, Optional.of(key), title, statement,
                provenance());
    }

    private static Scenario scenario(RequirementId requirementId, String title) {
        return new Scenario(ScenarioId.generate(), Optional.of(requirementId), title, List.of("given"), "when",
                "then", provenance());
    }

    private static TraceabilityLink link(RequirementId source, RequirementId target, TraceabilityResolutionState state) {
        return new TraceabilityLink(TraceabilityLinkId.generate(),
                new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, source.value()),
                TraceabilityRelationType.DEPENDS_ON,
                new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, target.value()),
                TraceabilityLinkOrigin.EXPLICIT, state, Optional.empty(), Set.of(EvidenceId.generate()), NOW);
    }

    private static Provenance provenance() {
        return new Provenance(new ProviderId("openspec"), Optional.of("1"), SourceLocator.file("specs/r.md"),
                Optional.of("R-1"), Optional.of("rev"), EVIDENCE);
    }

    private static List<ChangeAnalysisWarningCode> codes(ChangeAnalysisResult result) {
        return result.warnings().stream().map(ChangeAnalysisWarning::code).toList();
    }

    private static final class Snapshots implements SpecificationKnowledgeStore {
        private final Map<KnowledgeSnapshotId, KnowledgeSnapshotMetadata> byId = new HashMap<>();

        private void put(KnowledgeSnapshotMetadata snapshot) {
            byId.put(snapshot.id(), snapshot);
        }

        @Override public Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId snapshotId) {
            return Optional.ofNullable(byId.get(snapshotId));
        }

        @Override public Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId projectId) {
            return byId.values().stream()
                    .filter(snapshot -> snapshot.projectId().equals(projectId))
                    .filter(snapshot -> snapshot.state() == KnowledgeSnapshotState.ACTIVE)
                    .findFirst();
        }

        @Override public void putProject(ProjectStoreEntry project) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProjectStoreEntry> findProject(ProjectSpecificationId projectId) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator) { throw new UnsupportedOperationException(); }
        @Override public List<ProjectStoreEntry> listProjects() { throw new UnsupportedOperationException(); }
        @Override public void putSnapshot(KnowledgeSnapshotMetadata snapshot) { throw new UnsupportedOperationException(); }
        @Override public KnowledgeSnapshotMetadata transitionSnapshotState(
                KnowledgeSnapshotId id, KnowledgeSnapshotState from, KnowledgeSnapshotState to) {
            throw new UnsupportedOperationException();
        }
        @Override public KnowledgeSnapshotMetadata activateSnapshot(
                KnowledgeSnapshotId id, Optional<KnowledgeSnapshotId> expected) {
            throw new UnsupportedOperationException();
        }
    }

    private final class Contents implements SnapshotBusinessContentStore {
        private final List<Scenario> scenarios = new ArrayList<>();
        private boolean published = true;

        @Override public Optional<SnapshotBusinessContent> findSnapshotContent(KnowledgeSnapshotId id) {
            if (!published) {
                return Optional.empty();
            }
            Specification specification = new Specification(specificationId, projectId, "spec", "Spec",
                    Optional.empty(), provenance());
            return Optional.of(new SnapshotBusinessContent(id, SpecificationVersionId.generate(),
                    List.of(specification), scenarios, List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(new Evidence(EVIDENCE, SourceLocator.file("specs/r.md"), Optional.empty(), Optional.empty()))));
        }

        @Override public void putSnapshotContent(SnapshotBusinessContent content) {
            throw new UnsupportedOperationException();
        }
    }

    private final class Versions implements VersionedRequirementStore {
        private final Map<DomainIdentity, RequirementVersionRecord> current = new HashMap<>();

        private void current(Requirement... requirements) {
            for (Requirement requirement : requirements) {
                current.put(requirement.id().value(), new RequirementVersionRecord(snapshotId, new EntityVersion<>(
                        EntityVersionId.generate(), requirement.id().value(), SpecificationVersionId.generate(),
                        TemporalState.CURRENT, requirement)));
            }
        }

        @Override public Optional<RequirementVersionRecord> currentRequirement(
                KnowledgeSnapshotId id, DomainIdentity entityIdentity) {
            return Optional.ofNullable(current.get(entityIdentity));
        }

        @Override public void putSpecificationVersion(SpecificationVersion version) { throw new UnsupportedOperationException(); }
        @Override public Optional<SpecificationVersion> findSpecificationVersion(SpecificationVersionId versionId) { throw new UnsupportedOperationException(); }
        @Override public void bindSnapshotVersion(SnapshotSpecificationVersionBinding binding) { throw new UnsupportedOperationException(); }
        @Override public Optional<SnapshotSpecificationVersionBinding> findSnapshotVersion(KnowledgeSnapshotId id) { throw new UnsupportedOperationException(); }
        @Override public void putRequirementVersion(RequirementVersionRecord record) { throw new UnsupportedOperationException(); }
        @Override public Optional<RequirementVersionRecord> findRequirementVersion(EntityVersionId entityVersionId) { throw new UnsupportedOperationException(); }
        @Override public List<RequirementVersionRecord> listRequirementVersions(KnowledgeSnapshotId id) { throw new UnsupportedOperationException(); }
    }

    private static final class Links implements TraceabilityStore {
        private final List<TraceabilityLink> links = new ArrayList<>();

        private void add(TraceabilityLink link) {
            links.add(link);
        }

        @Override public List<TraceabilityLink> outgoing(KnowledgeSnapshotId id, TraceabilityEntityRef source,
                Set<TraceabilityRelationType> relationTypes) {
            return links.stream().filter(link -> link.source().equals(source))
                    .filter(link -> relationTypes.contains(link.relationType())).toList();
        }

        @Override public List<TraceabilityLink> incoming(KnowledgeSnapshotId id, TraceabilityEntityRef target,
                Set<TraceabilityRelationType> relationTypes) {
            return links.stream().filter(link -> link.target().equals(target))
                    .filter(link -> relationTypes.contains(link.relationType())).toList();
        }

        @Override public void putLink(KnowledgeSnapshotId id, TraceabilityLink link) { throw new UnsupportedOperationException(); }
        @Override public void putLinks(KnowledgeSnapshotId id, List<TraceabilityLink> batch) { throw new UnsupportedOperationException(); }
        @Override public Optional<TraceabilityLink> findLink(KnowledgeSnapshotId id, TraceabilityLinkId linkId) { throw new UnsupportedOperationException(); }
    }
}
