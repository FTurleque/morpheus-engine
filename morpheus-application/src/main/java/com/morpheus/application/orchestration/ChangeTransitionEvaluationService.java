package com.morpheus.application.orchestration;

import com.morpheus.application.lifecycle.ChangeLifecycleBlocker;
import com.morpheus.application.lifecycle.ChangeLifecyclePolicy;
import com.morpheus.application.lifecycle.ChangeLifecycleStateMachine;
import com.morpheus.application.quality.ChangeCompletenessService;
import com.morpheus.application.quality.ChangeLifecycleQualityAssessment;
import com.morpheus.application.quality.ChangeLifecycleQualityService;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.change.lifecycle.ChangeAbandonmentReason;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Read-only M14 adapter over the M3 lifecycle machine and M6 tri-state completeness facts. */
public final class ChangeTransitionEvaluationService {
    private final ChangeLifecycleQualityService lifecycleQuality;

    public ChangeTransitionEvaluationService(
            SpecificationKnowledgeStore snapshotStore,
            SnapshotBusinessContentStore contentStore,
            VersionedRequirementStore requirementStore,
            TraceabilityStore traceabilityStore) {
        Objects.requireNonNull(snapshotStore, "snapshotStore");
        ChangeCompletenessService completeness = new ChangeCompletenessService(
                snapshotStore,
                Objects.requireNonNull(contentStore, "contentStore"),
                Objects.requireNonNull(requirementStore, "requirementStore"),
                Objects.requireNonNull(traceabilityStore, "traceabilityStore"));
        this.lifecycleQuality = new ChangeLifecycleQualityService(
                snapshotStore,
                completeness,
                new ChangeLifecycleStateMachine());
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
        if (assessment.decision().isEmpty()) {
            return new ChangeTransitionEvaluation(
                    assessment.source().state(),
                    assessment.targetState(),
                    ChangeTransitionEvaluationState.UNKNOWN,
                    List.of(),
                    assessment.requiredFacts(),
                    assessment.unavailableRequiredFacts(),
                    assessment.factSource().name(),
                    "Required lifecycle facts are unavailable in the normalized snapshot");
        }

        var decision = assessment.decision().orElseThrow();
        if (decision.allowed()) {
            return new ChangeTransitionEvaluation(
                    assessment.source().state(),
                    assessment.targetState(),
                    ChangeTransitionEvaluationState.ALLOWED,
                    List.of(),
                    assessment.requiredFacts(),
                    List.of(),
                    assessment.factSource().name(),
                    "Transition is allowed by MORPHEUS lifecycle rules");
        }

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
                reason);
    }
}
