package com.morpheus.application.traceability;

import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.reference.ExternalReferenceTarget;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The trace of one current requirement in a published snapshot, with its external links explained, never hidden. */
class TraceRequirementServiceTest {
    private static final Instant T0 = Instant.parse("2026-10-30T12:00:00Z");
    private static final Set<TraceabilityRelationType> ALL = Set.copyOf(EnumSet.allOf(TraceabilityRelationType.class));
    private static final EvidenceId EVIDENCE = EvidenceId.generate();

    private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
    private final Snapshots snapshots = new Snapshots();
    private final Requirements requirements = new Requirements();
    private final Links links = new Links();
    private final References references = new References();
    private final TraceRequirementService service =
            new TraceRequirementService(snapshots, requirements, links, references);

    private final KnowledgeSnapshotMetadata active = snapshots.add(KnowledgeSnapshotState.ACTIVE);
    private final Requirement requirement = new Requirement(RequirementId.generate(), SpecificationId.generate(),
            Optional.of("R-1"), "Traced", "The system traces", provenance());
    private final RequirementVersionRecord record = requirements.add(active.id(), requirement);
    private final TraceabilityEntityRef root =
            new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, requirement.id().value());
    private final ExternalReference resolvable = references.add(active.id(), requirement.id().value());
    private final TraceabilityLink toCode = links.add(root, TraceabilityRelationType.LINKS_TO_CODE,
            new TraceabilityEntityRef(TraceabilityEntityKind.EXTERNAL_REFERENCE, resolvable.id().value()));
    private final TraceabilityLink toMissingTest = links.add(root, TraceabilityRelationType.LINKS_TO_TEST,
            new TraceabilityEntityRef(TraceabilityEntityKind.EXTERNAL_REFERENCE, DomainIdentity.generate()));
    private final TraceabilityLink refines = links.add(root, TraceabilityRelationType.REFINES,
            new TraceabilityEntityRef(TraceabilityEntityKind.SPECIFICATION, requirement.specificationId().value()));

    @Test
    void theActiveTraceIsRootedAtTheRequirementAndExplainsEveryExternalLinkInIdentityOrder() {
        TraceRequirementResult result = service.traceActive(projectId, requirement.id(), 2, ALL).orElseThrow();

        assertEquals(active, result.snapshot());
        assertEquals(record, result.requirement());
        assertEquals(root, result.subgraph().start());
        assertEquals(Set.of(toCode, toMissingTest, refines), Set.copyOf(result.subgraph().links()));

        List<TraceabilityLink> externalInOrder = new ArrayList<>(List.of(toCode, toMissingTest));
        externalInOrder.sort(Comparator.comparing(TraceabilityLink::id));
        assertEquals(externalInOrder, result.externalLinks().stream().map(ExternalTraceabilityView::link).toList());

        ExternalTraceabilityView code = view(result, toCode);
        assertEquals(ExternalTraceabilityAvailability.REFERENCE_UNVALIDATED, code.availability());
        assertEquals(Optional.of(resolvable), code.reference());
        ExternalTraceabilityView broken = view(result, toMissingTest);
        assertEquals(ExternalTraceabilityAvailability.BROKEN_REFERENCE, broken.availability(),
                "a link whose reference is missing is reported broken, not dropped");
        assertTrue(broken.reference().isEmpty());
    }

    @Test
    void theRelationFilterBoundsTheSubgraphAndItsExternalLinks() {
        TraceRequirementResult result = service.traceActive(
                projectId, requirement.id(), 1, Set.of(TraceabilityRelationType.LINKS_TO_CODE)).orElseThrow();

        assertEquals(List.of(toCode), result.subgraph().links());
        assertEquals(List.of(toCode), result.externalLinks().stream().map(ExternalTraceabilityView::link).toList());
    }

    @Test
    void noActiveSnapshotOrNoCurrentRequirementIsAnEmptyTraceRatherThanAFailure() {
        assertTrue(service.traceActive(ProjectSpecificationId.generate(), requirement.id(), 1, ALL).isEmpty());
        assertTrue(service.traceActive(projectId, RequirementId.generate(), 1, ALL).isEmpty());
    }

    @Test
    void aSnapshotTraceRequiresAKnownPublishedSnapshot() {
        KnowledgeSnapshotId unknown = KnowledgeSnapshotId.generate();
        KnowledgeStoreException missing = assertThrows(KnowledgeStoreException.class,
                () -> service.traceSnapshot(unknown, requirement.id(), 1, ALL));
        assertEquals("unknown knowledge snapshot: " + unknown, missing.getMessage());

        KnowledgeSnapshotMetadata building = snapshots.add(KnowledgeSnapshotState.BUILDING);
        KnowledgeStoreException unpublished = assertThrows(KnowledgeStoreException.class,
                () -> service.traceSnapshot(building.id(), requirement.id(), 1, ALL));
        assertEquals("trace(requirement) requires an ACTIVE or RETIRED snapshot: " + building.id() + " is BUILDING",
                unpublished.getMessage());

        KnowledgeSnapshotMetadata retired = snapshots.add(KnowledgeSnapshotState.RETIRED);
        requirements.add(retired.id(), requirement);
        TraceRequirementResult result = service.traceSnapshot(retired.id(), requirement.id(), 1, ALL).orElseThrow();
        assertEquals(retired, result.snapshot());
        assertTrue(result.externalLinks().isEmpty(), "links are scoped to the snapshot that holds them");

        assertEquals(active, service.traceSnapshot(active.id(), requirement.id(), 1, ALL).orElseThrow().snapshot());
    }

    @Test
    void aDepthThatIsNotPositiveIsRefusedOnBothEntries() {
        IllegalArgumentException activeDepth = assertThrows(IllegalArgumentException.class,
                () -> service.traceActive(projectId, requirement.id(), 0, ALL));
        assertEquals("maxDepth must be greater than zero", activeDepth.getMessage());
        IllegalArgumentException snapshotDepth = assertThrows(IllegalArgumentException.class,
                () -> service.traceSnapshot(active.id(), requirement.id(), -1, ALL));
        assertEquals("maxDepth must be greater than zero", snapshotDepth.getMessage());
    }

    @Test
    void aResultRefusesAnOccurrenceFromAnotherSnapshotAWrongRootAndAForeignExternalLink() {
        TraceRequirementResult valid = service.traceActive(projectId, requirement.id(), 1, ALL).orElseThrow();
        KnowledgeSnapshotMetadata other = snapshots.add(KnowledgeSnapshotState.RETIRED);

        IllegalArgumentException foreignSnapshot = assertThrows(IllegalArgumentException.class,
                () -> new TraceRequirementResult(other, record, valid.subgraph(), List.of()));
        assertEquals("requirement occurrence must belong to the result snapshot", foreignSnapshot.getMessage());

        TraceabilityEntityRef elsewhere = new TraceabilityEntityRef(
                TraceabilityEntityKind.REQUIREMENT, DomainIdentity.generate());
        IllegalArgumentException wrongRoot = assertThrows(IllegalArgumentException.class,
                () -> new TraceRequirementResult(active, record,
                        new TraceabilitySubgraph(elsewhere, List.of(elsewhere), List.of(), Optional.empty()), List.of()));
        assertEquals("trace subgraph must be rooted at the requirement identity", wrongRoot.getMessage());

        TraceabilitySubgraph rootOnly = new TraceabilitySubgraph(root, List.of(root), List.of(), Optional.empty());
        IllegalArgumentException foreignLink = assertThrows(IllegalArgumentException.class,
                () -> new TraceRequirementResult(active, record, rootOnly, valid.externalLinks()));
        assertEquals("external traceability views must belong to the returned subgraph", foreignLink.getMessage());
    }

    @Test
    void anExternalViewKeepsItsTargetItsIdentityAndItsAvailabilityConsistent() {
        Optional<ExternalReference> reference = Optional.of(resolvable);

        IllegalArgumentException notExternal = assertThrows(IllegalArgumentException.class,
                () -> new ExternalTraceabilityView(refines, Optional.empty(), ExternalTraceabilityAvailability.BROKEN_REFERENCE));
        assertEquals("external traceability view requires an EXTERNAL_REFERENCE target", notExternal.getMessage());

        IllegalArgumentException otherIdentity = assertThrows(IllegalArgumentException.class,
                () -> new ExternalTraceabilityView(toMissingTest, reference, ExternalTraceabilityAvailability.REFERENCE_RESOLVED));
        assertEquals("external reference identity must match link target", otherIdentity.getMessage());

        IllegalArgumentException brokenWithReference = assertThrows(IllegalArgumentException.class,
                () -> new ExternalTraceabilityView(toCode, reference, ExternalTraceabilityAvailability.BROKEN_REFERENCE));
        assertEquals("BROKEN_REFERENCE must not contain an external reference", brokenWithReference.getMessage());

        IllegalArgumentException staleWithout = assertThrows(IllegalArgumentException.class,
                () -> new ExternalTraceabilityView(toCode, Optional.empty(), ExternalTraceabilityAvailability.REFERENCE_STALE));
        assertEquals("non-broken external traceability view requires a reference", staleWithout.getMessage());
    }

    private static ExternalTraceabilityView view(TraceRequirementResult result, TraceabilityLink link) {
        return result.externalLinks().stream().filter(item -> item.link().equals(link)).findFirst().orElseThrow();
    }

    private static Provenance provenance() {
        return new Provenance(new ProviderId("openspec"), Optional.of("1"), SourceLocator.file("specs/r.md"),
                Optional.of("R-1"), Optional.of("rev"), EVIDENCE);
    }

    private final class Snapshots implements SpecificationKnowledgeStore {
        private final Map<KnowledgeSnapshotId, KnowledgeSnapshotMetadata> byId = new LinkedHashMap<>();

        KnowledgeSnapshotMetadata add(KnowledgeSnapshotState state) {
            KnowledgeSnapshotMetadata snapshot = new KnowledgeSnapshotMetadata(KnowledgeSnapshotId.generate(),
                    projectId, Optional.empty(), state, Optional.of("rev"), T0);
            byId.put(snapshot.id(), snapshot);
            return snapshot;
        }

        @Override public Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId id) {
            return byId.values().stream()
                    .filter(snapshot -> snapshot.projectId().equals(id) && snapshot.state() == KnowledgeSnapshotState.ACTIVE)
                    .findFirst();
        }

        @Override public Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override public void putProject(ProjectStoreEntry project) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProjectStoreEntry> findProject(ProjectSpecificationId id) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator) { throw new UnsupportedOperationException(); }
        @Override public List<ProjectStoreEntry> listProjects() { throw new UnsupportedOperationException(); }
        @Override public void putSnapshot(KnowledgeSnapshotMetadata metadata) { throw new UnsupportedOperationException(); }
        @Override public KnowledgeSnapshotMetadata transitionSnapshotState(
                KnowledgeSnapshotId id, KnowledgeSnapshotState from, KnowledgeSnapshotState to) {
            throw new UnsupportedOperationException();
        }
        @Override public KnowledgeSnapshotMetadata activateSnapshot(
                KnowledgeSnapshotId id, Optional<KnowledgeSnapshotId> expected) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class Requirements implements VersionedRequirementStore {
        private final List<RequirementVersionRecord> records = new ArrayList<>();

        RequirementVersionRecord add(KnowledgeSnapshotId snapshotId, Requirement requirement) {
            RequirementVersionRecord record = new RequirementVersionRecord(snapshotId, new EntityVersion<>(
                    EntityVersionId.generate(), requirement.id().value(), SpecificationVersionId.generate(),
                    TemporalState.CURRENT, requirement));
            records.add(record);
            return record;
        }

        @Override public List<RequirementVersionRecord> listRequirementVersions(KnowledgeSnapshotId id) {
            return records.stream().filter(record -> record.snapshotId().equals(id)).toList();
        }

        @Override public Optional<RequirementVersionRecord> currentRequirement(KnowledgeSnapshotId id, DomainIdentity identity) {
            return listRequirementVersions(id).stream()
                    .filter(record -> record.entityVersion().entityIdentity().equals(identity)).findFirst();
        }

        @Override public void putSpecificationVersion(SpecificationVersion version) { throw new UnsupportedOperationException(); }
        @Override public Optional<SpecificationVersion> findSpecificationVersion(SpecificationVersionId versionId) { throw new UnsupportedOperationException(); }
        @Override public void bindSnapshotVersion(SnapshotSpecificationVersionBinding binding) { throw new UnsupportedOperationException(); }
        @Override public Optional<SnapshotSpecificationVersionBinding> findSnapshotVersion(KnowledgeSnapshotId id) { throw new UnsupportedOperationException(); }
        @Override public void putRequirementVersion(RequirementVersionRecord record) { throw new UnsupportedOperationException(); }
        @Override public Optional<RequirementVersionRecord> findRequirementVersion(EntityVersionId entityVersionId) { throw new UnsupportedOperationException(); }
    }

    private final class Links implements TraceabilityStore {
        private final List<TraceabilityLink> links = new ArrayList<>();

        TraceabilityLink add(TraceabilityEntityRef source, TraceabilityRelationType relation, TraceabilityEntityRef target) {
            TraceabilityLink link = new TraceabilityLink(TraceabilityLinkId.generate(), source, relation, target,
                    TraceabilityLinkOrigin.EXPLICIT, TraceabilityResolutionState.RESOLVED, Optional.empty(),
                    Set.of(EVIDENCE), T0);
            links.add(link);
            return link;
        }

        @Override public List<TraceabilityLink> outgoing(KnowledgeSnapshotId id, TraceabilityEntityRef source,
                Set<TraceabilityRelationType> relationTypes) {
            return scoped(id).filter(link -> link.source().equals(source) && relationTypes.contains(link.relationType()))
                    .toList();
        }

        @Override public List<TraceabilityLink> incoming(KnowledgeSnapshotId id, TraceabilityEntityRef target,
                Set<TraceabilityRelationType> relationTypes) {
            return scoped(id).filter(link -> link.target().equals(target) && relationTypes.contains(link.relationType()))
                    .toList();
        }

        private java.util.stream.Stream<TraceabilityLink> scoped(KnowledgeSnapshotId id) {
            return id.equals(active.id()) ? links.stream() : java.util.stream.Stream.empty();
        }

        @Override public void putLink(KnowledgeSnapshotId id, TraceabilityLink link) { throw new UnsupportedOperationException(); }
        @Override public void putLinks(KnowledgeSnapshotId id, List<TraceabilityLink> batch) { throw new UnsupportedOperationException(); }
        @Override public Optional<TraceabilityLink> findLink(KnowledgeSnapshotId id, TraceabilityLinkId linkId) { throw new UnsupportedOperationException(); }
    }

    private static final class References implements ExternalReferenceStore {
        private final Map<String, ExternalReference> byKey = new LinkedHashMap<>();

        ExternalReference add(KnowledgeSnapshotId snapshotId, DomainIdentity owner) {
            ExternalReference reference = ExternalReference.unvalidated(ExternalReferenceId.generate(), owner,
                    new ExternalReferenceTarget("MINOS", Optional.of("morpheus-engine"), "CODE_SYMBOL",
                            "com.morpheus.Sample", Optional.of("rev-1")),
                    Optional.empty());
            byKey.put(snapshotId + "/" + reference.id(), reference);
            return reference;
        }

        @Override public Optional<ExternalReference> findReference(KnowledgeSnapshotId id, ExternalReferenceId referenceId) {
            return Optional.ofNullable(byKey.get(id + "/" + referenceId));
        }

        @Override public void putReference(KnowledgeSnapshotId id, ExternalReference reference) { throw new UnsupportedOperationException(); }
        @Override public List<ExternalReference> findByOwner(KnowledgeSnapshotId id, DomainIdentity ownerId) { throw new UnsupportedOperationException(); }
    }
}
