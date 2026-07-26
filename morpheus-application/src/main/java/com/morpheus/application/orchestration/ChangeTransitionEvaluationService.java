package com.morpheus.application.orchestration;

import com.morpheus.application.constraint.ConstraintPolicyEvaluationService;
import com.morpheus.application.lifecycle.ChangeLifecycleBlocker;
import com.morpheus.application.lifecycle.ChangeLifecyclePolicy;
import com.morpheus.application.lifecycle.ChangeLifecycleStateMachine;
import com.morpheus.application.quality.ChangeCompletenessService;
import com.morpheus.application.quality.ChangeLifecycleQualityAssessment;
import com.morpheus.application.quality.ChangeLifecycleQualityService;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.change.lifecycle.ChangeAbandonmentReason;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.constraint.ConstraintEvaluation;
import com.morpheus.domain.constraint.ConstraintEvaluationState;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Read-only lifecycle evaluation enriched by explicit M16 constraint policy. */
public final class ChangeTransitionEvaluationService {
    private final ChangeLifecycleQualityService lifecycleQuality;
    private final SnapshotBusinessContentStore contentStore;
    private final ConstraintPolicyEvaluationService constraintPolicy;

    public ChangeTransitionEvaluationService(
            SpecificationKnowledgeStore snapshotStore,
            SnapshotBusinessContentStore contentStore,
            VersionedRequirementStore requirementStore,
            TraceabilityStore traceabilityStore) {
        Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.contentStore = Objects.requireNonNull(contentStore, "contentStore");
        ChangeCompletenessService completeness = new ChangeCompletenessService(
                snapshotStore,
                contentStore,
                Objects.requireNonNull(requirementStore, "requirementStore"),
                Objects.requireNonNull(traceabilityStore, "traceabilityStore"));
        this.lifecycleQuality = new ChangeLifecycleQualityService(
                snapshotStore,
                completeness,
                new ChangeLifecycleStateMachine());
        this.constraintPolicy = new ConstraintPolicyEvaluationService();
    }

    public Optional<ChangeTransitionEvaluation> evaluateActive(
            ProjectSpecificationId projectId,
            ChangeLifecycle source,
            ChangeLifecycleState targetState,
            ChangeLifecyclePolicy policy,
            Optional<ChangeAbandonmentReason> targetAbandonmentReason) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(targetState, "targetState");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(targetAbandonmentReason, "targetAbandonmentReason");

        return lifecycleQuality.assessDerivedActive(
                        projectId,
                        source,
                        targetState,
                        policy,
                        targetAbandonmentReason)
                .map(this::toEvaluation);
    }

    private ChangeTransitionEvaluation toEvaluation(ChangeLifecycleQualityAssessment assessment) {
        List<ConstraintEvaluation> constraintEvaluations = constraintEvaluations(assessment);
        List<ConstraintEvaluation> blocking = constraintEvaluations.stream()
                .filter(item -> item.state() == ConstraintEvaluationState.BLOCKING)
                .toList();
        List<ConstraintEvaluation> unknown = constraintEvaluations.stream()
                .filter(item -> item.state() == ConstraintEvaluationState.UNKNOWN)
                .toList();

        if (!blocking.isEmpty()) {
            List<ChangeLifecycleBlocker> blockers = new ArrayList<>();
            assessment.decision().ifPresent(decision -> blockers.addAll(decision.blockers()));
            if (!blockers.contains(ChangeLifecycleBlocker.BLOCKING_CONSTRAINT)) {
                blockers.add(ChangeLifecycleBlocker.BLOCKING_CONSTRAINT);
            }
            String ids = blocking.stream()
                    .map(item -> item.constraintId().toString())
                    .sorted()
                    .reduce((left, right) -> left + "," + right)
                    .orElseThrow();
            return new ChangeTransitionEvaluation(
                    assessment.source().state(),
                    assessment.targetState(),
                    ChangeTransitionEvaluationState.BLOCKED,
                    blockers,
                    assessment.requiredFacts(),
                    assessment.unavailableRequiredFacts(),
                    assessment.factSource().name(),
                    "Transition is blocked by explicit constraint policy: " + ids,
                    constraintEvaluations);
        }

        if (assessment.decision().isEmpty()) {
            List<String> unavailable = withConstraintAvailability(
                    assessment.unavailableRequiredFacts(), !unknown.isEmpty());
            return new ChangeTransitionEvaluation(
                    assessment.source().state(),
                    assessment.targetState(),
                    ChangeTransitionEvaluationState.UNKNOWN,
                    List.of(),
                    assessment.requiredFacts(),
                    unavailable,
                    assessment.factSource().name(),
                    !unknown.isEmpty()
                            ? "Required lifecycle facts and constraint blocking semantics are unavailable"
                            : "Required lifecycle facts are unavailable in the normalized snapshot",
                    constraintEvaluations);
        }

        var decision = assessment.decision().orElseThrow();
        if (!decision.allowed()) {
            ChangeTransitionEvaluationState state = decision.blockers().contains(ChangeLifecycleBlocker.ABANDONMENT_REASON_REQUIRED)
                    ? ChangeTransitionEvaluationState.REQUIRES_INPUT
                    : ChangeTransitionEvaluationState.BLOCKED;
            String reason = state == ChangeTransitionEvaluationState.REQUIRES_INPUT
                    ? "Transition requires an explicit abandonment reason"
                    : "Transition is blocked by MORPHEUS lifecycle rules";
            return new ChangeTransitionEvaluation(
                    assessment.source().state(),
                    assessment.targetState(),
                    state,
                    decision.blockers(),
                    assessment.requiredFacts(),
                    List.of(),
                    assessment.factSource().name(),
                    reason,
                    constraintEvaluations);
        }

        if (!unknown.isEmpty()) {
            return new ChangeTransitionEvaluation(
                    assessment.source().state(),
                    assessment.targetState(),
                    ChangeTransitionEvaluationState.UNKNOWN,
                    List.of(),
                    assessment.requiredFacts(),
                    List.of("blockingConstraints"),
                    assessment.factSource().name(),
                    "Transition cannot be asserted ALLOWED because constraint blocking semantics are unknown",
                    constraintEvaluations);
        }

        return new ChangeTransitionEvaluation(
                assessment.source().state(),
                assessment.targetState(),
                ChangeTransitionEvaluationState.ALLOWED,
                List.of(),
                assessment.requiredFacts(),
                List.of(),
                assessment.factSource().name(),
                "Transition is allowed by MORPHEUS lifecycle and explicit constraint rules",
                constraintEvaluations);
    }

    private List<ConstraintEvaluation> constraintEvaluations(ChangeLifecycleQualityAssessment assessment) {
        var content = contentStore.findSnapshotContent(assessment.snapshot().id())
                .orElseThrow(() -> new KnowledgeStoreException(
                        "published snapshot has no business-content projection: " + assessment.snapshot().id()));
        return content.constraints().stream()
                .filter(item -> item.changeId().equals(assessment.source().changeId()))
                .sorted(Comparator.comparing(item -> item.id().toString()))
                .map(item -> constraintPolicy.evaluate(item, assessment.targetState()))
                .toList();
    }

    private List<String> withConstraintAvailability(List<String> unavailable, boolean constraintUnknown) {
        if (!constraintUnknown || unavailable.contains("blockingConstraints")) {
            return unavailable;
        }
        List<String> copy = new ArrayList<>(unavailable);
        copy.add("blockingConstraints");
        return List.copyOf(copy);
    }
}
