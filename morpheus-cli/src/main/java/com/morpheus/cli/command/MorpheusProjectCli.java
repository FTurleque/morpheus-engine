package com.morpheus.cli.command;

import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.BoundedDiagnostics;
import com.morpheus.application.ingestion.ObservedProjectSnapshotPublisher;
import com.morpheus.application.ingestion.ProjectContentIngestion;
import com.morpheus.application.ingestion.ProjectSnapshotImportResult;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.sync.IncrementalSyncService;
import com.morpheus.application.sync.LocalSourceInventoryScanner;
import com.morpheus.application.sync.SyncPlan;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.provider.openspec.OpenSpecSpecificationContentReader;
import com.morpheus.provider.openspec.OpenSpecSpecificationProvider;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/**
 * The project family of the CLI: {@code projects list|add} and {@code sync}, which reads a workspace through the
 * provider port and publishes it.
 */
public final class MorpheusProjectCli {
    private static final String OPT_PROJECT = "project";
    private static final String OPT_FORCE = "force";
    private static final Set<String> COMMANDS = Set.of("projects", "sync");

    private final OperationalRecorder recorder;

    /** Every command counts what it puts on the database on {@code recorder}, the one the launcher handed. */
    public MorpheusProjectCli(OperationalRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public static boolean handles(String[] args) {
        return COMMANDS.contains(GlobalArgs.command(args));
    }

    public int run(
            String[] args,
            PrintStream out,
            PrintStream err,
            Map<String, String> environment,
            Properties properties) {
        return CliCommandRun.run(args, out, err, environment, properties, (tokens, run) -> {
            List<String> commandTokens = tokens.subList(1, tokens.size());
            return tokens.getFirst().equals("projects") ? projects(commandTokens, run) : sync(commandTokens, run);
        });
    }

    private int projects(List<String> tokens, CliCommandRun run) {
        if (tokens.isEmpty()) {
            throw CliFailure.usage("projects requires subcommand: list | add");
        }
        String subcommand = tokens.getFirst();
        CommandOptions options = CommandOptions.parse(tokens.subList(1, tokens.size()), switch (subcommand) {
            case "list" -> CommandOptions.Declared.nothing();
            case "add" -> CommandOptions.Declared.values("workspace");
            default -> throw CliFailure.usage("unknown projects subcommand: " + subcommand);
        });
        try (CliRuntime runtime = CliRuntime.recovering(run.databasePath(), recorder)) {
            return switch (subcommand) {
                case "list" -> {
                    List<ProjectView> projects = runtime.snapshots().listProjects().stream()
                            .map(item -> new ProjectView(item.id().toString(), item.rootLocator().value()))
                            .toList();
                    if (run.json()) {
                        run.printJson(projects);
                    } else if (projects.isEmpty()) {
                        run.printLine("No projects registered.");
                    } else {
                        projects.forEach(item -> run.printLine(item.projectId() + "\t" + item.workspace()));
                    }
                    yield CliExitCode.SUCCESS.code();
                }
                case "add" -> {
                    Path workspace = ProjectWorkspace.existingDirectory(options.requiredPath("workspace"));
                    SourceLocator root = SourceLocator.file(workspace.toString());
                    Optional<ProjectStoreEntry> existing = runtime.snapshots().findProjectByRoot(root);
                    ProjectStoreEntry project = existing.orElseGet(() -> {
                        ProjectStoreEntry created = new ProjectStoreEntry(ProjectSpecificationId.generate(), root);
                        runtime.snapshots().putProject(created);
                        return created;
                    });
                    ProjectView view = new ProjectView(project.id().toString(), project.rootLocator().value());
                    run.print(view,
                            "projectId=" + view.projectId() + System.lineSeparator() + "workspace=" + view.workspace());
                    yield CliExitCode.SUCCESS.code();
                }
                default -> throw CliFailure.usage("unknown projects subcommand: " + subcommand);
            };
        }
    }

    private int sync(List<String> tokens, CliCommandRun run) {
        CommandOptions options = CommandOptions.parse(tokens,
                CommandOptions.Declared.values(OPT_PROJECT, "revision").andFlags(OPT_FORCE));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required(OPT_PROJECT));
        Optional<String> revision = options.optional("revision");
        boolean force = options.flag(OPT_FORCE);

        try (CliRuntime runtime = CliRuntime.recovering(run.databasePath(), recorder)) {
            Path workspace = ProjectWorkspace.of(runtime, projectId);
            Instant attemptedAt = Instant.now();
            var scan = new LocalSourceInventoryScanner(recorder).scan(
                    workspace,
                    projectId,
                    revision,
                    attemptedAt,
                    OpenSpecSpecificationProvider.SOURCE_DIRECTORIES);
            IncrementalSyncService syncService = new IncrementalSyncService(runtime.syncState(), recorder);
            SyncPlan.Trigger trigger = SyncPlan.Trigger.manual().withoutIncrementalExecutor();
            if (force || runtime.snapshots().activeSnapshot(projectId).isEmpty()) {
                trigger = trigger.forced();
            }
            SyncPlan plan = syncService.prepare(scan, trigger, attemptedAt);
            if (!scan.complete()) {
                throw CliFailure.state("source scan is incomplete: " + scan.failures());
            }

            if (!force && plan.mode() == SyncPlan.SyncMode.INCREMENTAL && !plan.hasSourceChanges()
                    && runtime.snapshots().activeSnapshot(projectId).isPresent()) {
                Instant completedAt = Instant.now();
                syncService.complete(plan, completedAt);
                var active = runtime.snapshots().activeSnapshot(projectId).orElseThrow();
                SyncView view = new SyncView(
                        projectId.toString(),
                        active.id().toString(),
                        plan.mode().name(),
                        plan.fullRebuildReason().map(Enum::name),
                        scan.inventory().orElseThrow().entries().size(),
                        0,
                        0,
                        0,
                        false,
                        BoundedDiagnostics.local(List.of()));
                printSync(run, view);
                return CliExitCode.SUCCESS.code();
            }

            try {
                var normalized = new ProjectContentIngestion(new OpenSpecSpecificationContentReader()).read(
                        workspace, projectId, new PersistentEntityIdentityResolver(runtime.identities()));
                ProjectSnapshotImportResult imported = new ObservedProjectSnapshotPublisher(
                        new ProjectSnapshotImportService(
                                runtime.snapshots(),
                                runtime.requirements(),
                                runtime.content(),
                                runtime.traceability()),
                        recorder)
                        .publishFull(normalized, revision, Instant.now());
                syncService.complete(plan, Instant.now());
                SyncView view = new SyncView(
                        projectId.toString(),
                        imported.snapshot().id().toString(),
                        plan.mode().name(),
                        plan.fullRebuildReason().map(Enum::name),
                        scan.inventory().orElseThrow().entries().size(),
                        imported.requirementCount(),
                        imported.traceabilityLinkCount(),
                        imported.diagnostics().size(),
                        true,
                        BoundedDiagnostics.local(imported.diagnostics()));
                printSync(run, view);
                return CliExitCode.SUCCESS.code();
            } catch (RuntimeException failure) {
                syncService.fail(plan, Instant.now());
                throw failure;
            }
        }
    }

