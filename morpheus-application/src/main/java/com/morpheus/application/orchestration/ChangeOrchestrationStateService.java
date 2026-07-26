package com.morpheus.application.orchestration;

import com.morpheus.application.lifecycle.ChangeLifecyclePolicy;
import com.morpheus.application.quality.ChangeCompletenessAssessment;
import com.morpheus.application.quality.ChangeCompletenessService;
import com.morpheus.application.quality.ChangeLifecycleFactAssessment;
import com.morpheus.application.quality.QualityFactValue;
import com.morpheus.application.quality.QualityFinding;
import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintApplicability;
import com.morpheus.domain.constraint.ConstraintBlockingMode;
import com.morpheus.domain.constraint.ConstraintSatisfaction;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceResolutionState;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityResolutionState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Builds the non-destructive UC-16 orchestration view consumed by JARVIS. */
public final class ChangeOrchestrationStateService {
    private final SpecificationKnowledgeStore snapshotStore;
    private final SnapshotBusinessContentStore contentStore;
    private final ExternalReferenceStore externalReferenceStore;
    private final TraceabilityStore traceabilityStore;
    private final ChangeCompletenessService completenessService;
    private final ChangeTransitionEvaluationService transitionService;

    public ChangeOrchestrationStateService(
            SpecificationKnowledgeStore snapshotStore,
            SnapshotBusinessContentStore contentStore,
            VersionedRequirementStore requirementStore,
            TraceabilityStore traceabilityStore,
            ExternalReferenceStore externalReferenceStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.contentStore = Objects.requireNonNull(contentStore, "contentStore");
        Objects.requireNonNull(requirementStore, "requirementStore");
        this.traceabilityStore = Objects.requireNonNull(traceabilityStore, "traceabilityStore");
        this.externalReferenceStore = Objects.requireNonNull(externalReferenceStore, "externalReferenceStore");
        this.completenessService = new ChangeCompletenessService(
                snapshotStore, contentStore, requirementStore, traceabilityStore);
        this.transitionService = new ChangeTransitionEvaluationService(
                snapshotStore, contentStore, requirementStore, traceabilityStore);
    }

    public Optional<ChangeOrchestrationState> active(
            ProjectSpecificationId projectId,
            ChangeId changeId,
            ChangeLifecycleObservation lifecycle) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(lifecycle, "lifecycle");

        Optional<KnowledgeSnapshotMetadata> snapshot = snapshotStore.activeSnapshot(projectId);
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        KnowledgeSnapshotMetadata active = snapshot.orElseThrow();
        ChangeCompletenessAssessment assessment = completenessService.assessSnapshot(active.id()).changes().stream()
                .filter(item -> item.change().id().equals(changeId))
                .findFirst()
                .orElseThrow(() -> new KnowledgeStoreException("change not found in ACTIVE snapshot: " + changeId));
        SnapshotBusinessContent content = contentStore.findSnapshotContent(active.id())
                .orElseThrow(() -> new KnowledgeStoreException(
                        "published snapshot has no business-content projection: " + active.id()));

        List<Constraint> constraints = content.constraints().stream()
                .filter(item -> item.changeId().equals(changeId))
                .sorted(Comparator.comparing(item -> item.id().toString()))
                .toList();
        long acceptanceCriterionCount = content.acceptanceCriteria().stream()
                .filter(item -> item.changeId().filter(changeId::equals).isPresent())
                .count();
        ChangeOrchestrationState.AvailabilityView blockingAvailability = blockingAvailability(constraints);
        List<ChangeOrchestrationState.UnresolvedLinkView> unresolvedLinks = unresolvedLinks(active, changeId);
        List<ChangeTransitionEvaluation> evaluations = transitionEvaluations(projectId, changeId, lifecycle);
        List<ChangeLifecycleState> allowed = evaluations.stream()
                .filter(item -> item.state() == ChangeTransitionEvaluationState.ALLOWED)
                .map(ChangeTransitionEvaluation::targetState)
                .toList();

