package com.morpheus.application.quality;

import com.morpheus.application.lifecycle.ChangeLifecycleTransitionDecision;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Explainable lifecycle transition quality assessment anchored to one published snapshot. */
public record ChangeLifecycleQualityAssessment(
        KnowledgeSnapshotMetadata snapshot,
        ChangeLifecycle source,
        ChangeLifecycleState targetState,
        LifecycleFactSource factSource,
        ChangeLifecycleFactAssessment facts,
        List<String> requiredFacts,
        List<String> unavailableRequiredFacts,
        Optional<ChangeLifecycleTransitionDecision> decision,
        List<QualityFinding> findings) {

    public ChangeLifecycleQualityAssessment {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(targetState, "targetState");
        Objects.requireNonNull(factSource, "factSource");
        Objects.requireNonNull(facts, "facts");
        requiredFacts = List.copyOf(Objects.requireNonNull(requiredFacts, "requiredFacts"));
        unavailableRequiredFacts = List.copyOf(Objects.requireNonNull(
                unavailableRequiredFacts, "unavailableRequiredFacts"));
        decision = Objects.requireNonNull(decision, "decision");
        findings = Objects.requireNonNull(findings, "findings").stream()
                .peek(item -> Objects.requireNonNull(item, "findings item"))
                .sorted()
                .toList();

        if (!requiredFacts.containsAll(unavailableRequiredFacts)) {
            throw new IllegalArgumentException("unavailableRequiredFacts must be a subset of requiredFacts");
        }
        if (decision.isEmpty() && unavailableRequiredFacts.isEmpty()) {
            throw new IllegalArgumentException("missing decision requires unavailable required lifecycle facts");
        }
        if (decision.isPresent() && !unavailableRequiredFacts.isEmpty()) {
            throw new IllegalArgumentException("evaluated transition must not retain unavailable required facts");
        }
        if (factSource == LifecycleFactSource.EXPLICIT && !unavailableRequiredFacts.isEmpty()) {
            throw new IllegalArgumentException("explicit lifecycle facts cannot be unavailable");
        }
    }
}
