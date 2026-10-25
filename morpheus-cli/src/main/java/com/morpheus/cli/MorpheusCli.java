package com.morpheus.cli;

import com.morpheus.application.analysis.ChangeAnalysisService;
import com.morpheus.application.analysis.ChangeAnalysisWarning;
import com.morpheus.application.analysis.ProposedChangeSet;
import com.morpheus.application.analysis.compact.CompactChangeAnalysisViewService;
import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.BoundedDiagnostics;
import com.morpheus.application.ingestion.ObservedProjectSnapshotPublisher;
import com.morpheus.application.ingestion.ProjectSnapshotImportResult;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.operability.LocalOperationalRuntime;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.product.ProductMetadata;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.sync.IncrementalSyncService;
import com.morpheus.application.sync.LocalSourceInventoryScanner;
import com.morpheus.application.sync.SyncPlan;
import com.morpheus.cli.command.CliCommandRun;
import com.morpheus.cli.command.CliExitCode;
import com.morpheus.cli.command.CliFailure;
import com.morpheus.cli.command.CliRuntime;
import com.morpheus.cli.command.CommandOptions;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.provider.openspec.OpenSpecProjectContentReader;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import java.util.stream.Collectors;

/** Stable, scriptable local MORPHEUS command-line adapter. */
public final class MorpheusCli {
    private static final String OPT_PROJECT = "project";
    private static final String OPT_FORCE = "force";
    private static final String OPT_CHANGE = "change";
    private static final String OPT_DEPTH = "depth";
    private static final String KEY_SNAPSHOT_ID = "snapshotId=";
    private static final String MSG_PROJECT_NO_ACTIVE_SNAPSHOT = "project has no ACTIVE snapshot: ";

    private final OperationalRecorder recorder;

    public MorpheusCli(OperationalRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        int exit = new MorpheusCli(LocalOperationalRuntime.process().recorder())
                .run(args, out, err, System.getenv(), System.getProperties());
        if (exit != 0) {
            System.exit(exit);
        }
    }

    int run(
            String[] args,
            PrintStream out,
            PrintStream err,
            Map<String, String> environment,
            Properties properties) {
        return CliCommandRun.run(args, out, err, environment, properties, (tokens, run) -> {
            if (tokens.isEmpty()) {
                printHelp(run);
                return CliExitCode.SUCCESS.code();
            }
            return dispatch(tokens.getFirst(), tokens.subList(1, tokens.size()), run);
        });
    }

    /** Answers an invocation whose command path is not declared, the way every unknown command is answered. */
    int refuseUndeclared(String[] args, PrintStream err, String message) {
        return CliCommandRun.refuseUndeclared(args, err, message);
    }

    private int dispatch(String command, List<String> tokens, CliCommandRun run) {
        return switch (command) {
            case "help", "--help", "-h" -> {
                printHelp(run);
                yield CliExitCode.SUCCESS.code();
            }
            case "version", "--version" -> {
                run.print(new VersionView(version()), "MORPHEUS " + version());
                yield CliExitCode.SUCCESS.code();
            }
            case "projects" -> projects(tokens, run);
            case "sync" -> sync(tokens, run);
            case "analyze-change" -> analyzeChange(tokens, run);
            default -> throw CliFailure.usage("unknown command: " + command);
        };
    }

