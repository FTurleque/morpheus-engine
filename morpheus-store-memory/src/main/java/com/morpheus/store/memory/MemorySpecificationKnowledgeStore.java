package com.morpheus.store.memory;

import com.morpheus.application.identity.EntityIdentityBinding;
import com.morpheus.application.identity.EntityIdentityKey;
import com.morpheus.application.identity.EntityIdentityStore;
import com.morpheus.application.identity.IdentityCollisionException;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotConflictException;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reference in-memory implementation of the local MORPHEUS storage contracts. */
public final class MemorySpecificationKnowledgeStore
        implements SpecificationKnowledgeStore, EntityIdentityStore, VersionedRequirementStore {
    private final Map<ProjectSpecificationId, ProjectStoreEntry> projects = new HashMap<>();
    private final Map<KnowledgeSnapshotId, KnowledgeSnapshotMetadata> snapshots = new HashMap<>();
    private final Map<EntityIdentityKey, DomainIdentity> entityIdentities = new HashMap<>();
    private final Map<SpecificationVersionId, SpecificationVersion> specificationVersions = new HashMap<>();
    private final Map<KnowledgeSnapshotId, SnapshotSpecificationVersionBinding> snapshotVersionBindings = new HashMap<>();
    private final Map<EntityVersionId, RequirementVersionRecord> requirementVersions = new HashMap<>();

    @Override
    public synchronized void putProject(ProjectStoreEntry project) {
        ProjectStoreEntry existing = projects.get(project.id());
        if (existing != null) {
            if (!existing.equals(project)) {
                throw new KnowledgeStoreException("project identity collision: " + project.id());
            }
            return;
        }

        findProjectByRoot(project.rootLocator()).ifPresent(rootOwner -> {
            throw new KnowledgeStoreException(
                    "project root already registered by another identity: " + rootOwner.id());
        });
        projects.put(project.id(), project);
    }

    @Override
    public synchronized Optional<ProjectStoreEntry> findProject(ProjectSpecificationId projectId) {
        return Optional.ofNullable(projects.get(projectId));
    }

    @Override
    public synchronized Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator) {
        return projects.values().stream()
                .filter(project -> project.rootLocator().equals(rootLocator))
                .findFirst();
    }

    @Override
    public synchronized List<ProjectStoreEntry> listProjects() {
        return projects.values().stream()
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .toList();
    }

    @Override
    public synchronized Optional<DomainIdentity> find(EntityIdentityKey key) {
        return Optional.ofNullable(entityIdentities.get(key));
    }

    @Override
    public synchronized void put(EntityIdentityBinding binding) {
        DomainIdentity existing = entityIdentities.get(binding.key());
        if (existing != null) {
            if (!existing.equals(binding.identity())) {
                throw new IdentityCollisionException(
                        "external identity key already belongs to another MORPHEUS identity: " + binding.key());
            }
            return;
        }
        entityIdentities.put(binding.key(), binding.identity());
    }

    @Override
    public synchronized void putSnapshot(KnowledgeSnapshotMetadata snapshot) {
        if (snapshot.state() == KnowledgeSnapshotState.ACTIVE
                || snapshot.state() == KnowledgeSnapshotState.RETIRED) {
            throw new KnowledgeStoreException("ACTIVE/RETIRED snapshots must be produced by activation lifecycle");
        }

        KnowledgeSnapshotMetadata existing = snapshots.get(snapshot.id());
        if (existing != null) {
            if (!existing.sameDefinitionAs(snapshot)) {
                throw new KnowledgeStoreException("snapshot identity collision: " + snapshot.id());
            }
            return;
        }

        validateSnapshotReferences(snapshot);
        snapshots.put(snapshot.id(), snapshot);
    }

    @Override
    public synchronized Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId snapshotId) {
        return Optional.ofNullable(snapshots.get(snapshotId));
    }

    @Override
    public synchronized List<KnowledgeSnapshotMetadata> listSnapshots(ProjectSpecificationId projectId) {
        return snapshots.values().stream()
                .filter(snapshot -> snapshot.projectId().equals(projectId))
                .sorted(java.util.Comparator.comparing(KnowledgeSnapshotMetadata::createdAt)
                        .thenComparing(KnowledgeSnapshotMetadata::id))
                .toList();
    }

    @Override
    public synchronized Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId projectId) {
        return snapshots.values().stream()
                .filter(snapshot -> snapshot.projectId().equals(projectId))
                .filter(snapshot -> snapshot.state() == KnowledgeSnapshotState.ACTIVE)
                .findFirst();
    }

    @Override
    public synchronized KnowledgeSnapshotMetadata transitionSnapshotState(
            KnowledgeSnapshotId snapshotId,
            KnowledgeSnapshotState expectedState,
            KnowledgeSnapshotState targetState) {
        rejectPublishedTargetState(targetState);
        KnowledgeSnapshotMetadata snapshot = snapshots.get(snapshotId);
        if (snapshot == null) {
            throw new KnowledgeStoreException("snapshot not found: " + snapshotId);
        }
        if (snapshot.state() != expectedState) {
            throw new SnapshotConflictException(
                    "snapshot state changed: expected " + expectedState + " but was " + snapshot.state());
        }
        KnowledgeSnapshotMetadata updated = snapshot.withState(targetState);
        snapshots.put(snapshotId, updated);
        return updated;
    }

    @Override
    public synchronized KnowledgeSnapshotMetadata activateSnapshot(
            KnowledgeSnapshotId snapshotId,
            Optional<KnowledgeSnapshotId> expectedActiveSnapshotId) {
        KnowledgeSnapshotMetadata target = snapshots.get(snapshotId);
        if (target == null) {
            throw new KnowledgeStoreException("snapshot not found: " + snapshotId);
        }

        Optional<KnowledgeSnapshotMetadata> active = activeSnapshot(target.projectId());
        if (target.state() == KnowledgeSnapshotState.ACTIVE) {
            if (active.map(KnowledgeSnapshotMetadata::id).equals(Optional.of(snapshotId))) {
                return target;
            }
            throw new SnapshotConflictException("active snapshot state is inconsistent for project " + target.projectId());
        }

        if (target.state() != KnowledgeSnapshotState.READY) {
            throw new SnapshotConflictException("only READY snapshots can be activated: " + snapshotId);
        }

        if (!target.predecessorId().equals(expectedActiveSnapshotId)) {
            throw new SnapshotConflictException("snapshot predecessor does not match expected active snapshot");
        }

        Optional<KnowledgeSnapshotId> currentActiveId = active.map(KnowledgeSnapshotMetadata::id);
        if (!currentActiveId.equals(expectedActiveSnapshotId)) {
            throw new SnapshotConflictException("active snapshot changed before activation");
        }

        active.ifPresent(current -> snapshots.put(
                current.id(), current.withState(KnowledgeSnapshotState.RETIRED)));
        KnowledgeSnapshotMetadata activated = target.withState(KnowledgeSnapshotState.ACTIVE);
        snapshots.put(snapshotId, activated);
        return activated;
    }

    @Override
    public synchronized void putSpecificationVersion(SpecificationVersion version) {
        if (!projects.containsKey(version.projectId())) {
            throw new KnowledgeStoreException("project not found for specification version: " + version.projectId());
        }
        version.predecessor().ifPresent(predecessorId -> {
            SpecificationVersion predecessor = specificationVersions.get(predecessorId);
            if (predecessor == null) {
                throw new KnowledgeStoreException("specification version predecessor not found: " + predecessorId);
            }
            if (!predecessor.projectId().equals(version.projectId())) {
                throw new KnowledgeStoreException("specification version predecessor belongs to another project");
            }
        });

        SpecificationVersion existing = specificationVersions.get(version.id());
        if (existing != null) {
            if (!existing.equals(version)) {
                throw new KnowledgeStoreException("specification version identity collision: " + version.id());
            }
            return;
        }
        specificationVersions.put(version.id(), version);
    }

    @Override
    public synchronized Optional<SpecificationVersion> findSpecificationVersion(SpecificationVersionId versionId) {
        return Optional.ofNullable(specificationVersions.get(versionId));
    }

    @Override
    public synchronized void bindSnapshotVersion(SnapshotSpecificationVersionBinding binding) {
        KnowledgeSnapshotMetadata snapshot = snapshots.get(binding.snapshotId());
        if (snapshot == null) {
            throw new KnowledgeStoreException("snapshot not found: " + binding.snapshotId());
        }
        SpecificationVersion version = specificationVersions.get(binding.specificationVersionId());
        if (version == null) {
            throw new KnowledgeStoreException("specification version not found: " + binding.specificationVersionId());
        }
        if (!snapshot.projectId().equals(version.projectId())) {
            throw new KnowledgeStoreException("snapshot and specification version belong to different projects");
        }

        SnapshotSpecificationVersionBinding existing = snapshotVersionBindings.get(binding.snapshotId());
        if (existing != null) {
            if (!existing.equals(binding)) {
                throw new KnowledgeStoreException("snapshot already bound to another specification version");
            }
            return;
        }
        snapshotVersionBindings.put(binding.snapshotId(), binding);
    }

    @Override
    public synchronized Optional<SnapshotSpecificationVersionBinding> findSnapshotVersion(KnowledgeSnapshotId snapshotId) {
        return Optional.ofNullable(snapshotVersionBindings.get(snapshotId));
    }

    @Override
    public synchronized void putRequirementVersion(RequirementVersionRecord record) {
        SnapshotSpecificationVersionBinding binding = snapshotVersionBindings.get(record.snapshotId());
        if (binding == null) {
            throw new KnowledgeStoreException("snapshot has no specification version binding: " + record.snapshotId());
        }
        if (!binding.specificationVersionId().equals(record.entityVersion().specificationVersionId())) {
            throw new KnowledgeStoreException("requirement version does not match snapshot specification version");
        }

        RequirementVersionRecord existing = requirementVersions.get(record.entityVersion().id());
        if (existing != null) {
            if (!existing.equals(record)) {
                throw new KnowledgeStoreException("entity version identity collision: " + record.entityVersion().id());
            }
            return;
        }

        if (record.entityVersion().temporalState() == TemporalState.CURRENT) {
            boolean duplicateCurrent = requirementVersions.values().stream()
                    .filter(candidate -> candidate.snapshotId().equals(record.snapshotId()))
                    .map(RequirementVersionRecord::entityVersion)
                    .anyMatch(candidate -> candidate.temporalState() == TemporalState.CURRENT
                            && candidate.entityIdentity().equals(record.entityVersion().entityIdentity()));
            if (duplicateCurrent) {
                throw new KnowledgeStoreException(
                        "multiple CURRENT requirement versions for the same identity in one snapshot");
            }
        }

        requirementVersions.put(record.entityVersion().id(), record);
    }

    @Override
    public synchronized Optional<RequirementVersionRecord> findRequirementVersion(EntityVersionId entityVersionId) {
        return Optional.ofNullable(requirementVersions.get(entityVersionId));
    }

    @Override
    public synchronized List<RequirementVersionRecord> listRequirementVersions(KnowledgeSnapshotId snapshotId) {
        return requirementVersions.values().stream()
                .filter(record -> record.snapshotId().equals(snapshotId))
                .sorted((left, right) -> left.entityVersion().id().compareTo(right.entityVersion().id()))
                .toList();
    }

    @Override
    public synchronized Optional<RequirementVersionRecord> currentRequirement(
            KnowledgeSnapshotId snapshotId,
            DomainIdentity entityIdentity) {
        return requirementVersions.values().stream()
                .filter(record -> record.snapshotId().equals(snapshotId))
                .filter(record -> record.entityVersion().entityIdentity().equals(entityIdentity))
                .filter(record -> record.entityVersion().temporalState() == TemporalState.CURRENT)
                .findFirst();
    }

    private void validateSnapshotReferences(KnowledgeSnapshotMetadata snapshot) {
        if (!projects.containsKey(snapshot.projectId())) {
            throw new KnowledgeStoreException("project not found: " + snapshot.projectId());
        }

        snapshot.predecessorId().ifPresent(predecessorId -> {
            KnowledgeSnapshotMetadata predecessor = snapshots.get(predecessorId);
            if (predecessor == null) {
                throw new KnowledgeStoreException("snapshot predecessor not found: " + predecessorId);
            }
            if (!predecessor.projectId().equals(snapshot.projectId())) {
                throw new KnowledgeStoreException("snapshot predecessor belongs to another project");
            }
        });
    }

    private void rejectPublishedTargetState(KnowledgeSnapshotState targetState) {
        if (targetState == KnowledgeSnapshotState.ACTIVE || targetState == KnowledgeSnapshotState.RETIRED) {
            throw new SnapshotConflictException("ACTIVE/RETIRED states are owned by snapshot activation");
        }
    }
}
