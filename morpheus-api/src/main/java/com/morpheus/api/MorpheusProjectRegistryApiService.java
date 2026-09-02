package com.morpheus.api;

import com.morpheus.application.security.ServerLocationDisclosure;
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

    /**
     * The HTTP surface names a project's workspace; it does not locate it.
     *
     * <p>{@code GET /projects} lists every registered project, including ones registered locally by the operator,
     * so a remote READ caller would otherwise learn absolute pathnames it never supplied and cannot reach. The
     * workspace name is what distinguishes projects to a human, and {@code projectId} remains the identity every
     * other route is addressed by. The CLI renders its own view and keeps the full pathname, which is what an
     * operator passes back to {@code --workspace}.</p>
     */
    private Object project(ApiRuntime runtime, ProjectStoreEntry entry) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", entry.id().toString());
        result.put("workspaceName", workspaceName(entry.rootLocator()));
        result.put(
                "activeSnapshotId",
                runtime.snapshots.activeSnapshot(entry.id()).map(snapshot -> snapshot.id().toString()).orElse("none"));
        return Collections.unmodifiableMap(result);
    }

    /** The last segment of a file locator. Another scheme is relayed only when it locates nothing. */
    private String workspaceName(SourceLocator locator) {
        if (!"file".equals(locator.scheme())) {
            return ServerLocationDisclosure.isSafeToRelay(locator.value()) ? locator.value() : locator.scheme();
        }
        String normalized = locator.value().replace('\\', '/');
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int lastSeparator = normalized.lastIndexOf('/');
        String name = lastSeparator < 0 ? normalized : normalized.substring(lastSeparator + 1);
        return name.isBlank() ? locator.value() : name;
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
