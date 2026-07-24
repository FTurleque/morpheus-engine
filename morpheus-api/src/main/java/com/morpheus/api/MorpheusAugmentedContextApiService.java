package com.morpheus.api;

import com.morpheus.application.context.AugmentedContextService;
import com.morpheus.application.context.TechnicalContextOptions;
import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.RequirementId;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** M13 HTTP mapper over the provider-neutral augmented-context use case. */
final class MorpheusAugmentedContextApiService {
    private final Path databasePath;
    private final TechnicalContextProvider provider;

    MorpheusAugmentedContextApiService(Path databasePath, TechnicalContextProvider provider) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    Object nexusStatus() {
        return provider.status();
    }

    Object requirement(String projectIdValue, String requirementIdValue, AugmentedContextRequest request) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        RequirementId requirementId = RequirementId.parse(requirementIdValue);
        TechnicalContextOptions options = options(request);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            if (runtime.snapshots.findProject(projectId).isEmpty()) {
                throw ApiFailure.notFound("project not found: " + projectId);
            }
            return new AugmentedContextService(
                    runtime.snapshots,
                    runtime.content,
                    runtime.requirements,
                    runtime.traceability,
                    runtime.externalReferences,
                    provider)
                    .requirement(projectId, requirementId, options)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
        }
    }

    Object change(String projectIdValue, String changeIdValue, AugmentedContextRequest request) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ChangeId changeId = ChangeId.parse(changeIdValue);
        TechnicalContextOptions options = options(request);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            if (runtime.snapshots.findProject(projectId).isEmpty()) {
                throw ApiFailure.notFound("project not found: " + projectId);
            }
            return new AugmentedContextService(
                    runtime.snapshots,
                    runtime.content,
                    runtime.requirements,
                    runtime.traceability,
                    runtime.externalReferences,
                    provider)
                    .change(projectId, changeId, options)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
        }
    }

    private TechnicalContextOptions options(AugmentedContextRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request body must be a JSON object");
        }
        String nexusProject = request.nexusProject();
        if (nexusProject == null || nexusProject.isBlank()) {
            throw new IllegalArgumentException("nexusProject must not be blank");
        }
        int budget = request.tokenBudget() == null
                ? TechnicalContextOptions.DEFAULT_TOKEN_BUDGET
                : request.tokenBudget();
        List<String> sourceValues = request.requestedSources() == null ? List.of() : request.requestedSources();
        Set<String> sources = sourceValues.stream()
                .map(value -> {
                    if (value == null || value.isBlank()) {
                        throw new IllegalArgumentException("requestedSources must contain non-blank strings");
                    }
                    return value.trim().toUpperCase(java.util.Locale.ROOT);
                })
                .collect(Collectors.toUnmodifiableSet());
        Map<String, String> constraints = request.constraints() == null ? Map.of() : request.constraints();
        boolean explain = Boolean.TRUE.equals(request.explain());
        return new TechnicalContextOptions(nexusProject, budget, sources, constraints, explain);
    }
}
