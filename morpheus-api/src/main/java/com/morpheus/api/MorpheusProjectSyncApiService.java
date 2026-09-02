package com.morpheus.api;

import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.ObservedProjectSnapshotPublisher;
import com.morpheus.application.ingestion.ProjectSnapshotImportResult;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.operability.LocalOperationalRuntime;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.sync.IncrementalSyncService;
import com.morpheus.application.sync.LocalSourceInventoryScanner;
import com.morpheus.application.sync.SyncFreshness;
import com.morpheus.application.sync.SyncFreshnessService;
import com.morpheus.application.sync.SyncPlan;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.provider.openspec.OpenSpecProjectContentReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Owns project synchronization orchestration and synchronization API views. */
final class MorpheusProjectSyncApiService {
    private static final String UNKNOWN_VALUE = "unknown";

    private final Path databasePath;
    private final Optional<AllowedWorkspaceRoots> allowedWorkspaceRoots;

    MorpheusProjectSyncApiService(Path databasePath, Optional<AllowedWorkspaceRoots> allowedWorkspaceRoots) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.allowedWorkspaceRoots = Objects.requireNonNull(allowedWorkspaceRoots, "allowedWorkspaceRoots");
    }

    Object sync(String projectIdValue, Optional<String> revision) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        Optional<String> normalizedRevision = Objects.requireNonNull(revision, "revision")
                .map(String::trim)
                .filter(value -> !value.isEmpty());
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            Path workspace = projectWorkspace(runtime, projectId);
            Instant attemptedAt = Instant.now();
            var scan = new LocalSourceInventoryScanner().scan(
                    workspace,
                    projectId,
                    normalizedRevision,
                    attemptedAt,
                    List.of(Path.of("openspec")));
            IncrementalSyncService syncService = new IncrementalSyncService(runtime.syncState);
            SyncPlan plan = syncService.prepare(scan, SyncPlan.Trigger.manual().forced(), attemptedAt);
            if (!scan.complete()) {
                syncService.fail(plan, Instant.now());
                throw ApiFailure.conflict("source scan is incomplete: " + scan.failures());
            }

            try {
                var normalized = new OpenSpecProjectContentReader().read(
                        workspace,
                        projectId,
                        new PersistentEntityIdentityResolver(runtime.identities));
                ProjectSnapshotImportResult imported = new ObservedProjectSnapshotPublisher(
                        new ProjectSnapshotImportService(
                                runtime.snapshots,
                                runtime.requirements,
                                runtime.content,
                                runtime.traceability),
                        LocalOperationalRuntime.recorder())
                        .publishFull(normalized, normalizedRevision, Instant.now());
                syncService.complete(plan, Instant.now());
                return map(
                        "projectId", projectId.toString(),
                        "snapshotId", imported.snapshot().id().toString(),
                        "mode", plan.mode().name(),
                        "fullRebuildReason", plan.fullRebuildReason().map(Enum::name).orElse("none"),
                        "sourceCount", scan.inventory().orElseThrow().entries().size(),
                        "requirementCount", imported.requirementCount(),
                        "traceabilityLinkCount", imported.traceabilityLinkCount(),
                        "diagnosticCount", imported.diagnostics().size(),
                        "published", true);
            } catch (RuntimeException failure) {
                syncService.fail(plan, Instant.now());
                throw failure;
            }
        }
    }

    Object syncStatus(String projectIdValue, long maxAgeMinutes) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        requireRange("maxAgeMinutes", maxAgeMinutes, 1L, MorpheusApiService.MAX_MAX_AGE_MINUTES);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            requireProject(runtime, projectId);
            SyncFreshness freshness = new SyncFreshnessService(runtime.syncState)
                    .assess(projectId, Instant.now(), Duration.ofMinutes(maxAgeMinutes));
            return map(
                    "projectId", projectId.toString(),
                    "state", freshness.state().name(),
                    "lastSuccessfulSyncAt", freshness.lastSuccessfulSyncAt().map(Instant::toString).orElse(UNKNOWN_VALUE),
                    "ageSeconds", freshness.ageSinceSuccessfulSync().map(Duration::toSeconds).map(Object::toString).orElse(UNKNOWN_VALUE),
                    "lastObservedChangeAt", freshness.lastObservedChangeAt().map(Instant::toString).orElse(UNKNOWN_VALUE),
                    "sourceRevision", freshness.sourceRevision().orElse(UNKNOWN_VALUE),
                    "lastSuccessfulMode", freshness.lastSuccessfulMode().map(Enum::name).orElse(UNKNOWN_VALUE),
                    "pendingFullRebuildReason", freshness.pendingFullRebuildReason().map(Enum::name).orElse("none"),
                    "currentSourceCount", freshness.currentSourceCount());
        }
    }

    private ProjectStoreEntry requireProject(ApiRuntime runtime, ProjectSpecificationId projectId) {
        return runtime.snapshots.findProject(projectId)
                .orElseThrow(() -> ApiFailure.notFound("project not found: " + projectId));
    }

    private Path projectWorkspace(ApiRuntime runtime, ProjectSpecificationId projectId) {
        ProjectStoreEntry project = requireProject(runtime, projectId);
        if (!project.rootLocator().scheme().equals("file")) {
            throw ApiFailure.conflict("local headless sync requires a file: project root");
        }
        Path workspace = Path.of(project.rootLocator().value()).toAbsolutePath().normalize();
        Path authorizedWorkspace = allowedWorkspaceRoots
                .map(policy -> policy.requireAllowedDirectory(workspace))
                .orElse(workspace);
        if (!Files.isDirectory(authorizedWorkspace)) {
            // The workspace comes from the stored project, not from this request, so naming it would hand a remote
            // caller a pathname it never supplied -- including for a project the operator registered locally. The
            // route already identifies the project, which is what the caller can act on.
            throw ApiFailure.conflict("project workspace is no longer a directory: " + projectId);
        }
        return authorizedWorkspace;
    }

    private void requireRange(String name, long value, long minimum, long maximum) {
        if (value < minimum || value > maximum) {
            throw ApiFailure.badRequest(name + " must be between " + minimum + " and " + maximum);
        }
    }

    /** LinkedHashMap preserves stable construction order before canonical JSON serialization. */
    private Map<String, Object> map(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("map entries must be key/value pairs");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], entries[index + 1]);
        }
        return Collections.unmodifiableMap(result);
    }
}
