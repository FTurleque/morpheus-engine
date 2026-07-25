package com.morpheus.application.query;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.task.ImplementationTask;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministic business-content queries over published snapshot projections. */
public final class BusinessContentQueryService {
    private static final Comparator<ChangeProposal> CHANGE_ORDER = Comparator.comparing(ChangeProposal::id);
    private static final Comparator<Constraint> CONSTRAINT_ORDER = Comparator.comparing(Constraint::id);
    private static final Comparator<DesignDecision> DECISION_ORDER = Comparator.comparing(DesignDecision::id);
    private static final Comparator<ImplementationTask> TASK_ORDER = Comparator.comparing(ImplementationTask::id);
    private static final Comparator<AcceptanceCriterion> ACCEPTANCE_ORDER = Comparator.comparing(AcceptanceCriterion::id);

    private final SpecificationKnowledgeStore snapshotStore;
    private final SnapshotBusinessContentStore contentStore;

    public BusinessContentQueryService(
            SpecificationKnowledgeStore snapshotStore,
            SnapshotBusinessContentStore contentStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.contentStore = Objects.requireNonNull(contentStore, "contentStore");
    }

    public Optional<SnapshotItemResult<Specification>> activeSpecification(
            ProjectSpecificationId projectId,
            SpecificationId specificationId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(specificationId, "specificationId");
        return snapshotStore.activeSnapshot(projectId)
                .map(snapshot -> specification(snapshot, specificationId));
    }

    public SnapshotItemResult<Specification> snapshotSpecification(
            KnowledgeSnapshotId snapshotId,
            SpecificationId specificationId) {
        Objects.requireNonNull(specificationId, "specificationId");
        return specification(requirePublished(snapshotId), specificationId);
    }

    public Optional<SnapshotItemResult<ChangeProposal>> activeChange(
            ProjectSpecificationId projectId,
            ChangeId changeId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(changeId, "changeId");
        return snapshotStore.activeSnapshot(projectId)
                .map(snapshot -> change(snapshot, changeId));
    }

    public SnapshotItemResult<ChangeProposal> snapshotChange(
            KnowledgeSnapshotId snapshotId,
            ChangeId changeId) {
        Objects.requireNonNull(changeId, "changeId");
        return change(requirePublished(snapshotId), changeId);
    }

