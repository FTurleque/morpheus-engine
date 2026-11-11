package com.morpheus.application.temporal;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.version.EntityVersion;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CURRENT content is read through the ACTIVE snapshot only: an occurrence in any other snapshot is not current. */
class CurrentRequirementQueryServiceTest {
    private static final Instant T0 = Instant.parse("2026-10-30T12:00:00Z");

    private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
    private final Snapshots snapshots = new Snapshots();
    private final Requirements requirements = new Requirements();
    private final CurrentRequirementQueryService service = new CurrentRequirementQueryService(snapshots, requirements);
    private final Requirement requirement = new Requirement(RequirementId.generate(), SpecificationId.generate(),
            Optional.of("R-1"), "Current", "The statement", new Provenance(new ProviderId("openspec"),
            Optional.of("1"), SourceLocator.file("specs/r.md"), Optional.of("R-1"), Optional.of("rev"),
            EvidenceId.generate()));

    @Test
    void theCurrentRequirementIsTheOccurrenceOfTheActiveSnapshot() {
        KnowledgeSnapshotId activeId = KnowledgeSnapshotId.generate();
        snapshots.active = Optional.of(new KnowledgeSnapshotMetadata(activeId, projectId, Optional.empty(),
                KnowledgeSnapshotState.ACTIVE, Optional.of("rev"), T0));
        requirements.add(KnowledgeSnapshotId.generate(), requirement);
        RequirementVersionRecord current = requirements.add(activeId, requirement);

        assertEquals(Optional.of(current), service.current(projectId, requirement.id().value()));
        assertTrue(service.current(projectId, DomainIdentity.generate()).isEmpty(),
                "an identity absent from the active snapshot has no current content");
    }

    @Test
    void aProjectWithoutActiveSnapshotHasNoCurrentRequirementEvenWhenAnotherSnapshotHoldsIt() {
        requirements.add(KnowledgeSnapshotId.generate(), requirement);

        assertTrue(service.current(projectId, requirement.id().value()).isEmpty());
        assertTrue(requirements.reads.isEmpty(), "no requirement is read without an active snapshot");
    }

    @Test
    void aMissingArgumentIsRefusedByName() {
        NullPointerException project = assertThrows(NullPointerException.class,
                () -> service.current(null, requirement.id().value()));
        assertEquals("projectId", project.getMessage());
        NullPointerException entity = assertThrows(NullPointerException.class, () -> service.current(projectId, null));
        assertEquals("entityIdentity", entity.getMessage());
        NullPointerException store = assertThrows(NullPointerException.class,
                () -> new CurrentRequirementQueryService(snapshots, null));
        assertEquals("requirementStore", store.getMessage());
    }

    private static final class Snapshots implements SpecificationKnowledgeStore {
        private Optional<KnowledgeSnapshotMetadata> active = Optional.empty();

        @Override public Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId id) {
            return active.filter(snapshot -> snapshot.projectId().equals(id));
        }

        @Override public void putProject(ProjectStoreEntry project) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProjectStoreEntry> findProject(ProjectSpecificationId id) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator) { throw new UnsupportedOperationException(); }
        @Override public List<ProjectStoreEntry> listProjects() { throw new UnsupportedOperationException(); }
        @Override public void putSnapshot(KnowledgeSnapshotMetadata metadata) { throw new UnsupportedOperationException(); }
        @Override public Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId id) { throw new UnsupportedOperationException(); }
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
        private final List<KnowledgeSnapshotId> reads = new ArrayList<>();

        RequirementVersionRecord add(KnowledgeSnapshotId snapshotId, Requirement requirement) {
            RequirementVersionRecord record = new RequirementVersionRecord(snapshotId, new EntityVersion<>(
                    EntityVersionId.generate(), requirement.id().value(), SpecificationVersionId.generate(),
                    TemporalState.CURRENT, requirement));
            records.add(record);
            return record;
        }

        @Override public Optional<RequirementVersionRecord> currentRequirement(KnowledgeSnapshotId id, DomainIdentity identity) {
            reads.add(id);
            return records.stream()
                    .filter(record -> record.snapshotId().equals(id)
                            && record.entityVersion().entityIdentity().equals(identity))
                    .findFirst();
        }

        @Override public List<RequirementVersionRecord> listRequirementVersions(KnowledgeSnapshotId id) { throw new UnsupportedOperationException(); }
        @Override public void putSpecificationVersion(SpecificationVersion version) { throw new UnsupportedOperationException(); }
        @Override public Optional<SpecificationVersion> findSpecificationVersion(SpecificationVersionId versionId) { throw new UnsupportedOperationException(); }
        @Override public void bindSnapshotVersion(SnapshotSpecificationVersionBinding binding) { throw new UnsupportedOperationException(); }
        @Override public Optional<SnapshotSpecificationVersionBinding> findSnapshotVersion(KnowledgeSnapshotId id) { throw new UnsupportedOperationException(); }
        @Override public void putRequirementVersion(RequirementVersionRecord record) { throw new UnsupportedOperationException(); }
        @Override public Optional<RequirementVersionRecord> findRequirementVersion(EntityVersionId entityVersionId) { throw new UnsupportedOperationException(); }
    }
}
