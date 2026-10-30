package com.morpheus.application.query.compact;

import com.morpheus.application.query.ChangeContextQueryService;
import com.morpheus.application.query.ChangeContextResult;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.PublishedSnapshotWorld;
import com.morpheus.application.query.RequirementQueryService;
import com.morpheus.application.query.RequirementSearchPage;
import com.morpheus.application.query.RequirementSearchQuery;
import com.morpheus.application.query.TraceRequirementQueryService;
import com.morpheus.application.query.compact.CompactQueryTypes.EvidenceView;
import com.morpheus.application.query.compact.CompactQueryTypes.ExternalReferenceView;
import com.morpheus.application.query.compact.CompactQueryTypes.PageMetadata;
import com.morpheus.application.query.compact.CompactQueryTypes.ProvenanceView;
import com.morpheus.application.query.compact.CompactQueryTypes.QueryMetadata;
import com.morpheus.application.query.compact.CompactQueryTypes.RequirementView;
import com.morpheus.application.query.compact.CompactQueryTypes.SnapshotMetadata;
import com.morpheus.application.query.compact.CompactQueryTypes.SourceRangeView;
import com.morpheus.application.query.compact.CompactQueryTypes.TraceNodeView;
import com.morpheus.application.query.compact.CompactQueryTypes.WarningView;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.traceability.TraceRequirementResult;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceResolutionReason;
import com.morpheus.domain.reference.ExternalReferenceResolutionState;
import com.morpheus.domain.reference.ResolvedExternalTarget;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.morpheus.application.query.PublishedSnapshotWorld.ref;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactQueryViewServiceTest {
    private final PublishedSnapshotWorld world = new PublishedSnapshotWorld();
    private final CompactQueryViewService service = new CompactQueryViewService(world.contentStore());

    @Test
    void aRequirementSearchIsProjectedWithItsPageAndOnlyTheEvidenceItsProvenanceNames() {
        Specification specification = world.specification("spec");
        RequirementVersionRecord first = world.requirement(specification.id(), "R-1", "audit the login");
        world.requirement(specification.id(), "R-2", "audit the export");
        world.evidence("specs/unreferenced.md");
        RequirementSearchPage page = new RequirementQueryService(world.snapshots(), world.requirementStore())
                .findActive(world.projectId(), new RequirementSearchQuery("Audit"), new PageRequest(0, 1))
                .orElseThrow();

        CompactRequirementSearchView view = service.requirementSearch(page);

        assertEquals(new QueryMetadata(1, "find_requirements"), view.metadata());
        assertEquals(new SnapshotMetadata(world.active().id().toString(), world.projectId().toString(), "ACTIVE",
                Optional.empty(), Optional.of("rev-1"), PublishedSnapshotWorld.CREATED_AT.toString()), view.snapshot());
        assertEquals("audit", view.searchText());
        assertEquals(new PageMetadata(0, 1, 2, true), view.page());
        assertEquals(List.of(new RequirementView(
                first.entityVersion().content().id().toString(),
                first.entityVersion().id().toString(),
                first.entityVersion().specificationVersionId().toString(),
                "CURRENT",
                specification.id().toString(),
                Optional.of("R-1"),
                "Requirement R-1",
                "audit the login",
                provenanceView())), view.requirements());
        assertEquals(List.of(evidenceView(world.definition())), view.evidence());
        assertEquals(List.of(), view.warnings());
    }

    @Test
    void aTraceNamesEveryExternalReferenceThatIsNotResolvedAndEveryEvidenceTheSnapshotLacks() {
        Specification specification = world.specification("spec");
        RequirementVersionRecord requirement = world.requirement(specification.id(), "R-1", "traced");
        RequirementId requirementId = requirement.entityVersion().content().id();
        TraceabilityEntityRef root = ref(TraceabilityEntityKind.REQUIREMENT, requirementId.value());
        ExternalReference unvalidated = world.reference(requirementId.value());
        ExternalReference resolved = transitioned(world.reference(requirementId.value()),
                ExternalReferenceResolutionState.RESOLVED, ExternalReferenceResolutionReason.RESOLVED);
        ExternalReference stale = transitioned(world.reference(requirementId.value()),
                ExternalReferenceResolutionState.STALE, ExternalReferenceResolutionReason.TARGET_REMOVED);
        ExternalReference unresolved = transitioned(world.reference(requirementId.value()),
                ExternalReferenceResolutionState.UNRESOLVED, ExternalReferenceResolutionReason.NO_RESOLVER);
        TraceabilityLink unvalidatedLink = external(root, unvalidated);
        TraceabilityLink resolvedLink = external(root, resolved);
        external(root, stale);
        external(root, unresolved);
        TraceabilityLink brokenLink = world.link(root, TraceabilityRelationType.IMPLEMENTS,
                ref(TraceabilityEntityKind.EXTERNAL_REFERENCE, world.next()));
        EvidenceId missing = new EvidenceId(world.next());
        Evidence support = world.evidence("reviews/support.md");
        world.link(ref(TraceabilityEntityKind.SPECIFICATION, specification.id().value()),
                TraceabilityRelationType.REFINES, root, Set.of(missing, support.id()));
        TraceRequirementResult trace = new TraceRequirementQueryService(world.snapshots(), world.requirementStore(),
                world.traceabilityStore(), world.referenceStore())
                .active(world.projectId(), requirementId, 1, Set.of()).orElseThrow();

        CompactTraceRequirementView view = service.traceRequirement(trace);

        assertEquals(new QueryMetadata(1, "trace_requirement"), view.metadata());
        assertEquals(requirementId.toString(), view.requirement().id());
        assertEquals(6, view.links().size());
        assertEquals(7, view.nodes().size());
        assertEquals(new TraceNodeView("EXTERNAL_REFERENCE", unvalidated.id().toString()), view.nodes().getFirst());
        assertEquals(new TraceNodeView("SPECIFICATION", specification.id().toString()), view.nodes().getLast());
        assertEquals(List.of(
                        CompactWarningCode.EVIDENCE_NOT_FOUND,
                        CompactWarningCode.EXTERNAL_REFERENCE_BROKEN,
                        CompactWarningCode.EXTERNAL_REFERENCE_STALE,
                        CompactWarningCode.EXTERNAL_REFERENCE_UNRESOLVED,
                        CompactWarningCode.EXTERNAL_REFERENCE_UNVALIDATED),
                view.warnings().stream().map(WarningView::code).toList());
        assertEquals(new WarningView(CompactWarningCode.EVIDENCE_NOT_FOUND, DiagnosticSeverity.WARNING,
                "Referenced evidence is not available in the snapshot business-content projection",
                Map.of("evidenceId", missing.toString())), view.warnings().getFirst());
        assertEquals(Map.of("availability", "BROKEN_REFERENCE", "linkId", brokenLink.id().toString(),
                "referenceId", brokenLink.target().identity().toString()), view.warnings().get(1).details());
        assertEquals(List.of(evidenceView(world.definition()), evidenceView(support)), view.evidence());
        ExternalReferenceView first = view.externalReferences().getFirst();
        assertEquals(new ExternalReferenceView(unvalidatedLink.id().toString(), "REFERENCE_UNVALIDATED",
                Optional.of(unvalidated.id().toString()), Optional.of("MINOS"), Optional.of("morpheus"),
                Optional.of("SYMBOL"), Optional.of("symbol:Owner"), Optional.of("r1"), Optional.of(provenanceView())),
                first);
        assertEquals("REFERENCE_RESOLVED", view.externalReferences().get(1).availability());
        assertEquals(resolvedLink.id().toString(), view.externalReferences().get(1).linkId());
        ExternalReferenceView broken = view.externalReferences().getLast();
        assertEquals("BROKEN_REFERENCE", broken.availability());
        assertTrue(broken.system().isEmpty());
        assertTrue(broken.provenance().isEmpty());
        assertFalse(view.truncated());
        assertTrue(view.truncationReason().isEmpty());
    }

    @Test
    void aChangeContextProjectsTheChangeItsChildrenAndNamesAnAffectedRequirementWithoutOccurrence() {
        Specification specification = world.specification("spec");
        ChangeProposal change = world.change("change");
        Constraint constraint = world.constraint(change.id());
        DesignDecision decision = world.decision(change.id());
        ImplementationTask task = world.task(change.id());
        RequirementVersionRecord affected = world.requirement(specification.id(), "R-1", "affected");
        TraceabilityEntityRef root = ref(TraceabilityEntityKind.CHANGE, change.id().value());
        world.link(root, TraceabilityRelationType.AFFECTS,
                ref(TraceabilityEntityKind.REQUIREMENT, affected.entityVersion().entityIdentity()));
        TraceabilityLink unresolved = world.link(root, TraceabilityRelationType.AFFECTS,
                ref(TraceabilityEntityKind.REQUIREMENT, world.next()));

        CompactChangeContextView view = service.changeContext(changeContext(change.id()));

        assertEquals(new QueryMetadata(1, "get_change_context"), view.metadata());
        assertEquals(change.id().toString(), view.changeId());
        CompactQueryTypes.ChangeView changeView = view.change().orElseThrow();
        assertEquals(List.of("scope"), changeView.scope());
        assertEquals(Optional.of("change"), changeView.key());
        assertEquals(List.of(new CompactQueryTypes.ConstraintView(constraint.id().toString(),
                change.id().toString(), "Constraint statement", provenanceView())), view.constraints());
        assertEquals(List.of(new CompactQueryTypes.DesignDecisionView(decision.id().toString(),
                change.id().toString(), "Decision", "Use a port", provenanceView())), view.designDecisions());
        assertEquals(List.of(new CompactQueryTypes.ImplementationTaskView(task.id().toString(),
                change.id().toString(), Optional.of("1.1"), "Task", false, provenanceView())),
                view.implementationTasks());
        assertEquals(2, view.affectedRequirementLinks().size());
        assertEquals(List.of(affected.entityVersion().content().id().toString()),
                view.affectedRequirements().stream().map(RequirementView::id).toList());
        assertEquals(List.of(new WarningView(CompactWarningCode.AFFECTED_REQUIREMENT_UNRESOLVED,
                DiagnosticSeverity.WARNING, "AFFECTS target has no CURRENT requirement occurrence in the snapshot",
                Map.of("linkId", unresolved.id().toString(),
                        "requirementId", unresolved.target().identity().toString(),
                        "snapshotId", world.active().id().toString()))), view.warnings());
        assertEquals(3, view.nodes().size());
        assertEquals(List.of(evidenceView(world.definition())), view.evidence());
    }

    @Test
    void aChangeAbsentFromTheSnapshotIsNamedAsAWarningRatherThanOmitted() {
        ChangeId absent = new ChangeId(world.next());

        CompactChangeContextView view = service.changeContext(changeContext(absent));

        assertTrue(view.change().isEmpty());
        assertEquals(List.of(new WarningView(CompactWarningCode.CHANGE_NOT_FOUND, DiagnosticSeverity.WARNING,
                "Change is not present in the published snapshot",
                Map.of("changeId", absent.toString(), "snapshotId", world.active().id().toString()))),
                view.warnings());
        assertEquals(List.of(), view.evidence());
    }

    @Test
    void aProjectionIsRefusedWhenTheSnapshotHasNoContentOrTheContentOfAnotherSnapshot() {
        RequirementSearchPage page = new RequirementQueryService(world.snapshots(), world.requirementStore())
                .findActive(world.projectId(), RequirementSearchQuery.all(), PageRequest.first(1)).orElseThrow();
        KnowledgeSnapshotMetadata other = world.snapshot(KnowledgeSnapshotState.RETIRED);

        world.withContentOf(other.id());
        KnowledgeStoreException foreign = assertThrows(KnowledgeStoreException.class,
                () -> service.requirementSearch(page));
        assertEquals("business-content projection belongs to another snapshot: " + other.id(), foreign.getMessage());

        world.withoutContent();
        KnowledgeStoreException missing = assertThrows(KnowledgeStoreException.class,
                () -> service.requirementSearch(page));
        assertEquals("published snapshot has no business-content projection for compact view: "
                + world.active().id(), missing.getMessage());
    }

    @Test
    void theCompactViewsRefuseATruncationFlagWithoutItsReasonAndABlankChange() {
        CompactChangeContextView change = service.changeContext(changeContext(new ChangeId(world.next())));

        IllegalArgumentException blank = assertThrows(IllegalArgumentException.class,
                () -> withChangeAndTruncation(change, "  ", Optional.empty(), false));
        assertEquals("changeId must not be blank", blank.getMessage());
        IllegalArgumentException flagWithoutReason = assertThrows(IllegalArgumentException.class,
                () -> withChangeAndTruncation(change, change.changeId(), Optional.empty(), true));
        assertEquals("truncated must be true exactly when a truncationReason is present",
                flagWithoutReason.getMessage());
        assertEquals("abc", withChangeAndTruncation(change, " abc ", Optional.of("DEPTH_BUDGET_REACHED:1"), true)
                .changeId());

        Specification specification = world.specification("spec");
        RequirementId requirementId = world.requirement(specification.id(), "R-1", "traced")
                .entityVersion().content().id();
        CompactTraceRequirementView trace = service.traceRequirement(new TraceRequirementQueryService(
                world.snapshots(), world.requirementStore(), world.traceabilityStore(), world.referenceStore())
                .active(world.projectId(), requirementId, 1, Set.of()).orElseThrow());
        IllegalArgumentException reasonWithoutFlag = assertThrows(IllegalArgumentException.class,
                () -> new CompactTraceRequirementView(trace.metadata(), trace.snapshot(), trace.requirement(),
                        trace.nodes(), trace.links(), trace.externalReferences(), trace.evidence(), trace.warnings(),
                        Optional.of("NODE_BUDGET_REACHED:1000"), false));
        assertEquals("truncated must be true exactly when a truncationReason is present",
                reasonWithoutFlag.getMessage());

        NullPointerException noSearchText = assertThrows(NullPointerException.class,
                () -> new CompactRequirementSearchView(trace.metadata(), trace.snapshot(), null,
                        new PageMetadata(0, 1, 0, false), List.of(), List.of(), List.of()));
        assertEquals("searchText", noSearchText.getMessage());
    }

    private ChangeContextResult changeContext(ChangeId changeId) {
        return new ChangeContextQueryService(world.snapshots(), world.contentStore(), world.requirementStore(),
                world.traceabilityStore(), world.referenceStore())
                .active(world.projectId(), changeId, 1, Set.of()).orElseThrow();
    }

    private static CompactChangeContextView withChangeAndTruncation(
            CompactChangeContextView view, String changeId, Optional<String> reason, boolean truncated) {
        return new CompactChangeContextView(view.metadata(), view.snapshot(), changeId, view.change(),
                view.affectedRequirementLinks(), view.affectedRequirements(), view.constraints(),
                view.designDecisions(), view.implementationTasks(), view.nodes(), view.links(),
                view.externalReferences(), view.evidence(), view.warnings(), reason, truncated);
    }

    private TraceabilityLink external(TraceabilityEntityRef root, ExternalReference reference) {
        return world.link(root, TraceabilityRelationType.IMPLEMENTS,
                ref(TraceabilityEntityKind.EXTERNAL_REFERENCE, reference.id().value()));
    }

    private ExternalReference transitioned(
            ExternalReference reference,
            ExternalReferenceResolutionState state,
            ExternalReferenceResolutionReason reason) {
        Optional<ResolvedExternalTarget> target = state == ExternalReferenceResolutionState.RESOLVED
                ? Optional.of(new ResolvedExternalTarget(reference.target(), Map.of("name", "Owner")))
                : Optional.empty();
        ExternalReference next = reference.transition(state, reason, target, PublishedSnapshotWorld.CREATED_AT);
        world.replace(next);
        return next;
    }

    private ProvenanceView provenanceView() {
        return new ProvenanceView("world-provider", Optional.of("1"), "file:specs/world.md", Optional.of("external"),
                Optional.of("rev-1"), world.definition().id().toString());
    }

    private static EvidenceView evidenceView(Evidence evidence) {
        return new EvidenceView(evidence.id().toString(), evidence.source().toString(),
                Optional.of(new SourceRangeView(1, 3)), evidence.excerptHash());
    }
}
