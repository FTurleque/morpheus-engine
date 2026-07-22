package com.morpheus.domain.requirement;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.scenario.Scenario;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Provider-neutral structural requirement delta owned by one change proposal. */
public record RequirementDelta(
        RequirementDeltaId id,
        ChangeId changeId,
        RequirementDeltaKind kind,
        String specificationKey,
        RequirementId requirementId,
        Optional<String> key,
        String title,
        Optional<String> statement,
        List<Scenario> scenarios,
        Provenance provenance) {

    public RequirementDelta {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(kind, "kind");
        specificationKey = requireNonBlank(specificationKey, "specificationKey");
        Objects.requireNonNull(requirementId, "requirementId");
        key = normalizeOptional(key, "key");
        title = requireNonBlank(title, "title");
        statement = normalizeOptional(statement, "statement");
        scenarios = List.copyOf(Objects.requireNonNull(scenarios, "scenarios"));
        Objects.requireNonNull(provenance, "provenance");

        if (kind != RequirementDeltaKind.REMOVED && statement.isEmpty()) {
            throw new IllegalArgumentException(kind + " requirement delta must contain a statement");
        }

        for (Scenario scenario : scenarios) {
            if (scenario.requirementId().isEmpty() || !scenario.requirementId().orElseThrow().equals(requirementId)) {
                throw new IllegalArgumentException("delta scenario must reference the delta requirement identity: " + scenario.id());
            }
        }
    }

    private static Optional<String> normalizeOptional(Optional<String> value, String name) {
        return Objects.requireNonNull(value, name)
                .map(String::trim)
                .filter(candidate -> !candidate.isEmpty());
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
