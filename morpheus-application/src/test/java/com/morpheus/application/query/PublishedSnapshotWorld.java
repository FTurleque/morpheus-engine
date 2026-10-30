package com.morpheus.application.query;

import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.acceptance.AcceptanceCriterionId;
import com.morpheus.domain.acceptance.VerificationStatus;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintApplicability;
import com.morpheus.domain.constraint.ConstraintBlockingPolicy;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.constraint.ConstraintSatisfaction;
import com.morpheus.domain.constraint.ConstraintSeverity;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.decision.DesignDecisionId;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.evidence.SourceRange;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.reference.ExternalReferenceTarget;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.scenario.ScenarioId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One project with one ACTIVE snapshot, its business content, its versioned requirements, its links and its external
 * references, served by in-test ports so the application tests depend on no adapter module. Every identity the world
 * creates is drawn from one ascending sequence, so the order of creation is the order of the identities.
 */
public final class PublishedSnapshotWorld {
    public static final Instant CREATED_AT = Instant.parse("2026-10-30T08:00:00Z");

    private final Map<KnowledgeSnapshotId, KnowledgeSnapshotMetadata> snapshots = new LinkedHashMap<>();
    private final List<Specification> specifications = new ArrayList<>();
    private final List<Scenario> scenarios = new ArrayList<>();
    private final List<ChangeProposal> changes = new ArrayList<>();
    private final List<Constraint> constraints = new ArrayList<>();
    private final List<DesignDecision> decisions = new ArrayList<>();
    private final List<ImplementationTask> tasks = new ArrayList<>();
    private final List<AcceptanceCriterion> criteria = new ArrayList<>();
    private final List<Evidence> evidence = new ArrayList<>();
    private final List<RequirementVersionRecord> requirementVersions = new ArrayList<>();
    private final List<TraceabilityLink> links = new ArrayList<>();
    private final List<ExternalReference> references = new ArrayList<>();
    private final ProjectSpecificationId projectId;
    private final KnowledgeSnapshotMetadata active;
    private final SpecificationVersionId specificationVersionId;
    private final Evidence definition;
    private int sequence;
    private boolean contentPublished = true;
    private Optional<KnowledgeSnapshotId> contentSnapshotOverride = Optional.empty();

    public PublishedSnapshotWorld() {
        this.projectId = new ProjectSpecificationId(next());
        this.active = snapshot(KnowledgeSnapshotState.ACTIVE);
        this.specificationVersionId = new SpecificationVersionId(next());
        this.definition = evidence("specs/definition.md");
    }

    public DomainIdentity next() {
        sequence++;
        return DomainIdentity.parse("00000000-0000-7000-8000-%012d".formatted(sequence));
    }

    public ProjectSpecificationId projectId() {
        return projectId;
    }

    public KnowledgeSnapshotMetadata active() {
        return active;
    }

    public Evidence definition() {
        return definition;
    }

    public KnowledgeSnapshotMetadata snapshot(KnowledgeSnapshotState state) {
        KnowledgeSnapshotMetadata snapshot = new KnowledgeSnapshotMetadata(
                new KnowledgeSnapshotId(next()), projectId, Optional.empty(), state, Optional.of("rev-1"), CREATED_AT);
        snapshots.put(snapshot.id(), snapshot);
        return snapshot;
    }

    public void withoutContent() {
        contentPublished = false;
    }

    public void withContentOf(KnowledgeSnapshotId snapshotId) {
        contentSnapshotOverride = Optional.of(snapshotId);
    }

    public Evidence evidence(String path) {
        Evidence created = new Evidence(new EvidenceId(next()), SourceLocator.file(path),
                Optional.of(new SourceRange(1, 3)), Optional.of("sha256:" + path));
        evidence.add(created);
        return created;
    }

    public Provenance provenance() {
        return new Provenance(new ProviderId("world-provider"), Optional.of("1"), SourceLocator.file("specs/world.md"),
                Optional.of("external"), Optional.of("rev-1"), definition.id());
    }

    public Specification specification(String key) {
        Specification created = new Specification(new SpecificationId(next()), projectId, key, "Title " + key,
                Optional.of("About " + key), provenance());
        specifications.add(created);
        return created;
    }

    public RequirementVersionRecord requirement(SpecificationId specificationId, String key, String statement) {
        return requirementIn(active.id(), specificationId, key, statement, TemporalState.CURRENT);
    }

    public RequirementVersionRecord requirementIn(
            KnowledgeSnapshotId snapshotId,
            SpecificationId specificationId,
            String key,
            String statement,
            TemporalState state) {
        Requirement requirement = new Requirement(new RequirementId(next()), specificationId, Optional.of(key),
                "Requirement " + key, statement, provenance());
        RequirementVersionRecord record = new RequirementVersionRecord(snapshotId, new EntityVersion<>(
                new EntityVersionId(next()), requirement.id().value(), specificationVersionId, state, requirement));
        requirementVersions.add(record);
        return record;
    }

