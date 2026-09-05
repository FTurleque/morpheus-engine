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
    /** What a workspace registered at a filesystem root is called: it names the shape, and locates nothing. */
    static final String FILESYSTEM_ROOT_WORKSPACE_NAME = "filesystem-root";

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

    /**
     * The last segment of a file locator. Another scheme is relayed only when it locates nothing.
     *
     * <p>A workspace registered at a filesystem root has no last segment: {@code /} and a Windows drive root
     * leave nothing after the final separator. Falling back to the locator there handed a remote caller the
     * very pathname this projection exists to withhold, so the fallback is a name that locates nothing. The
     * result is checked before it leaves, because a locator shape nobody anticipated must fail closed too.</p>
     */
    String workspaceName(SourceLocator locator) {
        if (!"file".equals(locator.scheme())) {
            return ServerLocationDisclosure.isSafeToRelay(locator.value()) ? locator.value() : locator.scheme();
        }
        String normalized = locator.value().replace('\\', '/');
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int lastSeparator = normalized.lastIndexOf('/');
        String name = lastSeparator < 0 ? normalized : normalized.substring(lastSeparator + 1);
        if (name.isBlank() || isDriveDesignator(name) || !ServerLocationDisclosure.isSafeToRelay(name)) {
            return FILESYSTEM_ROOT_WORKSPACE_NAME;
        }
        return name;
    }

    /**
     * A drive root leaves a designator rather than nothing: {@code C:\} strips to {@code C:}, which passes a
     * blank check and still tells a caller which volume the server keeps the workspace on.
     */
    private static boolean isDriveDesignator(String candidate) {
        return candidate.length() == 2
                && candidate.charAt(1) == ':'
                && Character.isLetter(candidate.charAt(0));
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
            // Echo what the caller sent, not what the server resolved it to: a relative request would otherwise
            // report the server's working directory back. This branch runs only when no allowed-roots policy is
            // configured, so it is not reachable from a remote caller today -- and stays safe if that changes.
            throw ApiFailure.badRequest("workspace is not a directory: " + workspace);
        }
        return path;
    }

    record RegistrationResult(Object project, boolean created) {
        RegistrationResult {
            Objects.requireNonNull(project, "project");
        }
    }
}
