package com.morpheus.api;

import com.morpheus.application.composition.CompositionQueryService;
import com.morpheus.application.composition.CompositionStateView;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** M18 read-only HTTP application facade for persisted multi-provider composition state. */
final class MorpheusCompositionApiService {
    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 100;

    private final Path databasePath;

    MorpheusCompositionApiService(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    CompositionStateView status(String projectId) {
        return state(projectId);
    }

    Map<String, Object> conflicts(String projectId, int offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        CompositionStateView state = state(projectId);
        Map<String, Object> identifiers = new LinkedHashMap<>();
        identifiers.put("snapshotId", state.snapshotId());
        identifiers.put("primaryProviderId", state.primaryProviderId());
        return PagedEnvelope.following(
                identifiers, PagedEnvelope.slice(offset, limit, state.conflicts(), Function.identity()));
    }

    private CompositionStateView state(String rawProjectId) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(rawProjectId);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            return new CompositionQueryService(runtime.snapshots, runtime.compositions)
                    .findActive(projectId)
                    .orElseThrow(() -> new KnowledgeStoreException(
                            "project has no ACTIVE snapshot composition state: " + projectId));
        }
    }
}
