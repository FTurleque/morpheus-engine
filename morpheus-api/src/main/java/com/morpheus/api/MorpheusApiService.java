package com.morpheus.api;

import com.morpheus.application.product.ProductMetadata;
import com.morpheus.application.query.PageRequest;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Headless M11 application adapter facade.
 *
 * <p>This class performs adapter composition and compatibility delegation only. Business semantics
 * remain in MORPHEUS application/domain services.</p>
 */
public final class MorpheusApiService {
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = PageRequest.MAX_LIMIT;
    public static final int DEFAULT_DEPTH = 2;
    public static final int MAX_DEPTH = 20;
    public static final long DEFAULT_MAX_AGE_MINUTES = 60L;
    public static final long MAX_MAX_AGE_MINUTES = 525_600L;

    private final Path databasePath;
    private final MorpheusProjectRegistryApiService projectRegistryService;
    private final MorpheusProjectSyncApiService projectSyncService;
    private final MorpheusDiagnosticsApiService diagnosticsService;
    private final MorpheusHistoryApiService historyService;
    private final MorpheusRequirementQueryApiService requirementQueryService;
    private final MorpheusSpecificationQueryApiService specificationQueryService;
    private final MorpheusChangeQueryApiService changeQueryService;

    public MorpheusApiService(Path databasePath) {
        this(databasePath, Optional.empty());
    }

    MorpheusApiService(Path databasePath, AllowedWorkspaceRoots allowedWorkspaceRoots) {
        this(databasePath, Optional.of(Objects.requireNonNull(allowedWorkspaceRoots, "allowedWorkspaceRoots")));
    }

