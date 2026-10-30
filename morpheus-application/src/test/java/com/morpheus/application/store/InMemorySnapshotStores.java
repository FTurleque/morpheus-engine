package com.morpheus.application.store;

import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The snapshot, requirement, business-content and traceability ports over plain maps, for application tests that
 * publish, apply or promote a snapshot end to end: this module cannot depend on the memory store, which depends on it.
 */
public final class InMemorySnapshotStores implements
        SpecificationKnowledgeStore, VersionedRequirementStore, SnapshotBusinessContentStore, TraceabilityStore {
    private final Map<ProjectSpecificationId, ProjectStoreEntry> projects = new LinkedHashMap<>();
    private final Map<KnowledgeSnapshotId, KnowledgeSnapshotMetadata> snapshots = new LinkedHashMap<>();
    private final Map<SpecificationVersionId, SpecificationVersion> versions = new LinkedHashMap<>();
    private final Map<KnowledgeSnapshotId, SnapshotSpecificationVersionBinding> bindings = new LinkedHashMap<>();
    private final Map<EntityVersionId, RequirementVersionRecord> requirements = new LinkedHashMap<>();
    private final Map<KnowledgeSnapshotId, SnapshotBusinessContent> contents = new LinkedHashMap<>();
    private final Map<KnowledgeSnapshotId, Map<TraceabilityLinkId, TraceabilityLink>> links = new LinkedHashMap<>();
    private long nextSequence = 1L;

    @Override
    public void putProject(ProjectStoreEntry project) {
        projects.put(project.id(), project);
    }

    @Override
    public Optional<ProjectStoreEntry> findProject(ProjectSpecificationId projectId) {
        return Optional.ofNullable(projects.get(projectId));
    }

    @Override
    public Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator) {
        return projects.values().stream().filter(entry -> entry.rootLocator().equals(rootLocator)).findFirst();
    }

    @Override
    public List<ProjectStoreEntry> listProjects() {
        return List.copyOf(projects.values());
    }

    @Override
    public void putSnapshot(KnowledgeSnapshotMetadata snapshot) {
        if (snapshots.containsKey(snapshot.id())) {
            throw new KnowledgeStoreException("snapshot already exists: " + snapshot.id());
        }
        snapshots.put(snapshot.id(), snapshot);
    }

    @Override
    public Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId snapshotId) {
        return Optional.ofNullable(snapshots.get(snapshotId));
    }

    @Override
    public Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId projectId) {
        return snapshots.values().stream()
                .filter(snapshot -> snapshot.projectId().equals(projectId))
                .filter(snapshot -> snapshot.state() == KnowledgeSnapshotState.ACTIVE)
                .findFirst();
    }

    @Override
    public KnowledgeSnapshotMetadata transitionSnapshotState(
            KnowledgeSnapshotId snapshotId,
            KnowledgeSnapshotState expectedState,
            KnowledgeSnapshotState targetState) {
        KnowledgeSnapshotMetadata current = findSnapshot(snapshotId)
                .orElseThrow(() -> new KnowledgeStoreException("snapshot not found: " + snapshotId));
        if (current.state() != expectedState) {
            throw new KnowledgeStoreException("snapshot " + snapshotId + " is " + current.state());
        }
        KnowledgeSnapshotMetadata updated = current.withState(targetState);
        snapshots.put(snapshotId, updated);
        return updated;
    }

    @Override
    public KnowledgeSnapshotMetadata activateSnapshot(
            KnowledgeSnapshotId snapshotId,
            Optional<KnowledgeSnapshotId> expectedActiveSnapshotId) {
        KnowledgeSnapshotMetadata target = findSnapshot(snapshotId)
                .orElseThrow(() -> new KnowledgeStoreException("snapshot not found: " + snapshotId));
        Optional<KnowledgeSnapshotMetadata> active = activeSnapshot(target.projectId());
        if (!active.map(KnowledgeSnapshotMetadata::id).equals(expectedActiveSnapshotId)) {
            throw new KnowledgeStoreException("unexpected ACTIVE snapshot for " + target.projectId());
        }
        active.ifPresent(previous -> snapshots.put(previous.id(), previous.withState(KnowledgeSnapshotState.RETIRED)));
        KnowledgeSnapshotMetadata activated = target.withState(KnowledgeSnapshotState.ACTIVE);
        snapshots.put(snapshotId, activated);
        return activated;
    }

    @Override
    public void putSpecificationVersion(SpecificationVersion version) {
        versions.put(version.id(), version);
    }

    @Override
    public Optional<SpecificationVersion> findSpecificationVersion(SpecificationVersionId versionId) {
        return Optional.ofNullable(versions.get(versionId));
    }

    @Override
    public long nextSpecificationVersionSequence(ProjectSpecificationId projectId) {
        return nextSequence++;
    }

    @Override
    public void bindSnapshotVersion(SnapshotSpecificationVersionBinding binding) {
        bindings.put(binding.snapshotId(), binding);
    }

    @Override
    public Optional<SnapshotSpecificationVersionBinding> findSnapshotVersion(KnowledgeSnapshotId snapshotId) {
        return Optional.ofNullable(bindings.get(snapshotId));
    }

    @Override
    public void putRequirementVersion(RequirementVersionRecord record) {
        requirements.put(record.entityVersion().id(), record);
    }

    @Override
    public Optional<RequirementVersionRecord> findRequirementVersion(EntityVersionId entityVersionId) {
        return Optional.ofNullable(requirements.get(entityVersionId));
    }

    @Override
    public List<RequirementVersionRecord> listRequirementVersions(KnowledgeSnapshotId snapshotId) {
        return requirements.values().stream()
                .filter(record -> record.snapshotId().equals(snapshotId))
                .sorted(Comparator.comparing(record -> record.entityVersion().id().toString()))
                .toList();
    }

    @Override
    public Optional<RequirementVersionRecord> currentRequirement(
            KnowledgeSnapshotId snapshotId,
            DomainIdentity entityIdentity) {
        return listRequirementVersions(snapshotId).stream()
                .filter(record -> record.entityVersion().entityIdentity().equals(entityIdentity))
                .filter(record -> record.entityVersion().temporalState() == TemporalState.CURRENT)
                .findFirst();
    }

    @Override
    public void putSnapshotContent(SnapshotBusinessContent content) {
        contents.put(content.snapshotId(), content);
    }

    @Override
    public Optional<SnapshotBusinessContent> findSnapshotContent(KnowledgeSnapshotId snapshotId) {
        return Optional.ofNullable(contents.get(snapshotId));
    }

    @Override
    public void putLink(KnowledgeSnapshotId snapshotId, TraceabilityLink link) {
        links.computeIfAbsent(snapshotId, ignored -> new LinkedHashMap<>()).put(link.id(), link);
    }

    @Override
    public void putLinks(KnowledgeSnapshotId snapshotId, List<TraceabilityLink> batch) {
        batch.forEach(link -> putLink(snapshotId, link));
    }

    @Override
    public Optional<TraceabilityLink> findLink(KnowledgeSnapshotId snapshotId, TraceabilityLinkId linkId) {
        return Optional.ofNullable(links.getOrDefault(snapshotId, Map.of()).get(linkId));
    }

    @Override
    public List<TraceabilityLink> outgoing(
            KnowledgeSnapshotId snapshotId,
            TraceabilityEntityRef source,
            Set<TraceabilityRelationType> relationTypes) {
        return matching(snapshotId, relationTypes, link -> link.source().equals(source));
    }

    @Override
    public List<TraceabilityLink> incoming(
            KnowledgeSnapshotId snapshotId,
            TraceabilityEntityRef target,
            Set<TraceabilityRelationType> relationTypes) {
        return matching(snapshotId, relationTypes, link -> link.target().equals(target));
    }

    private List<TraceabilityLink> matching(
            KnowledgeSnapshotId snapshotId,
            Set<TraceabilityRelationType> relationTypes,
            Predicate<TraceabilityLink> endpoint) {
        return links.getOrDefault(snapshotId, Map.of()).values().stream()
                .filter(endpoint)
                .filter(link -> relationTypes.isEmpty() || relationTypes.contains(link.relationType()))
                .toList();
    }
}