    public Scenario scenario(Optional<RequirementId> requirementId) {
        Scenario created = new Scenario(new ScenarioId(next()), requirementId, "Scenario", List.of("given"),
                "when", "then", provenance());
        scenarios.add(created);
        return created;
    }

    public ChangeProposal change(String key) {
        ChangeProposal created = new ChangeProposal(new ChangeId(next()), projectId, Optional.of(key), "Change " + key,
                "Intent of " + key, List.of("scope"), List.of("out"), List.of("risk"), provenance());
        changes.add(created);
        return created;
    }

    public Constraint constraint(ChangeId changeId) {
        Constraint created = new Constraint(new ConstraintId(next()), changeId, "Constraint statement",
                ConstraintApplicability.APPLICABLE, ConstraintSeverity.INFO, ConstraintSatisfaction.UNKNOWN,
                ConstraintBlockingPolicy.nonBlocking(), List.of(), provenance());
        constraints.add(created);
        return created;
    }

    public DesignDecision decision(ChangeId changeId) {
        DesignDecision created = new DesignDecision(new DesignDecisionId(next()), changeId, "Decision", "Use a port",
                provenance());
        decisions.add(created);
        return created;
    }

    public ImplementationTask task(ChangeId changeId) {
        ImplementationTask created = new ImplementationTask(new TaskId(next()), changeId, Optional.of("1.1"), "Task",
                false, provenance());
        tasks.add(created);
        return created;
    }

    public AcceptanceCriterion criterion(Optional<RequirementId> requirementId, Optional<ChangeId> changeId) {
        AcceptanceCriterion created = new AcceptanceCriterion(new AcceptanceCriterionId(next()), requirementId,
                changeId, "Criterion", "Condition", VerificationStatus.NOT_VERIFIED, List.of(), provenance());
        criteria.add(created);
        return created;
    }

    public TraceabilityLink link(
            TraceabilityEntityRef source,
            TraceabilityRelationType relation,
            TraceabilityEntityRef target,
            Set<EvidenceId> evidenceIds) {
        TraceabilityLink created = new TraceabilityLink(new TraceabilityLinkId(next()), source, relation, target,
                TraceabilityLinkOrigin.EXPLICIT, TraceabilityResolutionState.RESOLVED, Optional.empty(), evidenceIds,
                CREATED_AT);
        links.add(created);
        return created;
    }

    public TraceabilityLink link(
            TraceabilityEntityRef source,
            TraceabilityRelationType relation,
            TraceabilityEntityRef target) {
        return link(source, relation, target, Set.of(definition.id()));
    }

    public ExternalReference reference(DomainIdentity ownerId) {
        ExternalReference created = ExternalReference.unvalidated(new ExternalReferenceId(next()), ownerId,
                new ExternalReferenceTarget("MINOS", Optional.of("morpheus"), "SYMBOL", "symbol:Owner",
                        Optional.of("r1")),
                Optional.of(provenance()));
        references.add(created);
        return created;
    }

    public void replace(ExternalReference reference) {
        references.removeIf(existing -> existing.id().equals(reference.id()));
        references.add(reference);
    }

    public static TraceabilityEntityRef ref(TraceabilityEntityKind kind, DomainIdentity identity) {
        return new TraceabilityEntityRef(kind, identity);
    }

    public SnapshotBusinessContent content() {
        return new SnapshotBusinessContent(contentSnapshotOverride.orElse(active.id()), specificationVersionId,
                specifications, scenarios, changes, constraints, decisions, tasks, criteria, evidence);
    }

    public SpecificationKnowledgeStore snapshots() {
        return new Snapshots();
    }

    public SnapshotBusinessContentStore contentStore() {
        return new Contents();
    }

    public VersionedRequirementStore requirementStore() {
        return new Requirements();
    }

    public TraceabilityStore traceabilityStore() {
        return new Links();
    }

    public ExternalReferenceStore referenceStore() {
        return new References();
    }

    private final class Snapshots implements SpecificationKnowledgeStore {
        @Override
        public Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId snapshotId) {
            return Optional.ofNullable(snapshots.get(snapshotId));
        }

