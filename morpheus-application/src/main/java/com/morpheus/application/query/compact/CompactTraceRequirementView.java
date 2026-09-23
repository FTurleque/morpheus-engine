package com.morpheus.application.query.compact;

import com.morpheus.application.query.compact.CompactQueryTypes.EvidenceView;
import com.morpheus.application.query.compact.CompactQueryTypes.ExternalReferenceView;
import com.morpheus.application.query.compact.CompactQueryTypes.QueryMetadata;
import com.morpheus.application.query.compact.CompactQueryTypes.RequirementView;
import com.morpheus.application.query.compact.CompactQueryTypes.SnapshotMetadata;
import com.morpheus.application.query.compact.CompactQueryTypes.TraceLinkView;
import com.morpheus.application.query.compact.CompactQueryTypes.TraceNodeView;
import com.morpheus.application.query.compact.CompactQueryTypes.WarningView;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Compact deterministic exposure view for trace_requirement. */
public record CompactTraceRequirementView(
        QueryMetadata metadata,
        SnapshotMetadata snapshot,
        RequirementView requirement,
        List<TraceNodeView> nodes,
        List<TraceLinkView> links,
        List<ExternalReferenceView> externalReferences,
        List<EvidenceView> evidence,
        List<WarningView> warnings,
        Optional<String> truncationReason,
        boolean truncated) {

    public CompactTraceRequirementView {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(requirement, "requirement");
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        links = List.copyOf(Objects.requireNonNull(links, "links"));
        externalReferences = List.copyOf(Objects.requireNonNull(externalReferences, "externalReferences"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        truncationReason = Objects.requireNonNull(truncationReason, "truncationReason");
        if (truncated != truncationReason.isPresent()) {
            throw new IllegalArgumentException("truncated must be true exactly when a truncationReason is present");
        }
    }
}