    private int projects(List<String> tokens, CliCommandRun run) {
        if (tokens.isEmpty()) {
            throw CliFailure.usage("projects requires subcommand: list | add");
        }
        String subcommand = tokens.getFirst();
        CommandOptions options = CommandOptions.parse(tokens.subList(1, tokens.size()), Set.of());
        try (CliRuntime runtime = CliRuntime.recovering(run.databasePath(), recorder)) {
            return switch (subcommand) {
                case "list" -> {
                    options.rejectUnknown(Set.of());
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
                    options.rejectUnknown(Set.of("workspace"));
                    Path workspace = existingDirectory(options.requiredPath("workspace"));
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
        CommandOptions options = CommandOptions.parse(tokens, Set.of(OPT_FORCE));
        options.rejectUnknown(Set.of(OPT_PROJECT, "revision", OPT_FORCE));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required(OPT_PROJECT));
        Optional<String> revision = options.optional("revision");
        boolean force = options.flag(OPT_FORCE);

        try (CliRuntime runtime = CliRuntime.recovering(run.databasePath(), recorder)) {
            Path workspace = projectWorkspace(runtime, projectId);
            Instant attemptedAt = Instant.now();
            var scan = new LocalSourceInventoryScanner(recorder).scan(
                    workspace,
                    projectId,
                    revision,
                    attemptedAt,
                    List.of(Path.of("openspec")));
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
                var identityResolver = new PersistentEntityIdentityResolver(runtime.identities());
                var normalized = new OpenSpecProjectContentReader().read(workspace, projectId, identityResolver);
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

    private void printSync(CliCommandRun run, SyncView view) {
        List<String> lines = new ArrayList<>(List.of(
                "projectId=" + view.projectId(),
                KEY_SNAPSHOT_ID + view.snapshotId(),
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

    private int analyzeChange(List<String> tokens, CliCommandRun run) {
        CommandOptions options = CommandOptions.parse(tokens, Set.of());
        options.rejectUnknown(Set.of(OPT_PROJECT, OPT_CHANGE, OPT_DEPTH));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required(OPT_PROJECT));
        ChangeId changeId = ChangeId.parse(options.required(OPT_CHANGE));
        int depth = options.intValue(OPT_DEPTH, 2, 1, 20);
        try (CliRuntime runtime = CliRuntime.recovering(run.databasePath(), recorder)) {
            Path workspace = projectWorkspace(runtime, projectId);
            var normalized = new OpenSpecProjectContentReader().read(
                    workspace, projectId, new PersistentEntityIdentityResolver(runtime.identities()));
            ProposedChangeSet proposal = ProposedChangeSet.from(normalized, changeId);
            var result = new ChangeAnalysisService(
                    runtime.snapshots(), runtime.content(), runtime.requirements(), runtime.traceability())
                    .analyzeActive(proposal, depth)
                    .orElseThrow(() -> CliFailure.notFound(MSG_PROJECT_NO_ACTIVE_SNAPSHOT + projectId));
            if (run.json()) {
                run.printLine(new CompactChangeAnalysisViewService().toJson(result));
            } else {
                var summary = result.summary();
                run.printLine("change=" + result.change().title());
                run.printLine("requirements=" + summary.affectedRequirements()
                        + " added=" + summary.addedRequirements()
                        + " modified=" + summary.modifiedRequirements()
                        + " removed=" + summary.removedRequirements());
                run.printLine("dependencies=" + summary.dependencies() + " dependents=" + summary.dependents()
                        + " warnings=" + summary.warnings());
                analysisWarningLines(result.warnings()).forEach(run::printLine);
            }
            return CliExitCode.SUCCESS.code();
        }
    }

    /**
     * Names what the warning count counts. The count alone never said which warnings it held -- and it is never below
     * one, since acceptance criteria are always reported unavailable -- while a truncated dependency traversal existed
     * only as one of them: the text output lost the truncation the JSON output carried. One code per warning, in the
     * result's order, then the distinct truncation reasons under the name the two sibling commands already print.
     */
    static List<String> analysisWarningLines(List<ChangeAnalysisWarning> warnings) {
        List<String> lines = new ArrayList<>();
        lines.add("warningCodes=" + warnings.stream().map(warning -> warning.code().name()).collect(Collectors.joining(",")));
        warnings.stream()
                .map(warning -> warning.details().get(ChangeAnalysisWarning.TRUNCATION_REASON))
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(reason -> lines.add("truncationReason=" + reason));
        return lines;
    }

    private Path projectWorkspace(CliRuntime runtime, ProjectSpecificationId projectId) {
        ProjectStoreEntry project = runtime.snapshots().findProject(projectId)
                .orElseThrow(() -> CliFailure.notFound("project not found: " + projectId));
        if (!project.rootLocator().scheme().equals("file")) {
            throw CliFailure.state("CLI local workspace requires a file: project root");
        }
        return existingDirectory(Path.of(project.rootLocator().value()));
    }

    private Path existingDirectory(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(absolute)) {
            throw CliFailure.usage("workspace is not a directory: " + absolute);
        }
        return absolute;
    }

    private String version() {
        return ProductMetadata.version();
    }

    private void printHelp(CliCommandRun run) {
        run.printText("""
                MORPHEUS — Specification & Intent Intelligence Engine

                Usage:
                  morpheus [--json] [--data-dir PATH] [--config-dir PATH] [--db PATH] <command>

                Commands:
                  help
                  version
                  paths
                  projects list
                  projects add --workspace PATH
                  sync --project ID [--revision REV] [--force]
                  sync-status --project ID [--max-age-minutes N]
                  requirements find --project ID [--query TEXT] [--offset N] [--limit N]
                  changes list --project ID [--offset N] [--limit N]
                  changes get --project ID --change ID
                  constraints list --project ID --change ID [--offset N] [--limit N]
                  decisions list --project ID --change ID [--offset N] [--limit N]
                  tasks list --project ID --change ID [--offset N] [--limit N]
                  trace-requirement --project ID --requirement ID [--depth N]
                  change-context --project ID --change ID [--depth N]
                  analyze-change --project ID --change ID [--depth N]
                  quality --project ID

                Environment overrides:
                  MORPHEUS_DATA_DIR
                  MORPHEUS_CONFIG_DIR
                  MORPHEUS_DB

                Exit codes:
                  0 success
                  2 usage / invalid arguments
                  3 requested project/entity/snapshot not found
                  4 persisted state or synchronization error
                  5 I/O error
                 10 unexpected internal error
                """);
    }

    private record VersionView(String version) {}
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