    public Optional<SnapshotPage<ChangeProposal>> listActiveChanges(
            ProjectSpecificationId projectId,
            PageRequest pageRequest) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(pageRequest, "pageRequest");
        return snapshotStore.activeSnapshot(projectId)
                .map(snapshot -> changes(snapshot, pageRequest));
    }

    public SnapshotPage<ChangeProposal> listSnapshotChanges(
            KnowledgeSnapshotId snapshotId,
            PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest");
        return changes(requirePublished(snapshotId), pageRequest);
    }

    public Optional<SnapshotPage<Constraint>> activeConstraints(
            ProjectSpecificationId projectId,
            ChangeId changeId,
            PageRequest pageRequest) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(pageRequest, "pageRequest");
        return snapshotStore.activeSnapshot(projectId)
                .map(snapshot -> constraints(snapshot, changeId, pageRequest));
    }

    public SnapshotPage<Constraint> snapshotConstraints(
            KnowledgeSnapshotId snapshotId,
            ChangeId changeId,
            PageRequest pageRequest) {
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(pageRequest, "pageRequest");
        return constraints(requirePublished(snapshotId), changeId, pageRequest);
    }

    public Optional<SnapshotPage<DesignDecision>> activeDesignDecisions(
            ProjectSpecificationId projectId,
            ChangeId changeId,
            PageRequest pageRequest) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(pageRequest, "pageRequest");
        return snapshotStore.activeSnapshot(projectId)
                .map(snapshot -> designDecisions(snapshot, changeId, pageRequest));
    }

    public SnapshotPage<DesignDecision> snapshotDesignDecisions(
            KnowledgeSnapshotId snapshotId,
            ChangeId changeId,
            PageRequest pageRequest) {
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(pageRequest, "pageRequest");
        return designDecisions(requirePublished(snapshotId), changeId, pageRequest);
    }

    public Optional<SnapshotPage<ImplementationTask>> activeImplementationTasks(
            ProjectSpecificationId projectId,
            ChangeId changeId,
            PageRequest pageRequest) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(pageRequest, "pageRequest");
        return snapshotStore.activeSnapshot(projectId)
                .map(snapshot -> implementationTasks(snapshot, changeId, pageRequest));
    }

    public SnapshotPage<ImplementationTask> snapshotImplementationTasks(
            KnowledgeSnapshotId snapshotId,
            ChangeId changeId,
            PageRequest pageRequest) {
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(pageRequest, "pageRequest");
        return implementationTasks(requirePublished(snapshotId), changeId, pageRequest);
    }

    public Optional<SnapshotPage<AcceptanceCriterion>> activeAcceptanceCriteria(
            ProjectSpecificationId projectId,
            PageRequest pageRequest) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(pageRequest, "pageRequest");
        return snapshotStore.activeSnapshot(projectId)
                .map(snapshot -> acceptanceCriteria(snapshot, pageRequest));
    }

    public SnapshotPage<AcceptanceCriterion> snapshotAcceptanceCriteria(
            KnowledgeSnapshotId snapshotId,
            PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest");
        return acceptanceCriteria(requirePublished(snapshotId), pageRequest);
    }

    public Optional<SnapshotPage<AcceptanceCriterion>> activeAcceptanceCriteriaForChange(
            ProjectSpecificationId projectId,
            ChangeId changeId,
            PageRequest pageRequest) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(pageRequest, "pageRequest");
        return snapshotStore.activeSnapshot(projectId)
                .map(snapshot -> acceptanceCriteriaForChange(snapshot, changeId, pageRequest));
    }

    public Optional<SnapshotPage<AcceptanceCriterion>> activeAcceptanceCriteriaForRequirement(
            ProjectSpecificationId projectId,
            RequirementId requirementId,
            PageRequest pageRequest) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(requirementId, "requirementId");
        Objects.requireNonNull(pageRequest, "pageRequest");
        return snapshotStore.activeSnapshot(projectId)
                .map(snapshot -> acceptanceCriteriaForRequirement(snapshot, requirementId, pageRequest));
    }

    private SnapshotItemResult<Specification> specification(
            KnowledgeSnapshotMetadata snapshot,
            SpecificationId specificationId) {
        Optional<Specification> item = content(snapshot).specifications().stream()
                .filter(candidate -> candidate.id().equals(specificationId))
                .findFirst();
        return new SnapshotItemResult<>(snapshot, item);
    }

    private SnapshotItemResult<ChangeProposal> change(
            KnowledgeSnapshotMetadata snapshot,
            ChangeId changeId) {
        Optional<ChangeProposal> item = content(snapshot).changes().stream()
                .filter(candidate -> candidate.id().equals(changeId))
                .findFirst();
        return new SnapshotItemResult<>(snapshot, item);
    }

    private SnapshotPage<ChangeProposal> changes(
            KnowledgeSnapshotMetadata snapshot,
            PageRequest pageRequest) {
        return page(snapshot, content(snapshot).changes().stream().sorted(CHANGE_ORDER).toList(), pageRequest);
    }

    private SnapshotPage<Constraint> constraints(
            KnowledgeSnapshotMetadata snapshot,
            ChangeId changeId,
            PageRequest pageRequest) {
        List<Constraint> matches = content(snapshot).constraints().stream()
                .filter(candidate -> candidate.changeId().equals(changeId))
                .sorted(CONSTRAINT_ORDER)
                .toList();
        return page(snapshot, matches, pageRequest);
    }

    private SnapshotPage<DesignDecision> designDecisions(
            KnowledgeSnapshotMetadata snapshot,
            ChangeId changeId,
            PageRequest pageRequest) {
        List<DesignDecision> matches = content(snapshot).designDecisions().stream()
                .filter(candidate -> candidate.changeId().equals(changeId))
                .sorted(DECISION_ORDER)
                .toList();
        return page(snapshot, matches, pageRequest);
    }

    private SnapshotPage<ImplementationTask> implementationTasks(
            KnowledgeSnapshotMetadata snapshot,
            ChangeId changeId,
            PageRequest pageRequest) {
        List<ImplementationTask> matches = content(snapshot).tasks().stream()
                .filter(candidate -> candidate.changeId().equals(changeId))
                .sorted(TASK_ORDER)
                .toList();
        return page(snapshot, matches, pageRequest);
    }

    private SnapshotPage<AcceptanceCriterion> acceptanceCriteria(
            KnowledgeSnapshotMetadata snapshot,
            PageRequest pageRequest) {
        return page(snapshot, content(snapshot).acceptanceCriteria().stream().sorted(ACCEPTANCE_ORDER).toList(), pageRequest);
    }

    private SnapshotPage<AcceptanceCriterion> acceptanceCriteriaForChange(
            KnowledgeSnapshotMetadata snapshot,
            ChangeId changeId,
            PageRequest pageRequest) {
        List<AcceptanceCriterion> matches = content(snapshot).acceptanceCriteria().stream()
                .filter(candidate -> candidate.changeId().filter(changeId::equals).isPresent())
                .sorted(ACCEPTANCE_ORDER)
                .toList();
        return page(snapshot, matches, pageRequest);
    }

    private SnapshotPage<AcceptanceCriterion> acceptanceCriteriaForRequirement(
            KnowledgeSnapshotMetadata snapshot,
            RequirementId requirementId,
            PageRequest pageRequest) {
        List<AcceptanceCriterion> matches = content(snapshot).acceptanceCriteria().stream()
                .filter(candidate -> candidate.requirementId().filter(requirementId::equals).isPresent())
                .sorted(ACCEPTANCE_ORDER)
                .toList();
        return page(snapshot, matches, pageRequest);
    }

    private SnapshotBusinessContent content(KnowledgeSnapshotMetadata snapshot) {
        return contentStore.findSnapshotContent(snapshot.id())
                .orElseThrow(() -> new KnowledgeStoreException(
                        "published snapshot has no business-content projection: " + snapshot.id()));
    }

    private KnowledgeSnapshotMetadata requirePublished(KnowledgeSnapshotId snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        KnowledgeSnapshotMetadata snapshot = snapshotStore.findSnapshot(snapshotId)
                .orElseThrow(() -> new KnowledgeStoreException("unknown knowledge snapshot: " + snapshotId));
        if (snapshot.state() != KnowledgeSnapshotState.ACTIVE
                && snapshot.state() != KnowledgeSnapshotState.RETIRED) {
            throw new KnowledgeStoreException(
                    "business-content query requires an ACTIVE or RETIRED snapshot: "
                            + snapshot.id() + " is " + snapshot.state());
        }
        return snapshot;
    }

    private <T> SnapshotPage<T> page(
            KnowledgeSnapshotMetadata snapshot,
            List<T> matches,
            PageRequest pageRequest) {
        int totalMatches = matches.size();
        int from = Math.min(pageRequest.offset(), totalMatches);
        long requestedEnd = (long) from + pageRequest.limit();
        int to = (int) Math.min(requestedEnd, totalMatches);
        List<T> items = matches.subList(from, to);
        return new SnapshotPage<>(snapshot, items, pageRequest, totalMatches, to < totalMatches);
    }
}
