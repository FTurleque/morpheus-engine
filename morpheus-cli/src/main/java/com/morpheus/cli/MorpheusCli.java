package com.morpheus.cli;

import com.morpheus.application.analysis.ChangeAnalysisService;
import com.morpheus.application.analysis.ProposedChangeSet;
import com.morpheus.application.analysis.compact.CompactChangeAnalysisViewService;
import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.ProjectSnapshotImportResult;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.ingestion.ObservedProjectSnapshotPublisher;
import com.morpheus.application.operability.LocalOperationalRuntime;
import com.morpheus.application.quality.AcceptanceQualityService;
import com.morpheus.application.quality.ChangeCompletenessService;
import com.morpheus.application.quality.DecisionReferenceQualityService;
import com.morpheus.application.quality.QualityReport;
import com.morpheus.application.quality.QualityReportService;
import com.morpheus.application.quality.RequirementQualityService;
import com.morpheus.application.quality.TaskQualityService;
import com.morpheus.application.quality.compact.CompactQualityReportService;
import com.morpheus.application.query.BusinessContentQueryService;
import com.morpheus.application.query.ChangeContextQueryService;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.RequirementQueryService;
import com.morpheus.application.query.RequirementSearchPage;
import com.morpheus.application.query.RequirementSearchQuery;
import com.morpheus.application.query.SnapshotPage;
import com.morpheus.application.query.TraceRequirementQueryService;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.query.compact.CompactQueryViewService;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.sync.IncrementalSyncService;
import com.morpheus.application.sync.LocalSourceInventoryScanner;
import com.morpheus.application.sync.SyncFreshness;
import com.morpheus.application.sync.SyncFreshnessService;
import com.morpheus.application.sync.SyncPlan;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.provider.openspec.OpenSpecProjectContentReader;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/** Stable, scriptable local MORPHEUS command-line adapter. */
public final class MorpheusCli {
    static final String FALLBACK_VERSION = "0.1.0-SNAPSHOT";
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    public static void main(String[] args) {
        int exit = new MorpheusCli().run(args, System.out, System.err, System.getenv(), System.getProperties());
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
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        boolean jsonOutput = false;
        try {
            GlobalOptions global = GlobalOptions.parse(args);
            jsonOutput = global.json();
            CliLayout layout = CliLayout.resolve(
                    global.dataDirectory(),
                    global.configDirectory(),
                    global.databasePath(),
                    environment,
                    properties);
            if (global.tokens().isEmpty()) {
                printHelp(out);
                return CliExitCode.SUCCESS.code();
            }
            String command = global.tokens().getFirst();
            List<String> commandTokens = global.tokens().subList(1, global.tokens().size());
            return dispatch(command, commandTokens, layout, jsonOutput, out);
        } catch (CliFailure failure) {
            printError(err, jsonOutput, failure.exitCode, failure.getMessage());
            return failure.exitCode.code();
        } catch (IllegalArgumentException failure) {
            printError(err, jsonOutput, CliExitCode.USAGE, safeMessage(failure));
            return CliExitCode.USAGE.code();
        } catch (KnowledgeStoreException | IllegalStateException failure) {
            printError(err, jsonOutput, CliExitCode.STATE_ERROR, safeMessage(failure));
            return CliExitCode.STATE_ERROR.code();
        } catch (RuntimeException failure) {
            printError(err, jsonOutput, CliExitCode.INTERNAL_ERROR, safeMessage(failure));
            return CliExitCode.INTERNAL_ERROR.code();
        }
    }

