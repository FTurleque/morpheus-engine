package com.morpheus.application.query.compact;

import com.morpheus.application.query.compact.CompactQueryTypes.EvidenceView;
import com.morpheus.application.query.compact.CompactQueryTypes.PageMetadata;
import com.morpheus.application.query.compact.CompactQueryTypes.QueryMetadata;
import com.morpheus.application.query.compact.CompactQueryTypes.RequirementView;
import com.morpheus.application.query.compact.CompactQueryTypes.SnapshotMetadata;
import com.morpheus.application.query.compact.CompactQueryTypes.WarningView;

import java.util.List;
import java.util.Objects;

/** Compact deterministic exposure view for find_requirements. */
public record CompactRequirementSearchView(
        QueryMetadata metadata,
        SnapshotMetadata snapshot,
        String searchText,
        PageMetadata page,
        List<RequirementView> requirements,
        List<EvidenceView> evidence,
        List<WarningView> warnings) {

    public CompactRequirementSearchView {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(snapshot, "snapshot");
        searchText = Objects.requireNonNull(searchText, "searchText");
        Objects.requireNonNull(page, "page");
        requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }
}
