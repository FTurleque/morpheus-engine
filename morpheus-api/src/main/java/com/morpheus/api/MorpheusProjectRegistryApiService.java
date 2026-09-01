package com.morpheus.api;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Owns project registry orchestration and project registry API views. */
final class MorpheusProjectRegistryApiService {
    private final Path databasePath;
    private final Optional<AllowedWorkspaceRoots> allowedWorkspaceRoots;

    MorpheusProjectRegistryApiService(Path databasePath, Optional<AllowedWorkspaceRoots> allowedWorkspaceRoots) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.allowedWorkspaceRoots = Objects.requireNonNull(allowedWorkspaceRoots, "allowedWorkspaceRoots");
    }

    Object listProjects() {
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            return runtime.snapshots.listProjects().stream()
                    .map(project -> project(runtime, project))
                    .toList();
        }
    }

    RegistrationResult registerProject(String workspace) {
        Path path = allowedWorkspaceRoots
                .map(policy -> policy.requireAllowedDirectory(workspace))
                .orElseGet(() -> existingDirectory(workspace));
        SourceLocator root = SourceLocator.file(path.toString());
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            Optional<ProjectStoreEntry> existing = runtime.snapshots.findProjectByRoot(root);
            ProjectStoreEntry entry = existing.orElseGet(() -> {
                ProjectStoreEntry created = new ProjectStoreEntry(ProjectSpecificationId.generate(), root);
                runtime.snapshots.putProject(created);
                return created;
            });
            return new RegistrationResult(project(runtime, entry), existing.isEmpty());
        }
    }

    Object project(String projectIdValue) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            ProjectStoreEntry entry = runtime.snapshots.findProject(projectId)
                    .orElseThrow(() -> ApiFailure.notFound("project not found: " + projectId));
            return project(runtime, entry);
        }
    }

    private Object project(ApiRuntime runtime, ProjectStoreEntry entry) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", entry.id().toString());
        result.put("workspace", entry.rootLocator().value());
        result.put(
                "activeSnapshotId",
                runtime.snapshots.activeSnapshot(entry.id()).map(snapshot -> snapshot.id().toString()).orElse("none"));
        return Collections.unmodifiableMap(result);
    }

    private Path existingDirectory(String workspace) {
        if (workspace == null || workspace.isBlank()) {
            throw ApiFailure.badRequest("workspace is required");
        }
        Path path;
        try {
            path = Path.of(workspace).toAbsolutePath().normalize();
        } catch (RuntimeException failure) {
            throw ApiFailure.badRequest("workspace is not a valid path: " + workspace);
        }
        if (!Files.isDirectory(path)) {
            throw ApiFailure.badRequest("workspace is not a directory: " + path);
        }
        return path;
    }

    record RegistrationResult(Object project, boolean created) {
        RegistrationResult {
            Objects.requireNonNull(project, "project");
        }
    }
}