        @Override
        public Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId requested) {
            return requested.equals(projectId) ? Optional.of(active) : Optional.empty();
        }

        @Override
        public void putProject(ProjectStoreEntry project) {
            throw new UnsupportedOperationException("not read by a query");
        }

        @Override
        public Optional<ProjectStoreEntry> findProject(ProjectSpecificationId requested) {
            throw new UnsupportedOperationException("not read by a query");
        }

        @Override
        public Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator) {
            throw new UnsupportedOperationException("not read by a query");
        }

        @Override
        public List<ProjectStoreEntry> listProjects() {
            throw new UnsupportedOperationException("not read by a query");
        }

        @Override
        public void putSnapshot(KnowledgeSnapshotMetadata snapshot) {
            throw new UnsupportedOperationException("not read by a query");
        }

        @Override
        public KnowledgeSnapshotMetadata transitionSnapshotState(
                KnowledgeSnapshotId snapshotId, KnowledgeSnapshotState expected, KnowledgeSnapshotState target) {
            throw new UnsupportedOperationException("not read by a query");
        }

        @Override
        public KnowledgeSnapshotMetadata activateSnapshot(
                KnowledgeSnapshotId snapshotId, Optional<KnowledgeSnapshotId> expectedActive) {
            throw new UnsupportedOperationException("not read by a query");
        }
    }

    private final class Contents implements SnapshotBusinessContentStore {
        @Override
        public Optional<SnapshotBusinessContent> findSnapshotContent(KnowledgeSnapshotId snapshotId) {
            return contentPublished && snapshots.containsKey(snapshotId) ? Optional.of(content()) : Optional.empty();
        }

        @Override
        public void putSnapshotContent(SnapshotBusinessContent content) {
            throw new UnsupportedOperationException("not read by a query");
        }
    }

    private final class Requirements implements VersionedRequirementStore {
        @Override
        public List<RequirementVersionRecord> listRequirementVersions(KnowledgeSnapshotId snapshotId) {
            return requirementVersions.stream().filter(record -> record.snapshotId().equals(snapshotId)).toList();
        }

        @Override
        public Optional<RequirementVersionRecord> currentRequirement(
                KnowledgeSnapshotId snapshotId, DomainIdentity entityIdentity) {
            return listRequirementVersions(snapshotId).stream()
                    .filter(record -> record.entityVersion().temporalState() == TemporalState.CURRENT)
                    .filter(record -> record.entityVersion().entityIdentity().equals(entityIdentity))
                    .findFirst();
        }

        @Override
        public void putSpecificationVersion(SpecificationVersion version) {
            throw new UnsupportedOperationException("not read by a query");
        }

        @Override
        public Optional<SpecificationVersion> findSpecificationVersion(SpecificationVersionId versionId) {
            throw new UnsupportedOperationException("not read by a query");
        }

        @Override
        public void bindSnapshotVersion(SnapshotSpecificationVersionBinding binding) {
            throw new UnsupportedOperationException("not read by a query");
        }

        @Override
        public Optional<SnapshotSpecificationVersionBinding> findSnapshotVersion(KnowledgeSnapshotId snapshotId) {
            throw new UnsupportedOperationException("not read by a query");
        }

        @Override
        public void putRequirementVersion(RequirementVersionRecord record) {
            throw new UnsupportedOperationException("not read by a query");
        }

        @Override
        public Optional<RequirementVersionRecord> findRequirementVersion(EntityVersionId entityVersionId) {
            throw new UnsupportedOperationException("not read by a query");
        }
    }

    private final class Links implements TraceabilityStore {
        @Override
        public List<TraceabilityLink> outgoing(
                KnowledgeSnapshotId snapshotId,
                TraceabilityEntityRef source,
                Set<TraceabilityRelationType> relationTypes) {
            return links.stream()
                    .filter(link -> snapshots.containsKey(snapshotId) && link.source().equals(source))
                    .filter(link -> relationTypes.isEmpty() || relationTypes.contains(link.relationType()))
                    .toList();
        }

        @Override
        public List<TraceabilityLink> incoming(
                KnowledgeSnapshotId snapshotId,
                TraceabilityEntityRef target,
                Set<TraceabilityRelationType> relationTypes) {
            return links.stream()
                    .filter(link -> snapshots.containsKey(snapshotId) && link.target().equals(target))
                    .filter(link -> relationTypes.isEmpty() || relationTypes.contains(link.relationType()))
                    .toList();
        }

        @Override
        public void putLink(KnowledgeSnapshotId snapshotId, TraceabilityLink link) {
            throw new UnsupportedOperationException("not read by a query");
        }

        @Override
        public void putLinks(KnowledgeSnapshotId snapshotId, List<TraceabilityLink> batch) {
            throw new UnsupportedOperationException("not read by a query");
        }

        @Override
        public Optional<TraceabilityLink> findLink(KnowledgeSnapshotId snapshotId, TraceabilityLinkId linkId) {
            throw new UnsupportedOperationException("not read by a query");
        }
    }

    private final class References implements ExternalReferenceStore {
        @Override
        public Optional<ExternalReference> findReference(
                KnowledgeSnapshotId snapshotId, ExternalReferenceId referenceId) {
            return references.stream()
                    .filter(reference -> snapshots.containsKey(snapshotId) && reference.id().equals(referenceId))
                    .findFirst();
        }

        @Override
        public List<ExternalReference> findByOwner(KnowledgeSnapshotId snapshotId, DomainIdentity ownerId) {
            return references.stream()
                    .filter(reference -> snapshots.containsKey(snapshotId) && reference.ownerId().equals(ownerId))
                    .toList();
        }

        @Override
        public void putReference(KnowledgeSnapshotId snapshotId, ExternalReference reference) {
            throw new UnsupportedOperationException("not read by a query");
        }
    }
}
