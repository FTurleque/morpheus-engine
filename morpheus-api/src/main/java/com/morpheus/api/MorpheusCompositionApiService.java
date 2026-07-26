package com.morpheus.api;

import com.morpheus.application.composition.CompositionQueryService;
import com.morpheus.application.composition.CompositionStateView;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        int total = state.conflicts().size();
        int from = Math.min(offset, total);
        int to = Math.min(total, from + limit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("snapshotId", state.snapshotId());
        result.put("primaryProviderId", state.primaryProviderId());
        result.put("offset", offset);
        result.put("limit", limit);
        result.put("totalMatches", total);
        result.put("hasMore", to < total);
        result.put("items", List.copyOf(state.conflicts().subList(from, to)));
        return Map.copyOf(result);
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
