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
import com.morpheus.application.quality.AcceptanceQualityService;
import com.morpheus.application.quality.ChangeCompletenessService;
import com.morpheus.application.quality.CoverageRatioStatus;
import com.morpheus.application.quality.DecisionReferenceQualityService;
import com.morpheus.application.quality.QualityReport;
import com.morpheus.application.quality.QualityReportService;
import com.morpheus.application.quality.RequirementQualityService;
import com.morpheus.application.quality.TaskQualityService;
import com.morpheus.application.quality.compact.CompactQualityReportService;
import com.morpheus.application.query.BusinessContentQueryService;
import com.morpheus.application.query.ChangeContextQueryService;
import com.morpheus.application.query.SnapshotPage;
import com.morpheus.application.query.compact.CompactQueryViewService;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.sync.IncrementalSyncService;
import com.morpheus.application.sync.LocalSourceInventoryScanner;
import com.morpheus.application.sync.SyncFreshness;
import com.morpheus.application.sync.SyncFreshnessService;
import com.morpheus.application.sync.SyncPlan;
import com.morpheus.cli.command.CliCommandRun;
import com.morpheus.cli.command.CliExitCode;
import com.morpheus.cli.command.CliFailure;
import com.morpheus.cli.command.CliRuntime;
import com.morpheus.cli.command.CommandOptions;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.provider.openspec.OpenSpecProjectContentReader;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
    private static final String OPT_OFFSET = "offset";
    private static final String OPT_LIMIT = "limit";
    private static final String OPT_DEPTH = "depth";
    private static final String KEY_SNAPSHOT_ID = "snapshotId=";
    private static final String KEY_UNKNOWN = "unknown";
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
            case "paths" -> paths(run);
            case "projects" -> projects(tokens, run);
            case "sync" -> sync(tokens, run);
            case "sync-status" -> syncStatus(tokens, run);
            case "changes" -> changes(tokens, run);
            case "constraints" -> constraints(tokens, run);
            case "decisions" -> decisions(tokens, run);
            case "tasks" -> tasks(tokens, run);
            case "change-context" -> changeContext(tokens, run);
            case "analyze-change" -> analyzeChange(tokens, run);
            case "quality" -> quality(tokens, run);
            default -> throw CliFailure.usage("unknown command: " + command);
        };
    }

    private int paths(CliCommandRun run) {
        PathsView view = new PathsView(
                run.layout().dataDirectory().toString(),
                run.layout().configDirectory().toString(),
                run.layout().logsDirectory().toString(),
                run.databasePath().toString());
        run.print(view, String.join(System.lineSeparator(),
                "data=" + view.dataDirectory(),
                "config=" + view.configDirectory(),
                "logs=" + view.logsDirectory(),
                "database=" + view.databasePath()));
        return CliExitCode.SUCCESS.code();
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

    private int syncStatus(List<String> tokens, CliCommandRun run) {
        CommandOptions options = CommandOptions.parse(tokens, Set.of());
        options.rejectUnknown(Set.of(OPT_PROJECT, "max-age-minutes"));
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

    private int changes(List<String> tokens, CliCommandRun run) {
        if (tokens.isEmpty()) {
            throw CliFailure.usage("changes requires subcommand: list | get");
        }
        String subcommand = tokens.getFirst();
        CommandOptions options = CommandOptions.parse(tokens.subList(1, tokens.size()), Set.of());
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required(OPT_PROJECT));
        try (CliRuntime runtime = CliRuntime.recovering(run.databasePath(), recorder)) {
            BusinessContentQueryService service = new BusinessContentQueryService(runtime.snapshots(), runtime.content());
            if (subcommand.equals("list")) {
                options.rejectUnknown(Set.of(OPT_PROJECT, OPT_OFFSET, OPT_LIMIT));
                SnapshotPage<ChangeProposal> page = service.listActiveChanges(projectId, options.page())
                        .orElseThrow(() -> CliFailure.notFound(MSG_PROJECT_NO_ACTIVE_SNAPSHOT + projectId));
                PageView<ChangeView> view = new PageView<>(
                        page.snapshot().id().toString(), page.totalMatches(), page.hasMore(),
                        page.items().stream().map(this::changeView).toList());
                run.printPage(view.snapshotId(), view.totalMatches(), view.hasMore(), view.items(), view, item -> item.id() + "\t" + item.key().orElse("") + "\t" + item.title());
                return CliExitCode.SUCCESS.code();
            }
            if (subcommand.equals("get")) {
                options.rejectUnknown(Set.of(OPT_PROJECT, OPT_CHANGE));
                ChangeId changeId = ChangeId.parse(options.required(OPT_CHANGE));
                var result = service.activeChange(projectId, changeId)
                        .orElseThrow(() -> CliFailure.notFound(MSG_PROJECT_NO_ACTIVE_SNAPSHOT + projectId));
                ChangeProposal change = result.item().orElseThrow(() -> CliFailure.notFound("change not found: " + changeId));
                ChangeView view = changeView(change);
                run.print(view, String.join(System.lineSeparator(),
                        "id=" + view.id(), "key=" + view.key().orElse(""), "title=" + view.title(), "intent=" + view.intent()));
                return CliExitCode.SUCCESS.code();
            }
            throw CliFailure.usage("unknown changes subcommand: " + subcommand);
        }
    }

    private int constraints(List<String> tokens, CliCommandRun run) {
        if (tokens.isEmpty() || !tokens.getFirst().equals("list")) {
            throw CliFailure.usage("constraints requires subcommand: list");
        }
        CommandOptions options = CommandOptions.parse(tokens.subList(1, tokens.size()), Set.of());
        options.rejectUnknown(Set.of(OPT_PROJECT, OPT_CHANGE, OPT_OFFSET, OPT_LIMIT));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required(OPT_PROJECT));
        ChangeId changeId = ChangeId.parse(options.required(OPT_CHANGE));
        try (CliRuntime runtime = CliRuntime.recovering(run.databasePath(), recorder)) {
            SnapshotPage<Constraint> page = new BusinessContentQueryService(runtime.snapshots(), runtime.content())
                    .activeConstraints(projectId, changeId, options.page())
                    .orElseThrow(() -> CliFailure.notFound(MSG_PROJECT_NO_ACTIVE_SNAPSHOT + projectId));
            PageView<ConstraintView> view = new PageView<>(page.snapshot().id().toString(), page.totalMatches(), page.hasMore(),
                    page.items().stream().map(item -> new ConstraintView(item.id().toString(), item.changeId().toString(), item.statement())).toList());
            run.printPage(view.snapshotId(), view.totalMatches(), view.hasMore(), view.items(), view, item -> item.id() + "\t" + item.statement());
            return CliExitCode.SUCCESS.code();
        }
    }

    private int decisions(List<String> tokens, CliCommandRun run) {
        if (tokens.isEmpty() || !tokens.getFirst().equals("list")) {
            throw CliFailure.usage("decisions requires subcommand: list");
        }
        CommandOptions options = CommandOptions.parse(tokens.subList(1, tokens.size()), Set.of());
        options.rejectUnknown(Set.of(OPT_PROJECT, OPT_CHANGE, OPT_OFFSET, OPT_LIMIT));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required(OPT_PROJECT));
        ChangeId changeId = ChangeId.parse(options.required(OPT_CHANGE));
        try (CliRuntime runtime = CliRuntime.recovering(run.databasePath(), recorder)) {
            SnapshotPage<DesignDecision> page = new BusinessContentQueryService(runtime.snapshots(), runtime.content())
                    .activeDesignDecisions(projectId, changeId, options.page())
                    .orElseThrow(() -> CliFailure.notFound(MSG_PROJECT_NO_ACTIVE_SNAPSHOT + projectId));
            PageView<DecisionView> view = new PageView<>(page.snapshot().id().toString(), page.totalMatches(), page.hasMore(),
                    page.items().stream().map(item -> new DecisionView(item.id().toString(), item.changeId().toString(), item.title(), item.decision())).toList());
            run.printPage(view.snapshotId(), view.totalMatches(), view.hasMore(), view.items(), view, item -> item.id() + "\t" + item.title());
            return CliExitCode.SUCCESS.code();
        }
    }

    private int tasks(List<String> tokens, CliCommandRun run) {
        if (tokens.isEmpty() || !tokens.getFirst().equals("list")) {
            throw CliFailure.usage("tasks requires subcommand: list");
        }
        CommandOptions options = CommandOptions.parse(tokens.subList(1, tokens.size()), Set.of());
        options.rejectUnknown(Set.of(OPT_PROJECT, OPT_CHANGE, OPT_OFFSET, OPT_LIMIT));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required(OPT_PROJECT));
        ChangeId changeId = ChangeId.parse(options.required(OPT_CHANGE));
        try (CliRuntime runtime = CliRuntime.recovering(run.databasePath(), recorder)) {
            SnapshotPage<ImplementationTask> page = new BusinessContentQueryService(runtime.snapshots(), runtime.content())
                    .activeImplementationTasks(projectId, changeId, options.page())
                    .orElseThrow(() -> CliFailure.notFound(MSG_PROJECT_NO_ACTIVE_SNAPSHOT + projectId));
            PageView<TaskView> view = new PageView<>(page.snapshot().id().toString(), page.totalMatches(), page.hasMore(),
                    page.items().stream().map(item -> new TaskView(
                            item.id().toString(), item.changeId().toString(), item.key(), item.title(), item.completed())).toList());
            run.printPage(view.snapshotId(), view.totalMatches(), view.hasMore(), view.items(), view, item -> item.id() + "\t" + (item.completed() ? "done" : "todo") + "\t" + item.title());
            return CliExitCode.SUCCESS.code();
        }
    }

    private int changeContext(List<String> tokens, CliCommandRun run) {
        CommandOptions options = CommandOptions.parse(tokens, Set.of());
        options.rejectUnknown(Set.of(OPT_PROJECT, OPT_CHANGE, OPT_DEPTH));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required(OPT_PROJECT));
        ChangeId changeId = ChangeId.parse(options.required(OPT_CHANGE));
        int depth = options.intValue(OPT_DEPTH, 2, 1, 20);
        try (CliRuntime runtime = CliRuntime.recovering(run.databasePath(), recorder)) {
            var result = new ChangeContextQueryService(
                    runtime.snapshots(), runtime.content(), runtime.requirements(), runtime.traceability(), runtime.externalReferences())
                    .active(projectId, changeId, depth, Set.of())
                    .orElseThrow(() -> CliFailure.notFound(MSG_PROJECT_NO_ACTIVE_SNAPSHOT + projectId));
            if (result.change().isEmpty()) {
                throw CliFailure.notFound("change not found: " + changeId);
            }
            var compact = new CompactQueryViewService(runtime.content()).changeContext(result);
            if (run.json()) {
                run.printJson(compact);
            } else {
                run.printLine("change=" + result.change().orElseThrow().title());
                run.printLine("affectedRequirements=" + result.affectedRequirements().size()
                        + " constraints=" + result.constraints().size()
                        + " decisions=" + result.designDecisions().size()
                        + " tasks=" + result.implementationTasks().size()
                        + " links=" + result.subgraph().links().size());
                result.subgraph().truncationReason()
                        .ifPresent(reason -> run.printLine("truncationReason=" + reason));
            }
            return CliExitCode.SUCCESS.code();
        }
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

    private int quality(List<String> tokens, CliCommandRun run) {
        CommandOptions options = CommandOptions.parse(tokens, Set.of());
        options.rejectUnknown(Set.of(OPT_PROJECT));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required(OPT_PROJECT));
        try (CliRuntime runtime = CliRuntime.recovering(run.databasePath(), recorder)) {
            QualityReportService service = new QualityReportService(
                    runtime.snapshots(),
                    new RequirementQualityService(runtime.snapshots(), runtime.requirements(), runtime.traceability()),
                    new TaskQualityService(runtime.snapshots(), runtime.content(), runtime.requirements(), runtime.traceability()),
                    new AcceptanceQualityService(runtime.snapshots(), runtime.content()),
                    new ChangeCompletenessService(runtime.snapshots(), runtime.content(), runtime.requirements(), runtime.traceability()),
                    new DecisionReferenceQualityService(
                            runtime.snapshots(), runtime.content(), runtime.requirements(), runtime.traceability(), runtime.externalReferences()));
            QualityReport report = service.assessActive(projectId)
                    .orElseThrow(() -> CliFailure.notFound(MSG_PROJECT_NO_ACTIVE_SNAPSHOT + projectId));
            if (run.json()) {
                run.printLine(new CompactQualityReportService().toJson(report));
            } else {
                var metrics = report.metrics();
                run.printLine(KEY_SNAPSHOT_ID + report.snapshot().id());
                run.printLine("findings=" + metrics.totalFindings()
                        + " requirements=" + metrics.totalRequirements()
                        + " requirementCoverage="
                        + coverage(metrics.requirementCoverageStatus(), metrics.requirementCoverageRatio())
                        + " tasks=" + metrics.totalTasks()
                        + " taskCoverage=" + coverage(metrics.taskCoverageStatus(), metrics.taskCoverageRatio())
                        + " acceptance=" + metrics.acceptanceCoverageStatus());
            }
            return CliExitCode.SUCCESS.code();
        }
    }

    /** A ratio over an empty population is not a measurement, so it is not printed as a number. */
    private static String coverage(CoverageRatioStatus status, double ratio) {
        return status == CoverageRatioStatus.MEASURED ? Double.toString(ratio) : status.name();
    }

    private ChangeView changeView(ChangeProposal change) {
        return new ChangeView(change.id().toString(), change.key(), change.title(), change.intent(), change.scope(), change.outOfScope(), change.risks());
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
    private record PathsView(String dataDirectory, String configDirectory, String logsDirectory, String databasePath) {}
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
    private record ChangeView(
            String id,
            Optional<String> key,
            String title,
            String intent,
            List<String> scope,
            List<String> outOfScope,
            List<String> risks) {}
    private record ConstraintView(String id, String changeId, String statement) {}
    private record DecisionView(String id, String changeId, String title, String decision) {}
    private record TaskView(String id, String changeId, Optional<String> key, String title, boolean completed) {}
    private record PageView<T>(String snapshotId, int totalMatches, boolean hasMore, List<T> items) {}
}
