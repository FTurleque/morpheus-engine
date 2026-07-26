package com.morpheus.application.query;

import com.morpheus.application.constraint.ConstraintPolicyEvaluationService;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.constraint.ConstraintEvaluation;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministic snapshot-scoped query for explicit M16 constraint-policy evaluations. */
public final class ConstraintEvaluationQueryService {
    private final SpecificationKnowledgeStore snapshotStore;
    private final SnapshotBusinessContentStore contentStore;
    private final ConstraintPolicyEvaluationService evaluator = new ConstraintPolicyEvaluationService();

    public ConstraintEvaluationQueryService(
            SpecificationKnowledgeStore snapshotStore,
            SnapshotBusinessContentStore contentStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.contentStore = Objects.requireNonNull(contentStore, "contentStore");
    }

    public Optional<SnapshotPage<ConstraintEvaluation>> activeEvaluations(
            ProjectSpecificationId projectId,
            ChangeId changeId,
            ChangeLifecycleState targetState,
            PageRequest pageRequest) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(targetState, "targetState");
        Objects.requireNonNull(pageRequest, "pageRequest");
        return snapshotStore.activeSnapshot(projectId)
                .map(snapshot -> evaluations(snapshot, changeId, targetState, pageRequest));
    }

    public SnapshotPage<ConstraintEvaluation> snapshotEvaluations(
            KnowledgeSnapshotId snapshotId,
            ChangeId changeId,
            ChangeLifecycleState targetState,
            PageRequest pageRequest) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(targetState, "targetState");
        Objects.requireNonNull(pageRequest, "pageRequest");
        return evaluations(requirePublished(snapshotId), changeId, targetState, pageRequest);
    }

    private SnapshotPage<ConstraintEvaluation> evaluations(
            KnowledgeSnapshotMetadata snapshot,
            ChangeId changeId,
            ChangeLifecycleState targetState,
            PageRequest pageRequest) {
        var content = contentStore.findSnapshotContent(snapshot.id())
                .orElseThrow(() -> new KnowledgeStoreException(
                        "published snapshot has no business-content projection: " + snapshot.id()));
        List<ConstraintEvaluation> all = content.constraints().stream()
                .filter(item -> item.changeId().equals(changeId))
                .sorted(java.util.Comparator.comparing(item -> item.id().toString()))
                .map(item -> evaluator.evaluate(item, targetState))
                .toList();
        int from = Math.min(pageRequest.offset(), all.size());
        int to = Math.min(from + pageRequest.limit(), all.size());
        List<ConstraintEvaluation> items = all.subList(from, to);
        return new SnapshotPage<>(snapshot, items, pageRequest, all.size(), to < all.size());
    }

    private KnowledgeSnapshotMetadata requirePublished(KnowledgeSnapshotId snapshotId) {
        KnowledgeSnapshotMetadata snapshot = snapshotStore.findSnapshot(snapshotId)
                .orElseThrow(() -> new KnowledgeStoreException("unknown knowledge snapshot: " + snapshotId));
        if (snapshot.state() != KnowledgeSnapshotState.ACTIVE
                && snapshot.state() != KnowledgeSnapshotState.RETIRED) {
            throw new KnowledgeStoreException(
                    "query requires an ACTIVE or RETIRED snapshot: " + snapshot.id() + " is " + snapshot.state());
        }
        return snapshot;
    }
}
