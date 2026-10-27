package com.morpheus.cli.command;

import com.morpheus.application.analysis.ChangeAnalysisService;
import com.morpheus.application.analysis.ChangeAnalysisWarning;
import com.morpheus.application.analysis.ProposedChangeSet;
import com.morpheus.application.analysis.compact.CompactChangeAnalysisViewService;
import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.ProjectContentIngestion;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.query.BusinessContentQueryService;
import com.morpheus.application.query.ChangeContextQueryService;
import com.morpheus.application.query.SnapshotPage;
import com.morpheus.application.query.compact.CompactQueryViewService;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.provider.openspec.OpenSpecSpecificationContentReader;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The change family of the CLI: {@code changes list|get}, {@code constraints list}, {@code decisions list},
 * {@code tasks list}, {@code change-context} and {@code analyze-change}, which reads the proposal from the workspace
 * through the provider port.
 *
 * <p>{@code constraints evaluate} belongs to {@link MorpheusConstraintSemanticsCli}; every other path that starts with
 * {@code constraints} is this class's, so that {@code constraints} alone is answered here with the action it lacks.</p>
 */
public final class MorpheusChangeCli {
    private static final String OPT_PROJECT = "project";
    private static final String OPT_CHANGE = "change";
    private static final String OPT_OFFSET = "offset";
    private static final String OPT_LIMIT = "limit";
    private static final String MSG_PROJECT_NO_ACTIVE_SNAPSHOT = "project has no ACTIVE snapshot: ";
    private static final Set<String> COMMANDS = Set.of(
            "changes", "constraints", "decisions", "tasks", "change-context", "analyze-change");

    private final OperationalRecorder recorder;

