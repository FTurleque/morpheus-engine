package com.morpheus.application.traceability;

import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;

import java.util.List;
import java.util.Objects;

/** Final explainable trace result rooted at one CURRENT requirement occurrence in one published snapshot. */
public record TraceRequirementResult(
        KnowledgeSnapshotMetadata snapshot,
        RequirementVersionRecord requirement,
        TraceabilitySubgraph subgraph,
        List<ExternalTraceabilityView> externalLinks) {

    public TraceRequirementResult {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(subgraph, "subgraph");
        externalLinks = List.copyOf(Objects.requireNonNull(externalLinks, "externalLinks"));

        if (!snapshot.id().equals(requirement.snapshotId())) {
            throw new IllegalArgumentException("requirement occurrence must belong to the result snapshot");
        }

        TraceabilityEntityRef expectedRoot = new TraceabilityEntityRef(
                TraceabilityEntityKind.REQUIREMENT,
                requirement.entityVersion().content().id().value());
        if (!expectedRoot.equals(subgraph.start())) {
            throw new IllegalArgumentException("trace subgraph must be rooted at the requirement identity");
        }

        if (externalLinks.stream().anyMatch(view -> !subgraph.links().contains(view.link()))) {
            throw new IllegalArgumentException("external traceability views must belong to the returned subgraph");
        }
    }
}