    private static void printSync(CliCommandRun run, SyncView view) {
        List<String> lines = new ArrayList<>(List.of(
                "projectId=" + view.projectId(),
                "snapshotId=" + view.snapshotId(),
                "mode=" + view.mode(),
                "fullRebuildReason=" + view.fullRebuildReason().orElse("none"),
                "sources=" + view.sourceCount(),
                "requirements=" + view.requirementCount(),
                "traceLinks=" + view.traceabilityLinkCount(),
                "diagnostics=" + view.diagnosticCount(),
                "published=" + view.published()));
        lines.addAll(diagnosticLines(view.diagnostics()));
        run.print(view, String.join(System.lineSeparator(), lines));
    }

    /**
     * One line per relayed diagnostic, so the text output names what the count counts -- a skipped requirement is in
     * its details -- then the truncation reason under the name the traversal commands already print.
     */
    static List<String> diagnosticLines(BoundedDiagnostics diagnostics) {
        List<String> lines = new ArrayList<>();
        diagnostics.items().forEach(diagnostic -> lines.add("diagnostic=" + diagnostic.severity()
                + " " + diagnostic.code()
                + " " + diagnostic.source().orElse("-")
                + " " + new TreeMap<>(diagnostic.details())
                + " " + diagnostic.message()));
        diagnostics.truncationReason().ifPresent(reason -> lines.add("truncationReason=" + reason));
        return lines;
    }

    private record ProjectView(String projectId, String workspace) {}
    private record SyncView(
            String projectId,
            String snapshotId,
            String mode,
            Optional<String> fullRebuildReason,
            int sourceCount,
            int requirementCount,
            int traceabilityLinkCount,
            int diagnosticCount,
            boolean published,
            BoundedDiagnostics diagnostics) {}
}
