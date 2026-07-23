package com.morpheus.application.query.compact;

import com.morpheus.application.query.compact.CompactQueryTypes.ChangeView;
import com.morpheus.application.query.compact.CompactQueryTypes.ConstraintView;
import com.morpheus.application.query.compact.CompactQueryTypes.DesignDecisionView;
import com.morpheus.application.query.compact.CompactQueryTypes.EvidenceView;
import com.morpheus.application.query.compact.CompactQueryTypes.ExternalReferenceView;
import com.morpheus.application.query.compact.CompactQueryTypes.ImplementationTaskView;
import com.morpheus.application.query.compact.CompactQueryTypes.QueryMetadata;
import com.morpheus.application.query.compact.CompactQueryTypes.RequirementView;
import com.morpheus.application.query.compact.CompactQueryTypes.SnapshotMetadata;
import com.morpheus.application.query.compact.CompactQueryTypes.TraceLinkView;
import com.morpheus.application.query.compact.CompactQueryTypes.TraceNodeView;
import com.morpheus.application.query.compact.CompactQueryTypes.WarningView;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Compact deterministic exposure view for get_change_context. */
public record CompactChangeContextView(
        QueryMetadata metadata,
        SnapshotMetadata snapshot,
        String changeId,
        Optional<ChangeView> change,
        List<TraceLinkView> affectedRequirementLinks,
        List<RequirementView> affectedRequirements,
        List<ConstraintView> constraints,
        List<DesignDecisionView> designDecisions,
        List<ImplementationTaskView> implementationTasks,
        List<TraceNodeView> nodes,
        List<TraceLinkView> links,
        List<ExternalReferenceView> externalReferences,
        List<EvidenceView> evidence,
        List<WarningView> warnings) {

    public CompactChangeContextView {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(snapshot, "snapshot");
        changeId = Objects.requireNonNull(changeId, "changeId").trim();
        if (changeId.isEmpty()) {
            throw new IllegalArgumentException("changeId must not be blank");
        }
        change = Objects.requireNonNull(change, "change");
        affectedRequirementLinks = List.copyOf(Objects.requireNonNull(affectedRequirementLinks, "affectedRequirementLinks"));
        affectedRequirements = List.copyOf(Objects.requireNonNull(affectedRequirements, "affectedRequirements"));
        constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints"));
        designDecisions = List.copyOf(Objects.requireNonNull(designDecisions, "designDecisions"));
        implementationTasks = List.copyOf(Objects.requireNonNull(implementationTasks, "implementationTasks"));
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        links = List.copyOf(Objects.requireNonNull(links, "links"));
        externalReferences = List.copyOf(Objects.requireNonNull(externalReferences, "externalReferences"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }
}