        return Optional.of(new ChangeOrchestrationState(
                snapshot(active),
                change(assessment.change()),
                lifecycle,
                observableFacts(assessment.lifecycleFacts()),
                missingArtifacts(assessment.lifecycleFacts()),
                unavailableFacts(assessment.lifecycleFacts(), blockingAvailability),
                new ChangeOrchestrationState.AvailabilityView(
                        "AVAILABLE",
                        acceptanceCriterionCount == 0
                                ? "No explicit acceptance criterion is attached to this change in the published snapshot"
                                : "Explicit acceptance criteria are persisted and queryable; verification state does not imply blocking semantics",
                        Math.toIntExact(acceptanceCriterionCount)),
                constraints.stream().map(this::constraint).toList(),
                blockingAvailability,
                unresolvedLinks,
                assessment.findings().stream().map(this::finding).toList(),
                allowed,
                evaluations,
                false));
    }

    private ChangeOrchestrationState.AvailabilityView blockingAvailability(List<Constraint> constraints) {
        if (constraints.isEmpty()) {
            return new ChangeOrchestrationState.AvailabilityView(
                    "AVAILABLE", "No constraint is attached to this change", 0);
        }
        long blocking = constraints.stream().filter(this::currentlyBlockingByExplicitPolicy).count();
        long unknown = constraints.stream().filter(item -> !blockingKnowledgeKnown(item)).count();
        if (unknown == 0) {
            return new ChangeOrchestrationState.AvailabilityView(
                    "AVAILABLE",
                    "All constraint blocking semantics are explicit; observedCount is the number of currently violated explicit blocking policies",
                    Math.toIntExact(blocking));
        }
        if (unknown < constraints.size()) {
            return new ChangeOrchestrationState.AvailabilityView(
                    "PARTIALLY_AVAILABLE",
                    "Some constraints have explicit blocking semantics while others remain unknown; UNKNOWN is not treated as blocking",
                    Math.toIntExact(blocking));
        }
        return new ChangeOrchestrationState.AvailabilityView(
                "UNKNOWN",
                "Constraint blocking semantics are not explicit for this change; UNKNOWN is not treated as blocking",
                0);
    }

    private boolean blockingKnowledgeKnown(Constraint constraint) {
        if (constraint.applicability() == ConstraintApplicability.NOT_APPLICABLE) {
            return true;
        }
        if (constraint.applicability() == ConstraintApplicability.UNKNOWN) {
            return false;
        }
        if (constraint.blockingPolicy().mode() == ConstraintBlockingMode.UNKNOWN) {
            return false;
        }
        if (constraint.blockingPolicy().mode() == ConstraintBlockingMode.NON_BLOCKING) {
            return true;
        }
        return constraint.satisfaction() != ConstraintSatisfaction.UNKNOWN;
    }

    private boolean currentlyBlockingByExplicitPolicy(Constraint constraint) {
        return constraint.applicability() == ConstraintApplicability.APPLICABLE
                && constraint.blockingPolicy().mode() == ConstraintBlockingMode.BLOCK_WHEN_VIOLATED
                && constraint.satisfaction() == ConstraintSatisfaction.VIOLATED;
    }

    private List<ChangeTransitionEvaluation> transitionEvaluations(
            ProjectSpecificationId projectId,
            ChangeId changeId,
            ChangeLifecycleObservation lifecycle) {
        if (lifecycle.state().isEmpty()) {
            return List.of();
        }
        ChangeLifecycle source = lifecycle.state().orElseThrow() == ChangeLifecycleState.ABANDONED
                ? ChangeLifecycle.abandoned(changeId, lifecycle.abandonmentReason().orElseThrow())
                : ChangeLifecycle.of(changeId, lifecycle.state().orElseThrow());

        List<ChangeTransitionEvaluation> evaluations = new ArrayList<>();
        for (ChangeLifecycleState target : ChangeLifecycleState.values()) {
            if (target == source.state()) {
                continue;
            }
            transitionService.evaluateActive(
                            projectId,
                            source,
                            target,
                            ChangeLifecyclePolicy.forwardOnly(),
                            Optional.empty())
                    .ifPresent(evaluations::add);
        }
        return List.copyOf(evaluations);
    }

    private List<ChangeOrchestrationState.UnresolvedLinkView> unresolvedLinks(
            KnowledgeSnapshotMetadata snapshot,
            ChangeId changeId) {
        TraceabilityEntityRef changeRef = new TraceabilityEntityRef(TraceabilityEntityKind.CHANGE, changeId.value());
        List<ChangeOrchestrationState.UnresolvedLinkView> result = new ArrayList<>();

        traceabilityStore.outgoing(snapshot.id(), changeRef, Set.of()).stream()
                .filter(link -> link.resolution() != TraceabilityResolutionState.RESOLVED)
                .map(this::traceabilityLink)
                .forEach(result::add);

        externalReferenceStore.findByOwner(snapshot.id(), changeId.value()).stream()
                .filter(reference -> reference.resolutionState() != ExternalReferenceResolutionState.RESOLVED)
                .map(this::externalReference)
                .forEach(result::add);

        result.sort(Comparator.comparing(ChangeOrchestrationState.UnresolvedLinkView::kind)
                .thenComparing(ChangeOrchestrationState.UnresolvedLinkView::id));
        return List.copyOf(result);
    }

    private Map<String, QualityFactValue> observableFacts(ChangeLifecycleFactAssessment facts) {
        Map<String, QualityFactValue> values = new LinkedHashMap<>();
        values.put("requirementsIdentified", facts.requirementsIdentified());
        values.put("criticalConstraintsKnown", facts.criticalConstraintsKnown());
        values.put("acceptanceCriteriaDefined", facts.acceptanceCriteriaDefined());
        values.put("designRequired", facts.designRequired());
        values.put("designDecisionsAvailable", facts.designDecisionsAvailable());
        values.put("planPresent", facts.planPresent());
        values.put("knownBlocker", facts.knownBlocker());
        values.put("blockingAcceptanceCriterionFailed", facts.blockingAcceptanceCriterionFailed());
        values.put("blockingAcceptanceCriterionUnverified", facts.blockingAcceptanceCriterionUnverified());
        return Map.copyOf(values);
    }

    private List<String> unavailableFacts(
            ChangeLifecycleFactAssessment facts,
            ChangeOrchestrationState.AvailabilityView blockingAvailability) {
        List<String> unavailable = new ArrayList<>(facts.unavailableFacts());
        if (!"AVAILABLE".equals(blockingAvailability.status())) {
            unavailable.add("blockingConstraints");
        }
        return List.copyOf(unavailable.stream().distinct().sorted().toList());
    }

    private List<String> missingArtifacts(ChangeLifecycleFactAssessment facts) {
        List<String> missing = new ArrayList<>();
        if (facts.requirementsIdentified() == QualityFactValue.FALSE) {
            missing.add("requirements");
        }
        if (facts.criticalConstraintsKnown() == QualityFactValue.FALSE) {
            missing.add("criticalConstraints");
        }
        if (facts.acceptanceCriteriaDefined() == QualityFactValue.FALSE) {
            missing.add("acceptanceCriteria");
        }
        if (facts.designRequired() == QualityFactValue.TRUE
                && facts.designDecisionsAvailable() == QualityFactValue.FALSE) {
            missing.add("designDecisions");
        }
        if (facts.planPresent() == QualityFactValue.FALSE) {
            missing.add("implementationPlan");
        }
        return List.copyOf(missing);
    }

    private ChangeOrchestrationState.SnapshotView snapshot(KnowledgeSnapshotMetadata snapshot) {
        return new ChangeOrchestrationState.SnapshotView(
                snapshot.id().toString(),
                snapshot.projectId().toString(),
                snapshot.state().name(),
                snapshot.createdAt().toString());
    }

    private ChangeOrchestrationState.ChangeView change(ChangeProposal change) {
        return new ChangeOrchestrationState.ChangeView(
                change.id().toString(), change.key(), change.title(), change.intent());
    }

    private ChangeOrchestrationState.ConstraintView constraint(Constraint constraint) {
        return new ChangeOrchestrationState.ConstraintView(
                constraint.id().toString(),
                constraint.statement(),
                constraint.applicability().name(),
                constraint.severity().name(),
                constraint.satisfaction().name(),
                constraint.blockingPolicy().mode().name(),
                constraint.blockingPolicy().targetStates().stream().map(Enum::name).toList(),
                constraint.supportingEvidenceIds().stream().map(EvidenceId::toString).toList(),
                constraint.provenance().evidenceId().toString());
    }

    private ChangeOrchestrationState.UnresolvedLinkView traceabilityLink(TraceabilityLink link) {
        return new ChangeOrchestrationState.UnresolvedLinkView(
                "TRACEABILITY",
                link.id().toString(),
                link.relationType().name(),
                link.target().kind().name() + ":" + link.target().identity(),
                link.resolution().name(),
                link.origin().name());
    }

    private ChangeOrchestrationState.UnresolvedLinkView externalReference(ExternalReference reference) {
        var target = reference.target();
        String coordinate = target.project().map(value -> value + ":").orElse("")
                + target.externalId()
                + target.revision().map(value -> "@" + value).orElse("");
        return new ChangeOrchestrationState.UnresolvedLinkView(
                "EXTERNAL_REFERENCE",
                reference.id().toString(),
                target.system() + ":" + target.resourceType(),
                coordinate,
                reference.resolutionState().name(),
                reference.resolutionReason().name());
    }

    private ChangeOrchestrationState.QualityFindingView finding(QualityFinding finding) {
        return new ChangeOrchestrationState.QualityFindingView(
                finding.code().name(),
                finding.severity().name(),
                finding.evidenceKind().name(),
                finding.message(),
                finding.details(),
                finding.confidence(),
                finding.evidenceIds().stream().map(Object::toString).toList());
    }
}
