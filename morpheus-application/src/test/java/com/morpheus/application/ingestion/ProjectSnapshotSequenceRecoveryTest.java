package com.morpheus.application.ingestion;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectSnapshotSequenceRecoveryTest {
    @Test
    void nextSequenceIncludesFailedDurableCandidatesButKeepsPublishedPredecessorIndependent() throws Exception {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId activeSnapshot = KnowledgeSnapshotId.generate();
        KnowledgeSnapshotId failedSnapshot = KnowledgeSnapshotId.generate();
        SpecificationVersion activeVersion = version(projectId, 1L, Optional.empty());
        SpecificationVersion failedVersion = version(projectId, 2L, Optional.of(activeVersion.id()));

        SnapshotStore snapshots = new SnapshotStore(List.of(
                snapshot(activeSnapshot, projectId, KnowledgeSnapshotState.ACTIVE),
                snapshot(failedSnapshot, projectId, KnowledgeSnapshotState.FAILED)));
        VersionStore versions = new VersionStore();
        versions.bind(activeSnapshot, activeVersion);
        versions.bind(failedSnapshot, failedVersion);

        ProjectSnapshotImportService service = new ProjectSnapshotImportService(
                snapshots, versions, new EmptyContentStore(), new EmptyTraceabilityStore());
        Method nextSequence = ProjectSnapshotImportService.class.getDeclaredMethod(
                "nextSequence", ProjectSpecificationId.class, Optional.class);
        nextSequence.setAccessible(true);

        long result = (long) nextSequence.invoke(service, projectId, Optional.of(activeVersion));

        assertEquals(3L, result);
    }

    private SpecificationVersion version(
            ProjectSpecificationId projectId,
            long sequence,
            Optional<SpecificationVersionId> predecessor) {
        return new SpecificationVersion(
                SpecificationVersionId.generate(), projectId, Optional.of(sequence), Optional.empty(), Optional.empty(),
                Instant.parse("2026-08-19T18:00:00Z").plusSeconds(sequence), predecessor);
    }

    private KnowledgeSnapshotMetadata snapshot(
            KnowledgeSnapshotId id,
            ProjectSpecificationId projectId,
            KnowledgeSnapshotState state) {
        return new KnowledgeSnapshotMetadata(
                id, projectId, Optional.empty(), state, Optional.empty(), Instant.parse("2026-08-19T18:00:00Z"));
    }

    private static final class SnapshotStore implements SpecificationKnowledgeStore {
        private final List<KnowledgeSnapshotMetadata> snapshots;
        private SnapshotStore(List<KnowledgeSnapshotMetadata> snapshots) { this.snapshots = snapshots; }
        @Override public void putProject(ProjectStoreEntry project) { }
        @Override public Optional<ProjectStoreEntry> findProject(ProjectSpecificationId projectId) { return Optional.empty(); }
        @Override public Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator) { return Optional.empty(); }
        @Override public List<ProjectStoreEntry> listProjects() { return List.of(); }
        @Override public void putSnapshot(KnowledgeSnapshotMetadata snapshot) { throw new UnsupportedOperationException(); }
        @Override public Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId snapshotId) { return Optional.empty(); }
        @Override public List<KnowledgeSnapshotMetadata> listSnapshots(ProjectSpecificationId projectId) { return snapshots; }
        @Override public Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId projectId) { return Optional.empty(); }
        @Override public KnowledgeSnapshotMetadata transitionSnapshotState(KnowledgeSnapshotId id, KnowledgeSnapshotState from, KnowledgeSnapshotState to) { throw new UnsupportedOperationException(); }
        @Override public KnowledgeSnapshotMetadata activateSnapshot(KnowledgeSnapshotId id, Optional<KnowledgeSnapshotId> expected) { throw new UnsupportedOperationException(); }
    }

    private static final class VersionStore implements VersionedRequirementStore {
        private final Map<KnowledgeSnapshotId, SnapshotSpecificationVersionBinding> bindings = new HashMap<>();
        private final Map<SpecificationVersionId, SpecificationVersion> versions = new HashMap<>();
        private void bind(KnowledgeSnapshotId snapshotId, SpecificationVersion version) {
            versions.put(version.id(), version);
            bindings.put(snapshotId, new SnapshotSpecificationVersionBinding(snapshotId, version.id()));
        }
        @Override public void putSpecificationVersion(SpecificationVersion version) { throw new UnsupportedOperationException(); }
        @Override public Optional<SpecificationVersion> findSpecificationVersion(SpecificationVersionId versionId) { return Optional.ofNullable(versions.get(versionId)); }
        @Override public void bindSnapshotVersion(SnapshotSpecificationVersionBinding binding) { throw new UnsupportedOperationException(); }
        @Override public Optional<SnapshotSpecificationVersionBinding> findSnapshotVersion(KnowledgeSnapshotId snapshotId) { return Optional.ofNullable(bindings.get(snapshotId)); }
        @Override public void putRequirementVersion(RequirementVersionRecord record) { throw new UnsupportedOperationException(); }
        @Override public Optional<RequirementVersionRecord> findRequirementVersion(EntityVersionId entityVersionId) { return Optional.empty(); }
        @Override public List<RequirementVersionRecord> listRequirementVersions(KnowledgeSnapshotId snapshotId) { return List.of(); }
        @Override public Optional<RequirementVersionRecord> currentRequirement(KnowledgeSnapshotId snapshotId, DomainIdentity entityIdentity) { return Optional.empty(); }
    }

    private static final class EmptyContentStore implements SnapshotBusinessContentStore {
        @Override public void putSnapshotContent(SnapshotBusinessContent content) { throw new UnsupportedOperationException(); }
        @Override public Optional<SnapshotBusinessContent> findSnapshotContent(KnowledgeSnapshotId snapshotId) { return Optional.empty(); }
    }

    private static final class EmptyTraceabilityStore implements TraceabilityStore {
        @Override public void putLink(KnowledgeSnapshotId snapshotId, TraceabilityLink link) { throw new UnsupportedOperationException(); }
        @Override public Optional<TraceabilityLink> findLink(KnowledgeSnapshotId snapshotId, TraceabilityLinkId linkId) { return Optional.empty(); }
        @Override public List<TraceabilityLink> outgoing(KnowledgeSnapshotId snapshotId, TraceabilityEntityRef source, Set<TraceabilityRelationType> relationTypes) { return List.of(); }
        @Override public List<TraceabilityLink> incoming(KnowledgeSnapshotId snapshotId, TraceabilityEntityRef target, Set<TraceabilityRelationType> relationTypes) { return List.of(); }
    }
}
