package com.morpheus.cli.command;

import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.sync.SyncFreshness;
import com.morpheus.application.sync.SyncFreshnessService;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/** The synchronisation-status family of the CLI: {@code sync-status}, the persisted freshness of one project. */
public final class MorpheusSyncStatusCli {
    private static final String COMMAND = "sync-status";
    private static final String OPT_PROJECT = "project";
    private static final String KEY_UNKNOWN = "unknown";

    private final OperationalRecorder recorder;

    /** Every command counts what it puts on the database on {@code recorder}, the one the launcher handed. */
    public MorpheusSyncStatusCli(OperationalRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public static boolean handles(String[] args) {
        return GlobalArgs.command(args).equals(COMMAND);
    }

    public int run(
            String[] args,
            PrintStream out,
            PrintStream err,
            Map<String, String> environment,
            Properties properties) {
        return CliCommandRun.run(args, out, err, environment, properties,
                (tokens, run) -> syncStatus(tokens.subList(1, tokens.size()), run));
    }

    private int syncStatus(List<String> tokens, CliCommandRun run) {
        CommandOptions options = CommandOptions.parse(tokens, CommandOptions.Declared.values(OPT_PROJECT, "max-age-minutes"));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required(OPT_PROJECT));
        long maxAgeMinutes = options.longValue("max-age-minutes", 60L, 1L, 525600L);
        try (CliRuntime runtime = CliRuntime.recovering(run.databasePath(), recorder)) {
            SyncFreshness freshness = new SyncFreshnessService(runtime.syncState())
                    .assess(projectId, Instant.now(), Duration.ofMinutes(maxAgeMinutes));
            SyncStatusView view = new SyncStatusView(
                    projectId.toString(),
                    freshness.state().name(),
                    freshness.lastSuccessfulSyncAt().map(Instant::toString),
                    freshness.ageSinceSuccessfulSync().map(Duration::toSeconds),
                    freshness.lastObservedChangeAt().map(Instant::toString),
                    freshness.sourceRevision(),
                    freshness.lastSuccessfulMode().map(Enum::name),
                    freshness.pendingFullRebuildReason().map(Enum::name),
                    freshness.currentSourceCount());
            run.print(view, String.join(System.lineSeparator(),
                    "state=" + view.state(),
                    "lastSuccessfulSyncAt=" + view.lastSuccessfulSyncAt().orElse(KEY_UNKNOWN),
                    "ageSeconds=" + view.ageSeconds().map(Object::toString).orElse(KEY_UNKNOWN),
                    "sourceRevision=" + view.sourceRevision().orElse(KEY_UNKNOWN),
                    "mode=" + view.lastSuccessfulMode().orElse(KEY_UNKNOWN),
                    "pendingFullRebuildReason=" + view.pendingFullRebuildReason().orElse("none"),
                    "sourceCount=" + view.currentSourceCount()));
            return CliExitCode.SUCCESS.code();
        }
    }

    private record SyncStatusView(
            String projectId,
            String state,
            Optional<String> lastSuccessfulSyncAt,
            Optional<Long> ageSeconds,
            Optional<String> lastObservedChangeAt,
            Optional<String> sourceRevision,
            Optional<String> lastSuccessfulMode,
            Optional<String> pendingFullRebuildReason,
            int currentSourceCount) {}
}
