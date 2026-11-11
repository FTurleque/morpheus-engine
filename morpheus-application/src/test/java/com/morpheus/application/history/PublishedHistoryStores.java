package com.morpheus.application.history;

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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hand-written snapshot and requirement ports for the published-history services: the application module cannot
 * depend on the memory store. Only what these services read is implemented.
 */
final class PublishedHistoryStores implements SpecificationKnowledgeStore, VersionedRequirementStore {
    static final Instant T0 = Instant.parse("2026-10-30T09:00:00Z");
    static final Provenance PROVENANCE = new Provenance(
            new ProviderId("test-provider"),
            Optional.of("1"),
            SourceLocator.file("specs/history.md"),
            Optional.empty(),
            Optional.of("rev-1"),
            EvidenceId.generate());

    private final Map<KnowledgeSnapshotId, KnowledgeSnapshotMetadata> snapshots = new LinkedHashMap<>();
    private final Map<ProjectSpecificationId, KnowledgeSnapshotId> active = new LinkedHashMap<>();
    private final Map<KnowledgeSnapshotId, SnapshotSpecificationVersionBinding> bindings = new LinkedHashMap<>();
    private final List<RequirementVersionRecord> records = new ArrayList<>();

    static DomainIdentity identity(int suffix) {
        return DomainIdentity.parse(String.format("01890f7a-36d4-7c1e-8000-%012d", suffix));
    }

    KnowledgeSnapshotMetadata publish(
            ProjectSpecificationId projectId,
            Optional<KnowledgeSnapshotId> predecessor,
            KnowledgeSnapshotState state) {
        return publishWithId(KnowledgeSnapshotId.generate(), projectId, predecessor, state);
    }

    KnowledgeSnapshotMetadata publishWithId(
            KnowledgeSnapshotId snapshotId,
            ProjectSpecificationId projectId,
            Optional<KnowledgeSnapshotId> predecessor,
            KnowledgeSnapshotState state) {
        KnowledgeSnapshotMetadata snapshot = new KnowledgeSnapshotMetadata(
                snapshotId, projectId, predecessor, state, Optional.of("rev"), T0);
        snapshots.put(snapshot.id(), snapshot);
        bindings.put(snapshot.id(),
                new SnapshotSpecificationVersionBinding(snapshot.id(), SpecificationVersionId.generate()));
        if (state == KnowledgeSnapshotState.ACTIVE) {
            active.put(projectId, snapshot.id());
        }
        return snapshot;
    }

    void pointActiveAt(ProjectSpecificationId projectId, KnowledgeSnapshotId snapshotId) {
        active.put(projectId, snapshotId);
    }

    void unbind(KnowledgeSnapshotId snapshotId) {
        bindings.remove(snapshotId);
    }

    RequirementVersionRecord addRequirement(
            KnowledgeSnapshotId snapshotId,
            DomainIdentity identity,
            SpecificationId specificationId,
            String statement) {
        String suffix = identity.toString().substring(30);
        Requirement requirement = new Requirement(
                new RequirementId(identity),
                specificationId,
                Optional.of("REQ-" + suffix),
                "Requirement " + suffix,
                statement,
                PROVENANCE);
        RequirementVersionRecord record = new RequirementVersionRecord(snapshotId, new EntityVersion<>(
                EntityVersionId.generate(),
                identity,
                SpecificationVersionId.generate(),
                TemporalState.CURRENT,
                requirement));
        records.add(record);
        return record;
    }

    @Override
    public Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId snapshotId) {
        return Optional.ofNullable(snapshots.get(snapshotId));
    }

    @Override
    public Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId projectId) {
        return Optional.ofNullable(active.get(projectId)).map(snapshots::get);
    }

    @Override
    public Optional<SnapshotSpecificationVersionBinding> findSnapshotVersion(KnowledgeSnapshotId snapshotId) {
        return Optional.ofNullable(bindings.get(snapshotId));
    }

    @Override
    public List<RequirementVersionRecord> listRequirementVersions(KnowledgeSnapshotId snapshotId) {
        return records.stream().filter(record -> record.snapshotId().equals(snapshotId)).toList();
    }

    @Override
    public Optional<RequirementVersionRecord> currentRequirement(
            KnowledgeSnapshotId snapshotId,
            DomainIdentity entityIdentity) {
        return listCurrentRequirementVersions(snapshotId).stream()
                .filter(record -> record.entityVersion().entityIdentity().equals(entityIdentity))
                .findFirst();
    }

    @Override public void putProject(ProjectStoreEntry project) { throw new UnsupportedOperationException(); }
    @Override public Optional<ProjectStoreEntry> findProject(ProjectSpecificationId id) { throw new UnsupportedOperationException(); }
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
    @Override public void putSpecificationVersion(SpecificationVersion version) { throw new UnsupportedOperationException(); }
    @Override public Optional<SpecificationVersion> findSpecificationVersion(SpecificationVersionId versionId) { throw new UnsupportedOperationException(); }
    @Override public void bindSnapshotVersion(SnapshotSpecificationVersionBinding binding) { throw new UnsupportedOperationException(); }
    @Override public void putRequirementVersion(RequirementVersionRecord record) { throw new UnsupportedOperationException(); }
    @Override public Optional<RequirementVersionRecord> findRequirementVersion(EntityVersionId entityVersionId) { throw new UnsupportedOperationException(); }
}