    private int dispatch(
            String command,
            List<String> tokens,
            CliLayout layout,
            boolean jsonOutput,
            PrintStream out) {
        return switch (command) {
            case "help", "--help", "-h" -> {
                printHelp(out);
                yield CliExitCode.SUCCESS.code();
            }
            case "version", "--version" -> {
                print(out, jsonOutput, new VersionView(version()), "MORPHEUS " + version());
                yield CliExitCode.SUCCESS.code();
            }
            case "paths" -> paths(layout, jsonOutput, out);
            case "projects" -> projects(tokens, layout, jsonOutput, out);
            case "sync" -> sync(tokens, layout, jsonOutput, out);
            case "sync-status" -> syncStatus(tokens, layout, jsonOutput, out);
            case "requirements" -> requirements(tokens, layout, jsonOutput, out);
            case "changes" -> changes(tokens, layout, jsonOutput, out);
            case "constraints" -> constraints(tokens, layout, jsonOutput, out);
            case "decisions" -> decisions(tokens, layout, jsonOutput, out);
            case "tasks" -> tasks(tokens, layout, jsonOutput, out);
            case "trace-requirement" -> traceRequirement(tokens, layout, jsonOutput, out);
            case "change-context" -> changeContext(tokens, layout, jsonOutput, out);
            case "analyze-change" -> analyzeChange(tokens, layout, jsonOutput, out);
            case "quality" -> quality(tokens, layout, jsonOutput, out);
            default -> throw usage("unknown command: " + command);
        };
    }

    private int paths(CliLayout layout, boolean jsonOutput, PrintStream out) {
        PathsView view = new PathsView(
                layout.dataDirectory().toString(),
                layout.configDirectory().toString(),
                layout.logsDirectory().toString(),
                layout.databasePath().toString());
        print(out, jsonOutput, view, String.join(System.lineSeparator(),
                "data=" + view.dataDirectory(),
                "config=" + view.configDirectory(),
                "logs=" + view.logsDirectory(),
                "database=" + view.databasePath()));
        return CliExitCode.SUCCESS.code();
    }

    private int projects(List<String> tokens, CliLayout layout, boolean jsonOutput, PrintStream out) {
        if (tokens.isEmpty()) {
            throw usage("projects requires subcommand: list | add");
        }
        String subcommand = tokens.getFirst();
        CommandOptions options = CommandOptions.parse(tokens.subList(1, tokens.size()), Set.of());
        try (CliRuntime runtime = new CliRuntime(layout.databasePath())) {
            return switch (subcommand) {
                case "list" -> {
                    options.rejectUnknown(Set.of());
                    List<ProjectView> projects = runtime.snapshots.listProjects().stream()
                            .map(item -> new ProjectView(item.id().toString(), item.rootLocator().value()))
                            .toList();
                    if (jsonOutput) {
                        out.println(json.toJson(projects));
                    } else if (projects.isEmpty()) {
                        out.println("No projects registered.");
                    } else {
                        projects.forEach(item -> out.println(item.projectId() + "\t" + item.workspace()));
                    }
                    yield CliExitCode.SUCCESS.code();
                }
                case "add" -> {
                    options.rejectUnknown(Set.of("workspace"));
                    Path workspace = existingDirectory(options.requiredPath("workspace"));
                    SourceLocator root = SourceLocator.file(workspace.toString());
                    Optional<ProjectStoreEntry> existing = runtime.snapshots.findProjectByRoot(root);
                    ProjectStoreEntry project = existing.orElseGet(() -> {
                        ProjectStoreEntry created = new ProjectStoreEntry(ProjectSpecificationId.generate(), root);
                        runtime.snapshots.putProject(created);
                        return created;
                    });
                    ProjectView view = new ProjectView(project.id().toString(), project.rootLocator().value());
                    print(out, jsonOutput, view,
                            "projectId=" + view.projectId() + System.lineSeparator() + "workspace=" + view.workspace());
                    yield CliExitCode.SUCCESS.code();
                }
                default -> throw usage("unknown projects subcommand: " + subcommand);
            };
        }
    }

