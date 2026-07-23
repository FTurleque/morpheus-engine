package com.morpheus.application.quality;

import com.morpheus.application.lifecycle.ChangeLifecycleFacts;
import com.morpheus.application.lifecycle.ChangeLifecyclePolicy;
import com.morpheus.application.lifecycle.ChangeLifecycleStateMachine;
import com.morpheus.application.lifecycle.ChangeLifecycleTransitionDecision;
import com.morpheus.application.lifecycle.ChangeLifecycleTransitionRequest;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.change.lifecycle.ChangeAbandonmentReason;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Projects M3 lifecycle decisions into explainable quality findings without inventing unavailable facts. */
public final class ChangeLifecycleQualityService {
    private final SpecificationKnowledgeStore snapshotStore;
    private final ChangeCompletenessService completenessService;
    private final ChangeLifecycleStateMachine stateMachine;

    public ChangeLifecycleQualityService(
            SpecificationKnowledgeStore snapshotStore,
            ChangeCompletenessService completenessService,
            ChangeLifecycleStateMachine stateMachine) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.completenessService = Objects.requireNonNull(completenessService, "completenessService");
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine");
    }

    public Optional<ChangeLifecycleQualityAssessment> assessDerivedActive(
            ProjectSpecificationId projectId,
            ChangeLifecycle source,
            ChangeLifecycleState targetState,
            ChangeLifecyclePolicy policy,
            Optional<ChangeAbandonmentReason> abandonmentReason) {
        Objects.requireNonNull(projectId, "projectId");
        return snapshotStore.activeSnapshot(projectId)
                .map(snapshot -> assessDerived(snapshot, source, targetState, policy, abandonmentReason));
    }

    public ChangeLifecycleQualityAssessment assessDerivedSnapshot(
            KnowledgeSnapshotId snapshotId,
            ChangeLifecycle source,
            ChangeLifecycleState targetState,
            ChangeLifecyclePolicy policy,
            Optional<ChangeAbandonmentReason> abandonmentReason) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        KnowledgeSnapshotMetadata snapshot = snapshotStore.findSnapshot(snapshotId)
                .orElseThrow(() -> new KnowledgeStoreException("unknown knowledge snapshot: " + snapshotId));
        return assessDerived(snapshot, source, targetState, policy, abandonmentReason);
    }

    public Optional<ChangeLifecycleQualityAssessment> assessExplicitActive(
            ProjectSpecificationId projectId,
            ChangeLifecycle source,
            ChangeLifecycleState targetState,
            ChangeLifecycleFacts facts,
            ChangeLifecyclePolicy policy,
            Optional<ChangeAbandonmentReason> abandonmentReason) {
        Objects.requireNonNull(projectId, "projectId");
        return snapshotStore.activeSnapshot(projectId)
                .map(snapshot -> assessExplicit(snapshot, source, targetState, facts, policy, abandonmentReason));
    }

    public ChangeLifecycleQualityAssessment assessExplicitSnapshot(
            KnowledgeSnapshotId snapshotId,
            ChangeLifecycle source,
            ChangeLifecycleState targetState,
            ChangeLifecycleFacts facts,
            ChangeLifecyclePolicy policy,
            Optional<ChangeAbandonmentReason> abandonmentReason) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        KnowledgeSnapshotMetadata snapshot = snapshotStore.findSnapshot(snapshotId)
                .orElseThrow(() -> new KnowledgeStoreException("unknown knowledge snapshot: " + snapshotId));
        return assessExplicit(snapshot, source, targetState, facts, policy, abandonmentReason);
    }

    private ChangeLifecycleQualityAssessment assessDerived(
            KnowledgeSnapshotMetadata snapshot,
            ChangeLifecycle source,
            ChangeLifecycleState targetState,
            ChangeLifecyclePolicy policy,
            Optional<ChangeAbandonmentReason> abandonmentReason) {
        validateInputs(source, targetState, policy, abandonmentReason);
        ChangeCompletenessAssessment completeness = completeness(snapshot.id(), source);
        ChangeLifecycleFactAssessment facts = completeness.lifecycleFacts();
        List<String> requiredFacts = requiredFacts(source.state(), targetState);
        List<String> unavailable = requiredFacts.stream()
                .filter(name -> facts.value(name) == QualityFactValue.UNAVAILABLE)
                .toList();

        if (!unavailable.isEmpty()) {
            List<QualityFinding> findings = unavailable.stream()
                    .map(fact -> unavailableFinding(snapshot, completeness.change(), source, targetState, fact))
                    .toList();
            return new ChangeLifecycleQualityAssessment(
                    snapshot,
                    source,
                    targetState,
                    LifecycleFactSource.DERIVED,
                    facts,
                    requiredFacts,
                    unavailable,
                    Optional.empty(),
                    findings);
        }

        ChangeLifecycleFacts materialized = facts.materializeForEvaluation(requiredFacts);
        ChangeLifecycleTransitionDecision decision = stateMachine.evaluate(new ChangeLifecycleTransitionRequest(
                source,
                targetState,
                materialized,
                policy,
                abandonmentReason));
        return evaluated(snapshot, completeness.change(), source, targetState,
                LifecycleFactSource.DERIVED, facts, requiredFacts, decision);
    }

    private ChangeLifecycleQualityAssessment assessExplicit(
            KnowledgeSnapshotMetadata snapshot,
            ChangeLifecycle source,
            ChangeLifecycleState targetState,
            ChangeLifecycleFacts explicitFacts,
            ChangeLifecyclePolicy policy,
            Optional<ChangeAbandonmentReason> abandonmentReason) {
        validateInputs(source, targetState, policy, abandonmentReason);
        Objects.requireNonNull(explicitFacts, "explicitFacts");
        ChangeCompletenessAssessment completeness = completeness(snapshot.id(), source);
        ChangeLifecycleTransitionDecision decision = stateMachine.evaluate(new ChangeLifecycleTransitionRequest(
                source,
                targetState,
                explicitFacts,
                policy,
                abandonmentReason));
        return evaluated(snapshot, completeness.change(), source, targetState,
                LifecycleFactSource.EXPLICIT,
                ChangeLifecycleFactAssessment.explicit(explicitFacts),
                requiredFacts(source.state(), targetState),
                decision);
    }

    private ChangeLifecycleQualityAssessment evaluated(
            KnowledgeSnapshotMetadata snapshot,
            ChangeProposal change,
            ChangeLifecycle source,
            ChangeLifecycleState targetState,
            LifecycleFactSource factSource,
            ChangeLifecycleFactAssessment facts,
            List<String> requiredFacts,
            ChangeLifecycleTransitionDecision decision) {
        List<QualityFinding> findings = new ArrayList<>();
        if (!decision.allowed()) {
            decision.blockers().forEach(blocker -> findings.add(new QualityFinding(
                    QualityFindingCode.LIFECYCLE_TRANSITION_BLOCKED,
                    DiagnosticSeverity.WARNING,
                    QualityEvidenceKind.DETERMINISTIC,
                    changeRef(change),
                    "Lifecycle transition blocked by M3 state machine: " + blocker.name(),
                    Map.of(
                            "changeId", change.id().toString(),
                            "snapshotId", snapshot.id().toString(),
                            "from", source.state().name(),
                            "to", targetState.name(),
                            "blocker", blocker.name(),
                            "factSource", factSource.name()),
                    Optional.empty(),
                    List.of(change.provenance().evidenceId()))));
        }
        return new ChangeLifecycleQualityAssessment(
                snapshot,
                source,
                targetState,
                factSource,
                facts,
                requiredFacts,
                List.of(),
                Optional.of(decision),
                findings);
    }

    private ChangeCompletenessAssessment completeness(KnowledgeSnapshotId snapshotId, ChangeLifecycle source) {
        ChangeCompletenessReport report = completenessService.assessSnapshot(snapshotId);
        return report.changes().stream()
                .filter(item -> item.change().id().equals(source.changeId()))
                .findFirst()
                .orElseThrow(() -> new KnowledgeStoreException(
                        "change lifecycle references a change absent from snapshot "
                                + snapshotId + ": " + source.changeId()));
    }

    private QualityFinding unavailableFinding(
            KnowledgeSnapshotMetadata snapshot,
            ChangeProposal change,
            ChangeLifecycle source,
            ChangeLifecycleState targetState,
            String fact) {
        return new QualityFinding(
                QualityFindingCode.LIFECYCLE_REQUIRED_FACT_UNAVAILABLE,
                DiagnosticSeverity.WARNING,
                QualityEvidenceKind.DETERMINISTIC,
                changeRef(change),
                "Lifecycle transition cannot be evaluated from normalized snapshot data because a required fact is unavailable: " + fact,
                Map.of(
                        "changeId", change.id().toString(),
                        "snapshotId", snapshot.id().toString(),
                        "from", source.state().name(),
                        "to", targetState.name(),
                        "fact", fact,
                        "factSource", LifecycleFactSource.DERIVED.name()),
                Optional.empty(),
                List.of(change.provenance().evidenceId()));
    }

    private TraceabilityEntityRef changeRef(ChangeProposal change) {
        return new TraceabilityEntityRef(TraceabilityEntityKind.CHANGE, change.id().value());
    }

    private List<String> requiredFacts(ChangeLifecycleState from, ChangeLifecycleState to) {
        if (from == ChangeLifecycleState.PROPOSED && to == ChangeLifecycleState.SPECIFIED) {
            return List.of("requirementsIdentified", "criticalConstraintsKnown", "acceptanceCriteriaDefined");
        }
        if (from == ChangeLifecycleState.SPECIFIED && to == ChangeLifecycleState.DESIGNED) {
            return List.of("designRequired", "designDecisionsAvailable");
        }
        if (from == ChangeLifecycleState.SPECIFIED && to == ChangeLifecycleState.PLANNED) {
            return List.of("designRequired", "planPresent");
        }
        if (from == ChangeLifecycleState.DESIGNED && to == ChangeLifecycleState.PLANNED) {
            return List.of("planPresent");
        }
        if (from == ChangeLifecycleState.PLANNED && to == ChangeLifecycleState.IMPLEMENTING) {
            return List.of("knownBlocker");
        }
        if (from == ChangeLifecycleState.VERIFYING && to == ChangeLifecycleState.COMPLETED) {
            return List.of("blockingAcceptanceCriterionFailed", "blockingAcceptanceCriterionUnverified");
        }
        return List.of();
    }

    private void validateInputs(
            ChangeLifecycle source,
            ChangeLifecycleState targetState,
            ChangeLifecyclePolicy policy,
            Optional<ChangeAbandonmentReason> abandonmentReason) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(targetState, "targetState");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(abandonmentReason, "abandonmentReason");
    }
}
