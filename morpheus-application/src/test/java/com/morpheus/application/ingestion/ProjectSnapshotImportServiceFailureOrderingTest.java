package com.morpheus.application.ingestion;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecification;
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

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectSnapshotImportServiceFailureOrderingTest {

    @Test
    void snapshotRegistrationFailureCannotLeaveOrphanSpecificationVersion() {
        FailingSnapshotStore snapshots = new FailingSnapshotStore(false);
        RecordingVersionStore versions = new RecordingVersionStore();
        ProjectSnapshotImportService service = service(snapshots, versions);

        assertThrows(
                KnowledgeStoreException.class,
                () -> service.publishFull(content(), Optional.of("rev-1"), Instant.parse("2026-08-17T20:00:00Z")));

        assertFalse(versions.versionWritten,
                "specification version must not be persisted when the candidate snapshot cannot be registered");
        assertEquals(0, snapshots.snapshots.size());
    }

    @Test
    void partiallyPersistedCandidateIsMarkedFailedBeforeVersionPersistence() {
        FailingSnapshotStore snapshots = new FailingSnapshotStore(true);
        RecordingVersionStore versions = new RecordingVersionStore();
        ProjectSnapshotImportService service = service(snapshots, versions);

        assertThrows(
                KnowledgeStoreException.class,
                () -> service.publishFull(content(), Optional.of("rev-2"), Instant.parse("2026-08-17T20:00:00Z")));

        assertFalse(versions.versionWritten,
                "version persistence must remain after successful candidate registration");
        KnowledgeSnapshotMetadata candidate = snapshots.snapshots.values().stream().findFirst().orElseThrow();
        assertEquals(KnowledgeSnapshotState.FAILED, candidate.state(),
                "a candidate durably written before registration failure must be recoverably FAILED");
    }

    @Test
    void durableSequenceIsAllocatedOnlyAfterCandidateRegistrationAndIsConsumedBeforeBindingFailure() {
        RegisteringSnapshotStore snapshots = new RegisteringSnapshotStore();
        SequenceVersionStore versions = new SequenceVersionStore(7L);
        ProjectSnapshotImportService service = service(snapshots, versions);

        assertThrows(
                KnowledgeStoreException.class,
                () -> service.publishFull(content(), Optional.of("rev-7"), Instant.parse("2026-08-17T20:00:00Z")));

        SpecificationVersion captured = versions.capturedVersion.orElseThrow();
        assertEquals(Optional.of(7L), captured.sequence());
        assertEquals(1, snapshots.snapshots.size(), "candidate must exist before durable sequence allocation");
        assertEquals(
                KnowledgeSnapshotState.FAILED,
                snapshots.snapshots.values().stream().findFirst().orElseThrow().state(),
                "binding failure must leave the durable candidate explicitly FAILED");
    }

    @Test
    void unsupportedDurableSequencePortFailsClosedAndMarksRegisteredCandidateFailed() {
        RegisteringSnapshotStore snapshots = new RegisteringSnapshotStore();
        DefaultSequenceVersionStore versions = new DefaultSequenceVersionStore();
        ProjectSnapshotImportService service = service(snapshots, versions);

        KnowledgeStoreException failure = assertThrows(
                KnowledgeStoreException.class,
                () -> service.publishFull(content(), Optional.of("rev-default"), Instant.parse("2026-08-17T20:00:00Z")));

        assertEquals(
                "versioned requirement store does not support durable specification-version sequence allocation",
                failure.getMessage());
        assertFalse(versions.versionWritten);
        assertEquals(
                KnowledgeSnapshotState.FAILED,
                snapshots.snapshots.values().stream().findFirst().orElseThrow().state());
    }

    private ProjectSnapshotImportService service(
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore versions) {
        return new ProjectSnapshotImportService(
                snapshots,
                versions,
                new EmptyContentStore(),
                new EmptyTraceabilityStore());
    }

    private NormalizedProjectContent content() {
        ProjectSpecification project = new ProjectSpecification(
                ProjectSpecificationId.generate(),
                "failure-ordering",
                SourceLocator.file("workspace"));
        return new NormalizedProjectContent(
                project,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static final class FailingSnapshotStore implements SpecificationKnowledgeStore {
        private final boolean persistBeforeFailure;
        private final Map<KnowledgeSnapshotId, KnowledgeSnapshotMetadata> snapshots = new HashMap<>();

        private FailingSnapshotStore(boolean persistBeforeFailure) {
            this.persistBeforeFailure = persistBeforeFailure;
        }

        @Override
        public void putProject(ProjectStoreEntry project) {
        }

        @Override
        public Optional<ProjectStoreEntry> findProject(ProjectSpecificationId projectId) {
            return Optional.empty();
        }

        @Override
        public Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator) {
            return Optional.empty();
        }

        @Override
        public List<ProjectStoreEntry> listProjects() {
            return List.of();
        }

        @Override
        public void putSnapshot(KnowledgeSnapshotMetadata snapshot) {
            if (persistBeforeFailure) snapshots.put(snapshot.id(), snapshot);
            throw new KnowledgeStoreException("injected snapshot registration failure");
        }

        @Override
        public Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId snapshotId) {
            return Optional.ofNullable(snapshots.get(snapshotId));
        }

        @Override
        public Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId projectId) {
            return Optional.empty();
        }

        @Override
        public KnowledgeSnapshotMetadata transitionSnapshotState(
                KnowledgeSnapshotId snapshotId,
                KnowledgeSnapshotState expectedState,
                KnowledgeSnapshotState targetState) {
            KnowledgeSnapshotMetadata current = snapshots.get(snapshotId);
            if (current == null || current.state() != expectedState) {
                throw new KnowledgeStoreException("unexpected snapshot state in failure fixture");
            }
            KnowledgeSnapshotMetadata updated = new KnowledgeSnapshotMetadata(
                    current.id(),
                    current.projectId(),
                    current.predecessorId(),
                    targetState,
                    current.sourceRevision(),
                    current.createdAt());
            snapshots.put(snapshotId, updated);
            return updated;
        }

        @Override
        public KnowledgeSnapshotMetadata activateSnapshot(
                KnowledgeSnapshotId snapshotId,
                Optional<KnowledgeSnapshotId> expectedActiveSnapshotId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RegisteringSnapshotStore implements SpecificationKnowledgeStore {
        private final Map<KnowledgeSnapshotId, KnowledgeSnapshotMetadata> snapshots = new HashMap<>();

        @Override
        public void putProject(ProjectStoreEntry project) {
        }

        @Override
        public Optional<ProjectStoreEntry> findProject(ProjectSpecificationId projectId) {
            return Optional.empty();
        }

        @Override
        public Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator) {
            return Optional.empty();
        }

        @Override
        public List<ProjectStoreEntry> listProjects() {
            return List.of();
        }

        @Override
        public void putSnapshot(KnowledgeSnapshotMetadata snapshot) {
            snapshots.put(snapshot.id(), snapshot);
        }

        @Override
        public Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId snapshotId) {
            return Optional.ofNullable(snapshots.get(snapshotId));
        }

        @Override
        public Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId projectId) {
            return Optional.empty();
        }

        @Override
        public KnowledgeSnapshotMetadata transitionSnapshotState(
                KnowledgeSnapshotId snapshotId,
                KnowledgeSnapshotState expectedState,
                KnowledgeSnapshotState targetState) {
            KnowledgeSnapshotMetadata current = snapshots.get(snapshotId);
            if (current == null || current.state() != expectedState) {
                throw new KnowledgeStoreException("unexpected snapshot state in registering fixture");
            }
            KnowledgeSnapshotMetadata updated = new KnowledgeSnapshotMetadata(
                    current.id(),
                    current.projectId(),
                    current.predecessorId(),
                    targetState,
                    current.sourceRevision(),
                    current.createdAt());
            snapshots.put(snapshotId, updated);
            return updated;
        }

        @Override
        public KnowledgeSnapshotMetadata activateSnapshot(
                KnowledgeSnapshotId snapshotId,
                Optional<KnowledgeSnapshotId> expectedActiveSnapshotId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingVersionStore implements VersionedRequirementStore {
        private boolean versionWritten;

        @Override
        public void putSpecificationVersion(SpecificationVersion version) {
            versionWritten = true;
        }

        @Override
        public Optional<SpecificationVersion> findSpecificationVersion(SpecificationVersionId versionId) {
            return Optional.empty();
        }

        @Override
        public void bindSnapshotVersion(SnapshotSpecificationVersionBinding binding) {
            throw new AssertionError("binding must not occur after injected registration failure");
        }

        @Override
        public Optional<SnapshotSpecificationVersionBinding> findSnapshotVersion(KnowledgeSnapshotId snapshotId) {
            return Optional.empty();
        }

        @Override
        public void putRequirementVersion(RequirementVersionRecord record) {
            throw new AssertionError("requirements must not be persisted after injected registration failure");
        }

        @Override
        public Optional<RequirementVersionRecord> findRequirementVersion(EntityVersionId entityVersionId) {
            return Optional.empty();
        }

        @Override
        public List<RequirementVersionRecord> listRequirementVersions(KnowledgeSnapshotId snapshotId) {
            return List.of();
        }

        @Override
        public Optional<RequirementVersionRecord> currentRequirement(
                KnowledgeSnapshotId snapshotId,
                DomainIdentity entityIdentity) {
            return Optional.empty();
        }
    }

    private static final class SequenceVersionStore implements VersionedRequirementStore {
        private final long nextSequence;
        private Optional<SpecificationVersion> capturedVersion = Optional.empty();

        private SequenceVersionStore(long nextSequence) {
            this.nextSequence = nextSequence;
        }

        @Override
        public long nextSpecificationVersionSequence(ProjectSpecificationId projectId) {
            return nextSequence;
        }

        @Override
        public void putSpecificationVersion(SpecificationVersion version) {
            capturedVersion = Optional.of(version);
        }

        @Override
        public Optional<SpecificationVersion> findSpecificationVersion(SpecificationVersionId versionId) {
            return capturedVersion.filter(version -> version.id().equals(versionId));
        }

        @Override
        public void bindSnapshotVersion(SnapshotSpecificationVersionBinding binding) {
            throw new KnowledgeStoreException("injected binding failure");
        }

        @Override
        public Optional<SnapshotSpecificationVersionBinding> findSnapshotVersion(KnowledgeSnapshotId snapshotId) {
            return Optional.empty();
        }

        @Override
        public void putRequirementVersion(RequirementVersionRecord record) {
            throw new AssertionError("requirements must not be persisted after injected binding failure");
        }

        @Override
        public Optional<RequirementVersionRecord> findRequirementVersion(EntityVersionId entityVersionId) {
            return Optional.empty();
        }

        @Override
        public List<RequirementVersionRecord> listRequirementVersions(KnowledgeSnapshotId snapshotId) {
            return List.of();
        }

        @Override
        public Optional<RequirementVersionRecord> currentRequirement(
                KnowledgeSnapshotId snapshotId,
                DomainIdentity entityIdentity) {
            return Optional.empty();
        }
    }

    private static final class DefaultSequenceVersionStore implements VersionedRequirementStore {
        private boolean versionWritten;

        @Override
        public void putSpecificationVersion(SpecificationVersion version) {
            versionWritten = true;
        }

        @Override
        public Optional<SpecificationVersion> findSpecificationVersion(SpecificationVersionId versionId) {
            return Optional.empty();
        }

        @Override
        public void bindSnapshotVersion(SnapshotSpecificationVersionBinding binding) {
            throw new AssertionError("binding must not occur when sequence allocation is unsupported");
        }

        @Override
        public Optional<SnapshotSpecificationVersionBinding> findSnapshotVersion(KnowledgeSnapshotId snapshotId) {
            return Optional.empty();
        }

        @Override
        public void putRequirementVersion(RequirementVersionRecord record) {
            throw new AssertionError("requirements must not be persisted when sequence allocation is unsupported");
        }

        @Override
        public Optional<RequirementVersionRecord> findRequirementVersion(EntityVersionId entityVersionId) {
            return Optional.empty();
        }

        @Override
        public List<RequirementVersionRecord> listRequirementVersions(KnowledgeSnapshotId snapshotId) {
            return List.of();
        }

        @Override
        public Optional<RequirementVersionRecord> currentRequirement(
                KnowledgeSnapshotId snapshotId,
                DomainIdentity entityIdentity) {
            return Optional.empty();
        }
    }

    private static final class EmptyContentStore implements SnapshotBusinessContentStore {
        @Override
        public void putSnapshotContent(SnapshotBusinessContent content) {
            throw new AssertionError("content must not be persisted after injected registration failure");
        }

        @Override
        public Optional<SnapshotBusinessContent> findSnapshotContent(KnowledgeSnapshotId snapshotId) {
            return Optional.empty();
        }
    }

    private static final class EmptyTraceabilityStore implements TraceabilityStore {
        @Override
        public void putLink(KnowledgeSnapshotId snapshotId, TraceabilityLink link) {
            throw new AssertionError("traceability must not be persisted after injected registration failure");
        }

        @Override
        public Optional<TraceabilityLink> findLink(KnowledgeSnapshotId snapshotId, TraceabilityLinkId linkId) {
            return Optional.empty();
        }

        @Override
        public List<TraceabilityLink> outgoing(
                KnowledgeSnapshotId snapshotId,
                TraceabilityEntityRef source,
                Set<TraceabilityRelationType> relationTypes) {
            return List.of();
        }

        @Override
        public List<TraceabilityLink> incoming(
                KnowledgeSnapshotId snapshotId,
                TraceabilityEntityRef target,
                Set<TraceabilityRelationType> relationTypes) {
            return List.of();
        }
    }
}