    /** Every command counts what it puts on the database on {@code recorder}, the one the launcher handed. */
    public MorpheusChangeCli(OperationalRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public static boolean handles(String[] args) {
        return COMMANDS.contains(GlobalArgs.command(args)) && !MorpheusConstraintSemanticsCli.handles(args);
    }

    public int run(
            String[] args,
            PrintStream out,
            PrintStream err,
            Map<String, String> environment,
            Properties properties) {
        return CliCommandRun.run(args, out, err, environment, properties, (tokens, run) -> {
            List<String> commandTokens = tokens.subList(1, tokens.size());
            return switch (tokens.getFirst()) {
                case "changes" -> changes(commandTokens, run);
                case "constraints" -> constraints(commandTokens, run);
                case "decisions" -> decisions(commandTokens, run);
                case "tasks" -> tasks(commandTokens, run);
                case "analyze-change" -> analyzeChange(commandTokens, run);
                default -> changeContext(commandTokens, run);
            };
        });
    }

    private int changes(List<String> tokens, CliCommandRun run) {
        if (tokens.isEmpty()) {
            throw CliFailure.usage("changes requires subcommand: list | get");
        }
        String subcommand = tokens.getFirst();
        CommandOptions options = CommandOptions.parse(tokens.subList(1, tokens.size()), switch (subcommand) {
            case "list" -> CommandOptions.Declared.values(OPT_PROJECT, OPT_OFFSET, OPT_LIMIT);
            case "get" -> CommandOptions.Declared.values(OPT_PROJECT, OPT_CHANGE);
            default -> throw CliFailure.usage("unknown changes subcommand: " + subcommand);
        });
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required(OPT_PROJECT));
        try (CliRuntime runtime = CliRuntime.recovering(run.databasePath(), recorder)) {
            BusinessContentQueryService service = new BusinessContentQueryService(runtime.snapshots(), runtime.content());
            if (subcommand.equals("list")) {
                SnapshotPage<ChangeProposal> page = service.listActiveChanges(projectId, options.page())
                        .orElseThrow(() -> CliFailure.notFound(MSG_PROJECT_NO_ACTIVE_SNAPSHOT + projectId));
                PageView<ChangeView> view = new PageView<>(
                        page.snapshot().id().toString(), page.totalMatches(), page.hasMore(),
                        page.items().stream().map(MorpheusChangeCli::changeView).toList());
                run.printPage(view.snapshotId(), view.totalMatches(), view.hasMore(), view.items(), view,
                        item -> item.id() + "\t" + item.key().orElse("") + "\t" + item.title());
                return CliExitCode.SUCCESS.code();
            }
            if (subcommand.equals("get")) {
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
        Listing listing = listing("constraints", tokens);
        try (CliRuntime runtime = CliRuntime.recovering(run.databasePath(), recorder)) {
            SnapshotPage<Constraint> page = new BusinessContentQueryService(runtime.snapshots(), runtime.content())
                    .activeConstraints(listing.projectId(), listing.changeId(), listing.options().page())
                    .orElseThrow(() -> CliFailure.notFound(MSG_PROJECT_NO_ACTIVE_SNAPSHOT + listing.projectId()));
            PageView<ConstraintView> view = new PageView<>(page.snapshot().id().toString(), page.totalMatches(), page.hasMore(),
                    page.items().stream().map(item -> new ConstraintView(item.id().toString(), item.changeId().toString(), item.statement())).toList());
            run.printPage(view.snapshotId(), view.totalMatches(), view.hasMore(), view.items(), view,
                    item -> item.id() + "\t" + item.statement());
            return CliExitCode.SUCCESS.code();
        }
    }

    private int decisions(List<String> tokens, CliCommandRun run) {
        Listing listing = listing("decisions", tokens);
        try (CliRuntime runtime = CliRuntime.recovering(run.databasePath(), recorder)) {
            SnapshotPage<DesignDecision> page = new BusinessContentQueryService(runtime.snapshots(), runtime.content())
                    .activeDesignDecisions(listing.projectId(), listing.changeId(), listing.options().page())
                    .orElseThrow(() -> CliFailure.notFound(MSG_PROJECT_NO_ACTIVE_SNAPSHOT + listing.projectId()));
            PageView<DecisionView> view = new PageView<>(page.snapshot().id().toString(), page.totalMatches(), page.hasMore(),
                    page.items().stream().map(item -> new DecisionView(item.id().toString(), item.changeId().toString(), item.title(), item.decision())).toList());
            run.printPage(view.snapshotId(), view.totalMatches(), view.hasMore(), view.items(), view,
                    item -> item.id() + "\t" + item.title());
            return CliExitCode.SUCCESS.code();
        }
    }

    private int tasks(List<String> tokens, CliCommandRun run) {
        Listing listing = listing("tasks", tokens);
        try (CliRuntime runtime = CliRuntime.recovering(run.databasePath(), recorder)) {
            SnapshotPage<ImplementationTask> page = new BusinessContentQueryService(runtime.snapshots(), runtime.content())
                    .activeImplementationTasks(listing.projectId(), listing.changeId(), listing.options().page())
                    .orElseThrow(() -> CliFailure.notFound(MSG_PROJECT_NO_ACTIVE_SNAPSHOT + listing.projectId()));
            PageView<TaskView> view = new PageView<>(page.snapshot().id().toString(), page.totalMatches(), page.hasMore(),
                    page.items().stream().map(item -> new TaskView(
                            item.id().toString(), item.changeId().toString(), item.key(), item.title(), item.completed())).toList());
            run.printPage(view.snapshotId(), view.totalMatches(), view.hasMore(), view.items(), view,
                    item -> item.id() + "\t" + (item.completed() ? "done" : "todo") + "\t" + item.title());
            return CliExitCode.SUCCESS.code();
        }
    }

    /** The options of {@code <command> list}: a project, a change and a page, nothing else. */
    private static Listing listing(String command, List<String> tokens) {
        if (tokens.isEmpty() || !tokens.getFirst().equals("list")) {
            throw CliFailure.usage(command + " requires subcommand: list");
        }
        CommandOptions options = CommandOptions.parse(tokens.subList(1, tokens.size()),
                CommandOptions.Declared.values(OPT_PROJECT, OPT_CHANGE, OPT_OFFSET, OPT_LIMIT));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required(OPT_PROJECT));
        ChangeId changeId = ChangeId.parse(options.required(OPT_CHANGE));
        return new Listing(options, projectId, changeId);
    }

    private int changeContext(List<String> tokens, CliCommandRun run) {
        CommandOptions options = CommandOptions.parse(tokens, CommandOptions.Declared.values(OPT_PROJECT, OPT_CHANGE, "depth"));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required(OPT_PROJECT));
        ChangeId changeId = ChangeId.parse(options.required(OPT_CHANGE));
        int depth = options.intValue("depth", 2, 1, 20);
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
        CommandOptions options = CommandOptions.parse(tokens, CommandOptions.Declared.values(OPT_PROJECT, OPT_CHANGE, "depth"));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required(OPT_PROJECT));
        ChangeId changeId = ChangeId.parse(options.required(OPT_CHANGE));
        int depth = options.intValue("depth", 2, 1, 20);
        try (CliRuntime runtime = CliRuntime.recovering(run.databasePath(), recorder)) {
            Path workspace = ProjectWorkspace.of(runtime, projectId);
            var normalized = new ProjectContentIngestion(new OpenSpecSpecificationContentReader()).read(
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

    private static ChangeView changeView(ChangeProposal change) {
        return new ChangeView(change.id().toString(), change.key(), change.title(), change.intent(), change.scope(), change.outOfScope(), change.risks());
    }

    private record Listing(CommandOptions options, ProjectSpecificationId projectId, ChangeId changeId) {}
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
