package com.morpheus.domain.scenario;

import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.requirement.RequirementId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Provider-neutral behavioral scenario, independent of Gherkin syntax. */
public record Scenario(
        ScenarioId id,
        Optional<RequirementId> requirementId,
        String title,
        List<String> preconditions,
        String action,
        String expectedOutcome,
        Provenance provenance) {

    public Scenario {
        Objects.requireNonNull(id, "id");
        requirementId = Objects.requireNonNull(requirementId, "requirementId");
        title = requireNonBlank(title, "title");
        preconditions = List.copyOf(Objects.requireNonNull(preconditions, "preconditions"));
        if (preconditions.stream().anyMatch(value -> value == null || value.trim().isEmpty())) {
            throw new IllegalArgumentException("preconditions must not contain blank values");
        }
        action = requireNonBlank(action, "action");
        expectedOutcome = requireNonBlank(expectedOutcome, "expectedOutcome");
        Objects.requireNonNull(provenance, "provenance");
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