    private int sync(List<String> tokens, CliLayout layout, boolean jsonOutput, PrintStream out) {
        CommandOptions options = CommandOptions.parse(tokens, Set.of("force"));
        options.rejectUnknown(Set.of("project", "revision", "force"));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
        Optional<String> revision = options.optional("revision");
        boolean force = options.flag("force");

        try (CliRuntime runtime = new CliRuntime(layout.databasePath())) {
            Path workspace = projectWorkspace(runtime, projectId);
            Instant attemptedAt = Instant.now();
            var scan = new LocalSourceInventoryScanner().scan(
                    workspace,
                    projectId,
                    revision,
                    attemptedAt,
                    List.of(Path.of("openspec")));
            IncrementalSyncService syncService = new IncrementalSyncService(runtime.syncState);
            SyncPlan.Trigger trigger = SyncPlan.Trigger.manual();
            if (force || runtime.snapshots.activeSnapshot(projectId).isEmpty()) {
                trigger = trigger.forced();
            }
            SyncPlan plan = syncService.prepare(scan, trigger, attemptedAt);
            if (!scan.complete()) {
                throw state("source scan is incomplete: " + scan.failures());
            }

            if (!force && plan.mode() == SyncPlan.SyncMode.INCREMENTAL && !plan.hasSourceChanges()
                    && runtime.snapshots.activeSnapshot(projectId).isPresent()) {
                Instant completedAt = Instant.now();
                syncService.complete(plan, completedAt);
                var active = runtime.snapshots.activeSnapshot(projectId).orElseThrow();
                SyncView view = new SyncView(
                        projectId.toString(),
                        active.id().toString(),
                        plan.mode().name(),
                        plan.fullRebuildReason().map(Enum::name),
                        scan.inventory().orElseThrow().entries().size(),
                        0,
                        0,
                        0,
                        false);
                printSync(out, jsonOutput, view);
                return CliExitCode.SUCCESS.code();
            }

            try {
                var identityResolver = new PersistentEntityIdentityResolver(runtime.identities);
                var normalized = new OpenSpecProjectContentReader().read(workspace, projectId, identityResolver);
                ProjectSnapshotImportResult imported = new ObservedProjectSnapshotPublisher(
                        new ProjectSnapshotImportService(
                                runtime.snapshots,
                                runtime.requirements,
                                runtime.content,
                                runtime.traceability),
                        LocalOperationalRuntime.recorder())
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
                        true);
                printSync(out, jsonOutput, view);
                return CliExitCode.SUCCESS.code();
            } catch (RuntimeException failure) {
                syncService.fail(plan, Instant.now());
                throw failure;
            }
        }
    }

    private void printSync(PrintStream out, boolean jsonOutput, SyncView view) {
        print(out, jsonOutput, view, String.join(System.lineSeparator(),
                "projectId=" + view.projectId(),
                "snapshotId=" + view.snapshotId(),
                "mode=" + view.mode(),
                "fullRebuildReason=" + view.fullRebuildReason().orElse("none"),
                "sources=" + view.sourceCount(),
                "requirements=" + view.requirementCount(),
                "traceLinks=" + view.traceabilityLinkCount(),
                "diagnostics=" + view.diagnosticCount(),
                "published=" + view.published()));
    }

    private int syncStatus(List<String> tokens, CliLayout layout, boolean jsonOutput, PrintStream out) {
        CommandOptions options = CommandOptions.parse(tokens, Set.of());
        options.rejectUnknown(Set.of("project", "max-age-minutes"));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
        long maxAgeMinutes = options.longValue("max-age-minutes", 60L, 1L, 525600L);
        try (CliRuntime runtime = new CliRuntime(layout.databasePath())) {
            SyncFreshness freshness = new SyncFreshnessService(runtime.syncState)
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
            print(out, jsonOutput, view, String.join(System.lineSeparator(),
                    "state=" + view.state(),
                    "lastSuccessfulSyncAt=" + view.lastSuccessfulSyncAt().orElse("unknown"),
                    "ageSeconds=" + view.ageSeconds().map(Object::toString).orElse("unknown"),
                    "sourceRevision=" + view.sourceRevision().orElse("unknown"),
                    "mode=" + view.lastSuccessfulMode().orElse("unknown"),
                    "pendingFullRebuildReason=" + view.pendingFullRebuildReason().orElse("none"),
                    "sourceCount=" + view.currentSourceCount()));
            return CliExitCode.SUCCESS.code();
        }
    }

    private int requirements(List<String> tokens, CliLayout layout, boolean jsonOutput, PrintStream out) {
        if (tokens.isEmpty() || !tokens.getFirst().equals("find")) {
            throw usage("requirements requires subcommand: find");
        }
        CommandOptions options = CommandOptions.parse(tokens.subList(1, tokens.size()), Set.of());
        options.rejectUnknown(Set.of("project", "query", "offset", "limit"));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
        PageRequest pageRequest = page(options);
        RequirementSearchQuery query = new RequirementSearchQuery(options.optional("query").orElse(""));
        try (CliRuntime runtime = new CliRuntime(layout.databasePath())) {
            RequirementSearchPage result = new RequirementQueryService(runtime.snapshots, runtime.requirements)
                    .findActive(projectId, query, pageRequest)
                    .orElseThrow(() -> notFound("project has no ACTIVE snapshot: " + projectId));
            List<RequirementView> items = result.items().stream().map(this::requirementView).toList();
            RequirementSearchView view = new RequirementSearchView(
                    result.snapshot().id().toString(),
                    query.text(),
                    result.totalMatches(),
                    result.hasMore(),
                    items);
            if (jsonOutput) {
                out.println(json.toJson(view));
            } else {
                out.println("snapshotId=" + view.snapshotId() + " total=" + view.totalMatches() + " hasMore=" + view.hasMore());
                items.forEach(item -> out.println(item.id() + "\t" + item.key().orElse("") + "\t" + item.title()));
            }
            return CliExitCode.SUCCESS.code();
        }
    }

    private int changes(List<String> tokens, CliLayout layout, boolean jsonOutput, PrintStream out) {
        if (tokens.isEmpty()) {
            throw usage("changes requires subcommand: list | get");
        }
        String subcommand = tokens.getFirst();
        CommandOptions options = CommandOptions.parse(tokens.subList(1, tokens.size()), Set.of());
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
        try (CliRuntime runtime = new CliRuntime(layout.databasePath())) {
            BusinessContentQueryService service = new BusinessContentQueryService(runtime.snapshots, runtime.content);
            if (subcommand.equals("list")) {
                options.rejectUnknown(Set.of("project", "offset", "limit"));
                SnapshotPage<ChangeProposal> page = service.listActiveChanges(projectId, page(options))
                        .orElseThrow(() -> notFound("project has no ACTIVE snapshot: " + projectId));
                PageView<ChangeView> view = new PageView<>(
                        page.snapshot().id().toString(), page.totalMatches(), page.hasMore(),
                        page.items().stream().map(this::changeView).toList());
                printPage(out, jsonOutput, view, item -> item.id() + "\t" + item.key().orElse("") + "\t" + item.title());
                return CliExitCode.SUCCESS.code();
            }
            if (subcommand.equals("get")) {
                options.rejectUnknown(Set.of("project", "change"));
                ChangeId changeId = ChangeId.parse(options.required("change"));
                var result = service.activeChange(projectId, changeId)
                        .orElseThrow(() -> notFound("project has no ACTIVE snapshot: " + projectId));
                ChangeProposal change = result.item().orElseThrow(() -> notFound("change not found: " + changeId));
                ChangeView view = changeView(change);
                print(out, jsonOutput, view, String.join(System.lineSeparator(),
                        "id=" + view.id(), "key=" + view.key().orElse(""), "title=" + view.title(), "intent=" + view.intent()));
                return CliExitCode.SUCCESS.code();
            }
            throw usage("unknown changes subcommand: " + subcommand);
        }
    }

    private int constraints(List<String> tokens, CliLayout layout, boolean jsonOutput, PrintStream out) {
        if (tokens.isEmpty() || !tokens.getFirst().equals("list")) {
            throw usage("constraints requires subcommand: list");
        }
        CommandOptions options = CommandOptions.parse(tokens.subList(1, tokens.size()), Set.of());
        options.rejectUnknown(Set.of("project", "change", "offset", "limit"));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
        ChangeId changeId = ChangeId.parse(options.required("change"));
        try (CliRuntime runtime = new CliRuntime(layout.databasePath())) {
            SnapshotPage<Constraint> page = new BusinessContentQueryService(runtime.snapshots, runtime.content)
                    .activeConstraints(projectId, changeId, page(options))
                    .orElseThrow(() -> notFound("project has no ACTIVE snapshot: " + projectId));
            PageView<ConstraintView> view = new PageView<>(page.snapshot().id().toString(), page.totalMatches(), page.hasMore(),
                    page.items().stream().map(item -> new ConstraintView(item.id().toString(), item.changeId().toString(), item.statement())).toList());
            printPage(out, jsonOutput, view, item -> item.id() + "\t" + item.statement());
            return CliExitCode.SUCCESS.code();
        }
    }

    private int decisions(List<String> tokens, CliLayout layout, boolean jsonOutput, PrintStream out) {
        if (tokens.isEmpty() || !tokens.getFirst().equals("list")) {
            throw usage("decisions requires subcommand: list");
        }
        CommandOptions options = CommandOptions.parse(tokens.subList(1, tokens.size()), Set.of());
        options.rejectUnknown(Set.of("project", "change", "offset", "limit"));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
        ChangeId changeId = ChangeId.parse(options.required("change"));
        try (CliRuntime runtime = new CliRuntime(layout.databasePath())) {
            SnapshotPage<DesignDecision> page = new BusinessContentQueryService(runtime.snapshots, runtime.content)
                    .activeDesignDecisions(projectId, changeId, page(options))
                    .orElseThrow(() -> notFound("project has no ACTIVE snapshot: " + projectId));
            PageView<DecisionView> view = new PageView<>(page.snapshot().id().toString(), page.totalMatches(), page.hasMore(),
                    page.items().stream().map(item -> new DecisionView(item.id().toString(), item.changeId().toString(), item.title(), item.decision())).toList());
            printPage(out, jsonOutput, view, item -> item.id() + "\t" + item.title());
            return CliExitCode.SUCCESS.code();
        }
    }

    private int tasks(List<String> tokens, CliLayout layout, boolean jsonOutput, PrintStream out) {
        if (tokens.isEmpty() || !tokens.getFirst().equals("list")) {
            throw usage("tasks requires subcommand: list");
        }
        CommandOptions options = CommandOptions.parse(tokens.subList(1, tokens.size()), Set.of());
        options.rejectUnknown(Set.of("project", "change", "offset", "limit"));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
        ChangeId changeId = ChangeId.parse(options.required("change"));
        try (CliRuntime runtime = new CliRuntime(layout.databasePath())) {
            SnapshotPage<ImplementationTask> page = new BusinessContentQueryService(runtime.snapshots, runtime.content)
                    .activeImplementationTasks(projectId, changeId, page(options))
                    .orElseThrow(() -> notFound("project has no ACTIVE snapshot: " + projectId));
            PageView<TaskView> view = new PageView<>(page.snapshot().id().toString(), page.totalMatches(), page.hasMore(),
                    page.items().stream().map(item -> new TaskView(
                            item.id().toString(), item.changeId().toString(), item.key(), item.title(), item.completed())).toList());
            printPage(out, jsonOutput, view, item -> item.id() + "\t" + (item.completed() ? "done" : "todo") + "\t" + item.title());
            return CliExitCode.SUCCESS.code();
        }
    }

    private int traceRequirement(List<String> tokens, CliLayout layout, boolean jsonOutput, PrintStream out) {
        CommandOptions options = CommandOptions.parse(tokens, Set.of());
        options.rejectUnknown(Set.of("project", "requirement", "depth"));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
        RequirementId requirementId = RequirementId.parse(options.required("requirement"));
        int depth = options.intValue("depth", 2, 1, 20);
        try (CliRuntime runtime = new CliRuntime(layout.databasePath())) {
            var result = new TraceRequirementQueryService(
                    runtime.snapshots, runtime.requirements, runtime.traceability, runtime.externalReferences)
                    .active(projectId, requirementId, depth, Set.of())
                    .orElseThrow(() -> notFound("requirement or ACTIVE snapshot not found: " + requirementId));
            var compact = new CompactQueryViewService(runtime.content).traceRequirement(result);
            if (jsonOutput) {
                out.println(json.toJson(compact));
            } else {
                out.println("requirement=" + result.requirement().entityVersion().content().title());
                out.println("nodes=" + result.subgraph().nodes().size() + " links=" + result.subgraph().links().size()
                        + " externalReferences=" + result.externalLinks().size());
            }
            return CliExitCode.SUCCESS.code();
        }
    }

    private int changeContext(List<String> tokens, CliLayout layout, boolean jsonOutput, PrintStream out) {
        CommandOptions options = CommandOptions.parse(tokens, Set.of());
        options.rejectUnknown(Set.of("project", "change", "depth"));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
        ChangeId changeId = ChangeId.parse(options.required("change"));
        int depth = options.intValue("depth", 2, 1, 20);
        try (CliRuntime runtime = new CliRuntime(layout.databasePath())) {
            var result = new ChangeContextQueryService(
                    runtime.snapshots, runtime.content, runtime.requirements, runtime.traceability, runtime.externalReferences)
                    .active(projectId, changeId, depth, Set.of())
                    .orElseThrow(() -> notFound("project has no ACTIVE snapshot: " + projectId));
            if (result.change().isEmpty()) {
                throw notFound("change not found: " + changeId);
            }
            var compact = new CompactQueryViewService(runtime.content).changeContext(result);
            if (jsonOutput) {
                out.println(json.toJson(compact));
            } else {
                out.println("change=" + result.change().orElseThrow().title());
                out.println("affectedRequirements=" + result.affectedRequirements().size()
                        + " constraints=" + result.constraints().size()
                        + " decisions=" + result.designDecisions().size()
                        + " tasks=" + result.implementationTasks().size()
                        + " links=" + result.subgraph().links().size());
            }
            return CliExitCode.SUCCESS.code();
        }
    }

    private int analyzeChange(List<String> tokens, CliLayout layout, boolean jsonOutput, PrintStream out) {
        CommandOptions options = CommandOptions.parse(tokens, Set.of());
        options.rejectUnknown(Set.of("project", "change", "depth"));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
        ChangeId changeId = ChangeId.parse(options.required("change"));
        int depth = options.intValue("depth", 2, 1, 20);
        try (CliRuntime runtime = new CliRuntime(layout.databasePath())) {
            Path workspace = projectWorkspace(runtime, projectId);
            var normalized = new OpenSpecProjectContentReader().read(
                    workspace, projectId, new PersistentEntityIdentityResolver(runtime.identities));
            ProposedChangeSet proposal = ProposedChangeSet.from(normalized, changeId);
            var result = new ChangeAnalysisService(
                    runtime.snapshots, runtime.content, runtime.requirements, runtime.traceability)
                    .analyzeActive(proposal, depth)
                    .orElseThrow(() -> notFound("project has no ACTIVE snapshot: " + projectId));
            if (jsonOutput) {
                out.println(new CompactChangeAnalysisViewService().toJson(result));
            } else {
                var summary = result.summary();
                out.println("change=" + result.change().title());
                out.println("requirements=" + summary.affectedRequirements()
                        + " added=" + summary.addedRequirements()
                        + " modified=" + summary.modifiedRequirements()
                        + " removed=" + summary.removedRequirements());
                out.println("dependencies=" + summary.dependencies() + " dependents=" + summary.dependents()
                        + " warnings=" + summary.warnings());
            }
            return CliExitCode.SUCCESS.code();
        }
    }

    private int quality(List<String> tokens, CliLayout layout, boolean jsonOutput, PrintStream out) {
        CommandOptions options = CommandOptions.parse(tokens, Set.of());
        options.rejectUnknown(Set.of("project"));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
        try (CliRuntime runtime = new CliRuntime(layout.databasePath())) {
            QualityReportService service = new QualityReportService(
                    runtime.snapshots,
                    new RequirementQualityService(runtime.snapshots, runtime.requirements, runtime.traceability),
                    new TaskQualityService(runtime.snapshots, runtime.content, runtime.requirements, runtime.traceability),
                    new AcceptanceQualityService(runtime.snapshots, runtime.content),
                    new ChangeCompletenessService(runtime.snapshots, runtime.content, runtime.requirements, runtime.traceability),
                    new DecisionReferenceQualityService(
                            runtime.snapshots, runtime.content, runtime.requirements, runtime.traceability, runtime.externalReferences));
            QualityReport report = service.assessActive(projectId)
                    .orElseThrow(() -> notFound("project has no ACTIVE snapshot: " + projectId));
            if (jsonOutput) {
                out.println(new CompactQualityReportService().toJson(report));
            } else {
                var metrics = report.metrics();
                out.println("snapshotId=" + report.snapshot().id());
                out.println("findings=" + metrics.totalFindings()
                        + " requirements=" + metrics.totalRequirements()
                        + " requirementCoverage=" + metrics.requirementCoverageRatio()
                        + " tasks=" + metrics.totalTasks()
                        + " taskCoverage=" + metrics.taskCoverageRatio()
                        + " acceptance=" + metrics.acceptanceCoverageStatus());
            }
            return CliExitCode.SUCCESS.code();
        }
    }

    private RequirementView requirementView(RequirementVersionRecord record) {
        var requirement = record.entityVersion().content();
        return new RequirementView(
                requirement.id().toString(),
                requirement.key(),
                requirement.title(),
                requirement.statement(),
                requirement.specificationId().toString());
    }

    private ChangeView changeView(ChangeProposal change) {
        return new ChangeView(change.id().toString(), change.key(), change.title(), change.intent(), change.scope(), change.outOfScope(), change.risks());
    }

    private PageRequest page(CommandOptions options) {
        return new PageRequest(options.intValue("offset", 0, 0, Integer.MAX_VALUE), options.intValue("limit", 20, 1, PageRequest.MAX_LIMIT));
    }

    private Path projectWorkspace(CliRuntime runtime, ProjectSpecificationId projectId) {
        ProjectStoreEntry project = runtime.snapshots.findProject(projectId)
                .orElseThrow(() -> notFound("project not found: " + projectId));
        if (!project.rootLocator().scheme().equals("file")) {
            throw state("CLI local workspace requires a file: project root");
        }
        return existingDirectory(Path.of(project.rootLocator().value()));
    }

    private Path existingDirectory(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(absolute)) {
            throw usage("workspace is not a directory: " + absolute);
        }
        return absolute;
    }

    private <T> void printPage(PrintStream out, boolean jsonOutput, PageView<T> view, java.util.function.Function<T, String> human) {
        if (jsonOutput) {
            out.println(json.toJson(view));
            return;
        }
        out.println("snapshotId=" + view.snapshotId() + " total=" + view.totalMatches() + " hasMore=" + view.hasMore());
        view.items().forEach(item -> out.println(human.apply(item)));
    }

    private void print(PrintStream out, boolean jsonOutput, Object value, String human) {
        out.println(jsonOutput ? json.toJson(value) : human);
    }

    private void printError(PrintStream err, boolean jsonOutput, CliExitCode code, String message) {
        if (jsonOutput) {
            err.println(json.toJson(new ErrorView(code.code(), code.name(), message)));
        } else {
            err.println("MORPHEUS error [" + code.code() + "]: " + message);
        }
    }

    private String version() {
        String implementationVersion = MorpheusCli.class.getPackage().getImplementationVersion();
        return implementationVersion == null || implementationVersion.isBlank() ? FALLBACK_VERSION : implementationVersion;
    }

    private void printHelp(PrintStream out) {
        out.print("""
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

    private String safeMessage(Throwable failure) {
        return Optional.ofNullable(failure.getMessage()).filter(value -> !value.isBlank())
                .orElse(failure.getClass().getSimpleName());
    }

    private CliFailure usage(String message) {
        return new CliFailure(CliExitCode.USAGE, message);
    }

    private CliFailure notFound(String message) {
        return new CliFailure(CliExitCode.NOT_FOUND, message);
    }

    private CliFailure state(String message) {
        return new CliFailure(CliExitCode.STATE_ERROR, message);
    }

    private static final class CliFailure extends RuntimeException {
        private final CliExitCode exitCode;

        private CliFailure(CliExitCode exitCode, String message) {
            super(message);
            this.exitCode = Objects.requireNonNull(exitCode, "exitCode");
        }
    }

    private record GlobalOptions(
            boolean json,
            Optional<Path> dataDirectory,
            Optional<Path> configDirectory,
            Optional<Path> databasePath,
            List<String> tokens) {
        private static GlobalOptions parse(String[] args) {
            boolean json = false;
            Optional<Path> data = Optional.empty();
            Optional<Path> config = Optional.empty();
            Optional<Path> database = Optional.empty();
            List<String> remaining = new ArrayList<>();
            for (int index = 0; index < args.length; index++) {
                String token = args[index];
                switch (token) {
                    case "--json" -> json = true;
                    case "--data-dir" -> data = Optional.of(Path.of(requireValue(args, ++index, token)));
                    case "--config-dir" -> config = Optional.of(Path.of(requireValue(args, ++index, token)));
                    case "--db" -> database = Optional.of(Path.of(requireValue(args, ++index, token)));
                    default -> remaining.add(token);
                }
            }
            return new GlobalOptions(json, data, config, database, List.copyOf(remaining));
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length || args[index].startsWith("--")) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return args[index];
        }
    }

    private static final class CommandOptions {
        private final Map<String, String> values;
        private final Set<String> flags;
        private final List<String> positionals;

        private CommandOptions(Map<String, String> values, Set<String> flags, List<String> positionals) {
            this.values = Map.copyOf(values);
            this.flags = Set.copyOf(flags);
            this.positionals = List.copyOf(positionals);
        }

        static CommandOptions parse(List<String> tokens, Set<String> allowedFlags) {
            Map<String, String> values = new HashMap<>();
            Set<String> flags = new HashSet<>();
            List<String> positionals = new ArrayList<>();
            for (int index = 0; index < tokens.size(); index++) {
                String token = tokens.get(index);
                if (!token.startsWith("--")) {
                    positionals.add(token);
                    continue;
                }
                String key = token.substring(2);
                if (key.isBlank()) {
                    throw new IllegalArgumentException("invalid empty option");
                }
                if (allowedFlags.contains(key)) {
                    if (!flags.add(key)) {
                        throw new IllegalArgumentException("duplicate flag: --" + key);
                    }
                    continue;
                }
                if (index + 1 >= tokens.size() || tokens.get(index + 1).startsWith("--")) {
                    throw new IllegalArgumentException("--" + key + " requires a value");
                }
                if (values.putIfAbsent(key, tokens.get(++index)) != null) {
                    throw new IllegalArgumentException("duplicate option: --" + key);
                }
            }
            if (!positionals.isEmpty()) {
                throw new IllegalArgumentException("unexpected positional arguments: " + positionals);
            }
            return new CommandOptions(values, flags, positionals);
        }

        void rejectUnknown(Set<String> allowed) {
            Set<String> unknown = new HashSet<>(values.keySet());
            unknown.addAll(flags);
            unknown.removeAll(allowed);
            if (!unknown.isEmpty()) {
                throw new IllegalArgumentException("unknown options: " + unknown);
            }
        }

        String required(String key) {
            return optional(key).orElseThrow(() -> new IllegalArgumentException("--" + key + " is required"));
        }

        Path requiredPath(String key) {
            return Path.of(required(key));
        }

        Optional<String> optional(String key) {
            return Optional.ofNullable(values.get(key)).map(String::trim).filter(value -> !value.isEmpty());
        }

        boolean flag(String key) {
            return flags.contains(key);
        }

        int intValue(String key, int defaultValue, int min, int max) {
            long value = longValue(key, defaultValue, min, max);
            return Math.toIntExact(value);
        }

        long longValue(String key, long defaultValue, long min, long max) {
            String raw = values.get(key);
            long value;
            try {
                value = raw == null ? defaultValue : Long.parseLong(raw);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("--" + key + " must be an integer", exception);
            }
            if (value < min || value > max) {
                throw new IllegalArgumentException("--" + key + " must be between " + min + " and " + max);
            }
            return value;
        }
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
            boolean published) {}
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
    private record RequirementView(String id, Optional<String> key, String title, String statement, String specificationId) {}
    private record RequirementSearchView(String snapshotId, String query, int totalMatches, boolean hasMore, List<RequirementView> items) {}
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
    private record ErrorView(int exitCode, String code, String message) {}
}