    private MorpheusApiService(Path databasePath, Optional<AllowedWorkspaceRoots> allowedWorkspaceRoots) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        Optional<AllowedWorkspaceRoots> workspaceRoots = Objects.requireNonNull(allowedWorkspaceRoots, "allowedWorkspaceRoots");
        this.projectRegistryService = new MorpheusProjectRegistryApiService(this.databasePath, workspaceRoots);
        this.projectSyncService = new MorpheusProjectSyncApiService(this.databasePath, workspaceRoots);
        this.diagnosticsService = new MorpheusDiagnosticsApiService(this.databasePath);
        this.historyService = new MorpheusHistoryApiService(this.databasePath);
        this.requirementQueryService = new MorpheusRequirementQueryApiService(this.databasePath);
        this.specificationQueryService = new MorpheusSpecificationQueryApiService(this.databasePath);
        this.changeQueryService = new MorpheusChangeQueryApiService(this.databasePath);
    }

    public Object health() {
        return map("status", "UP", "service", "morpheus", "apiVersion", ProductMetadata.API_VERSION);
    }

    public Object version() {
        return map("version", ProductMetadata.version());
    }

    public Object listProjects() {
        return projectRegistryService.listProjects();
    }

    public RegistrationResult registerProject(String workspace) {
        MorpheusProjectRegistryApiService.RegistrationResult result = projectRegistryService.registerProject(workspace);
        return new RegistrationResult(result.project(), result.created());
    }

    public Object project(String projectIdValue) {
        return projectRegistryService.project(projectIdValue);
    }

    MorpheusProjectRegistryApiService projectRegistryService() {
        return projectRegistryService;
    }

    MorpheusProjectSyncApiService projectSyncService() {
        return projectSyncService;
    }

    MorpheusDiagnosticsApiService diagnosticsService() {
        return diagnosticsService;
    }

    MorpheusHistoryApiService historyService() {
        return historyService;
    }

    MorpheusRequirementQueryApiService requirementQueryService() {
        return requirementQueryService;
    }

    MorpheusSpecificationQueryApiService specificationQueryService() {
        return specificationQueryService;
    }

    MorpheusChangeQueryApiService changeQueryService() {
        return changeQueryService;
    }

    public Object sync(String projectIdValue, Optional<String> revision) {
        return projectSyncService.sync(projectIdValue, revision);
    }

    public Object syncStatus(String projectIdValue, long maxAgeMinutes) {
        return projectSyncService.syncStatus(projectIdValue, maxAgeMinutes);
    }

    public Object listSpecifications(String projectIdValue, PageRequest pageRequest) {
        return specificationQueryService.listSpecifications(projectIdValue, pageRequest);
    }

    public Object specification(String projectIdValue, String specificationIdValue) {
        return specificationQueryService.specification(projectIdValue, specificationIdValue);
    }

    public Object specificationContext(
            String projectIdValue,
            String specificationIdValue,
            PageRequest pageRequest) {
        return specificationQueryService.specificationContext(projectIdValue, specificationIdValue, pageRequest);
    }

    public Object requirements(String projectIdValue, String query, PageRequest pageRequest) {
        return requirementQueryService.requirements(projectIdValue, query, pageRequest);
    }

    public Object requirement(String projectIdValue, String requirementIdValue) {
        return requirementQueryService.requirement(projectIdValue, requirementIdValue);
    }

    public Object traceRequirement(String projectIdValue, String requirementIdValue, int depth) {
        return requirementQueryService.traceRequirement(projectIdValue, requirementIdValue, depth);
    }

    public Object listChanges(String projectIdValue, PageRequest pageRequest) {
        return changeQueryService.listChanges(projectIdValue, pageRequest);
    }

    public Object change(String projectIdValue, String changeIdValue) {
        return changeQueryService.change(projectIdValue, changeIdValue);
    }

    public Object constraints(String projectIdValue, String changeIdValue, PageRequest pageRequest) {
        return changeQueryService.constraints(projectIdValue, changeIdValue, pageRequest);
    }

    public Object acceptanceCriteria(String projectIdValue, String changeIdValue) {
        return changeQueryService.acceptanceCriteria(projectIdValue, changeIdValue);
    }

    public Object acceptanceCriteria(
            String projectIdValue,
            String changeIdValue,
            PageRequest pageRequest) {
        return changeQueryService.acceptanceCriteria(projectIdValue, changeIdValue, pageRequest);
    }

    public Object designDecisions(String projectIdValue, String changeIdValue, PageRequest pageRequest) {
        return changeQueryService.designDecisions(projectIdValue, changeIdValue, pageRequest);
    }

    public Object implementationTasks(String projectIdValue, String changeIdValue, PageRequest pageRequest) {
        return changeQueryService.implementationTasks(projectIdValue, changeIdValue, pageRequest);
    }

    public Object changeContext(String projectIdValue, String changeIdValue, int depth) {
        return changeQueryService.changeContext(projectIdValue, changeIdValue, depth);
    }

    public Object changeStatus(String projectIdValue, String changeIdValue) {
        return diagnosticsService.changeStatus(projectIdValue, changeIdValue);
    }

    public Object blockingConditions(String projectIdValue, String changeIdValue) {
        return diagnosticsService.blockingConditions(projectIdValue, changeIdValue);
    }

    public Object versions(String projectIdValue) {
        return historyService.versions(projectIdValue);
    }

    public Object historicalRequirements(
            String projectIdValue,
            String snapshotIdValue,
            PageRequest pageRequest) {
        return historyService.historicalRequirements(projectIdValue, snapshotIdValue, pageRequest);
    }

    public Object compareVersions(String projectIdValue, String sourceIdValue, String targetIdValue) {
        return historyService.compareVersions(projectIdValue, sourceIdValue, targetIdValue);
    }

    public Object diagnostics(String projectIdValue) {
        return diagnosticsService.diagnostics(projectIdValue);
    }

    /** LinkedHashMap keeps construction stable while the canonical serializer sorts JSON keys. */
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

    public record RegistrationResult(Object project, boolean created) {
        public RegistrationResult {
            Objects.requireNonNull(project, "project");
        }
    }
}
