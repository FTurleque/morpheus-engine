package com.morpheus.mcp;

import com.morpheus.application.foundation.PayloadMap;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.sync.SyncFreshness;
import com.morpheus.application.sync.SyncFreshnessService;
import com.morpheus.domain.project.ProjectSpecificationId;
import io.modelcontextprotocol.server.McpServerFeatures;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The synchronisation family of the MCP server: the persisted freshness of one project. */
public final class MorpheusSyncStatusMcpTools {
    private final Path databasePath;
    private final OperationalRecorder recorder;

    public MorpheusSyncStatusMcpTools(Path databasePath, OperationalRecorder recorder) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public List<McpServerFeatures.SyncToolSpecification> specifications() {
        return List.of(
                tool("get_sync_status", MorpheusSyncStatusMcpTools::syncStatus));
    }

    private McpServerFeatures.SyncToolSpecification tool(String name, McpCatalogTool.Answer answer) {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(McpCatalogTool.definition(name))
                .callHandler((exchange, request) -> McpCatalogTool.answer(databasePath, recorder, request.arguments(), answer))
                .build();
    }

    private static Object syncStatus(MorpheusMcpRuntime runtime, Map<String, Object> arguments) {
        ProjectSpecificationId projectId = McpCatalogTool.projectId(arguments);
        long maxAgeMinutes = McpArguments.optionalInteger(arguments, "maxAgeMinutes", MorpheusMcpToolCatalog.DEFAULT_MAX_AGE_MINUTES,
                1L, MorpheusMcpToolCatalog.MAX_MAX_AGE_MINUTES);
        SyncFreshness freshness = new SyncFreshnessService(runtime.syncState())
                .assess(projectId, Instant.now(), Duration.ofMinutes(maxAgeMinutes));
        return PayloadMap.of(
                "projectId", projectId.toString(),
                "state", freshness.state().name(),
                "lastSuccessfulSyncAt", freshness.lastSuccessfulSyncAt().map(Instant::toString).orElse("unknown"),
                "ageSeconds", freshness.ageSinceSuccessfulSync().map(Duration::toSeconds).map(Object::toString).orElse("unknown"),
                "lastObservedChangeAt", freshness.lastObservedChangeAt().map(Instant::toString).orElse("unknown"),
                "sourceRevision", freshness.sourceRevision().orElse("unknown"),
                "lastSuccessfulMode", freshness.lastSuccessfulMode().map(Enum::name).orElse("unknown"),
                "pendingFullRebuildReason", freshness.pendingFullRebuildReason().map(Enum::name).orElse("none"),
                "currentSourceCount", freshness.currentSourceCount());
    }
}
