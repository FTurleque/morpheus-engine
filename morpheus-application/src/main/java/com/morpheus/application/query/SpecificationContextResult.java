package com.morpheus.application.query;

import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.specification.Specification;

import java.util.List;
import java.util.Objects;

/** Deterministic ACTIVE specification context reusable by MCP/API adapters. */
public record SpecificationContextResult(
        KnowledgeSnapshotMetadata snapshot,
        Specification specification,
        SnapshotPage<Requirement> requirements,
        List<Scenario> scenarios,
        List<ChangeProposal> changes) {

    public SpecificationContextResult {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(specification, "specification");
        Objects.requireNonNull(requirements, "requirements");
        scenarios = List.copyOf(Objects.requireNonNull(scenarios, "scenarios"));
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
        if (!requirements.snapshot().equals(snapshot)) {
            throw new IllegalArgumentException("requirements page belongs to another snapshot");
        }
    }
}
