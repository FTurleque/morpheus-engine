package com.morpheus.domain.change;

import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Provider-neutral normalized change proposal without M3 temporal projection. */
public record ChangeProposal(
        ChangeId id,
        ProjectSpecificationId projectId,
        Optional<String> key,
        String title,
        String intent,
        List<String> scope,
        List<String> outOfScope,
        List<String> risks,
        Provenance provenance) {

    public ChangeProposal {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(projectId, "projectId");
        key = normalizeOptional(key, "key");
        title = requireNonBlank(title, "title");
        intent = requireNonBlank(intent, "intent");
        scope = normalizedList(scope, "scope");
        outOfScope = normalizedList(outOfScope, "outOfScope");
        risks = normalizedList(risks, "risks");
        Objects.requireNonNull(provenance, "provenance");
    }

    private static Optional<String> normalizeOptional(Optional<String> value, String name) {
        return Objects.requireNonNull(value, name).map(String::trim).filter(candidate -> !candidate.isEmpty());
    }

    private static List<String> normalizedList(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        return values.stream().map(item -> requireNonBlank(item, name + " item")).toList();
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
