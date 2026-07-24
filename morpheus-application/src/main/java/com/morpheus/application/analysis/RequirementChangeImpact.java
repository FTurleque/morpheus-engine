package com.morpheus.application.analysis;

import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.domain.requirement.RequirementDelta;
import com.morpheus.domain.scenario.Scenario;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** CURRENT-versus-proposed comparison for one logical requirement affected by a change. */
public record RequirementChangeImpact(
        RequirementDelta delta,
        Optional<RequirementVersionRecord> currentRequirement,
        List<Scenario> currentScenarios,
        Set<RequirementChangeField> changedFields,
        List<ChangeAnalysisWarning> warnings) {

    public RequirementChangeImpact {
        Objects.requireNonNull(delta, "delta");
        currentRequirement = Objects.requireNonNull(currentRequirement, "currentRequirement");
        currentScenarios = Objects.requireNonNull(currentScenarios, "currentScenarios").stream()
                .peek(item -> Objects.requireNonNull(item, "currentScenarios item"))
                .sorted(Comparator.comparing(item -> item.id().toString()))
                .toList();
        changedFields = Set.copyOf(new TreeSet<>(Objects.requireNonNull(changedFields, "changedFields")));
        warnings = Objects.requireNonNull(warnings, "warnings").stream()
                .peek(item -> Objects.requireNonNull(item, "warnings item"))
                .sorted(Comparator.comparing((ChangeAnalysisWarning warning) -> warning.code().name())
                        .thenComparing(ChangeAnalysisWarning::message))
                .toList();
    }

    public List<Scenario> proposedScenarios() {
        return delta.kind() == com.morpheus.domain.requirement.RequirementDeltaKind.REMOVED
                ? List.of()
                : delta.scenarios();
    }
}
