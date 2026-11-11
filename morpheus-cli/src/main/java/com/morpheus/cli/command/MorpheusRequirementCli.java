package com.morpheus.cli.command;

import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.RequirementQueryService;
import com.morpheus.application.query.RequirementSearchPage;
import com.morpheus.application.query.RequirementSearchQuery;
import com.morpheus.application.query.TraceRequirementQueryService;
import com.morpheus.application.query.compact.CompactQueryViewService;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.RequirementId;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/** The requirement family of the CLI: {@code requirements find} and {@code trace-requirement}. */
public final class MorpheusRequirementCli {
    private static final String CMD_REQUIREMENTS = "requirements";
    private static final String CMD_TRACE = "trace-requirement";
    private static final String OPT_PROJECT = "project";

    private final OperationalRecorder recorder;

    /** Every command counts what it puts on the database on {@code recorder}, the one the launcher handed. */
    public MorpheusRequirementCli(OperationalRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public static boolean handles(String[] args) {
        String command = GlobalArgs.command(args);
        return command.equals(CMD_REQUIREMENTS) || command.equals(CMD_TRACE);
    }

    public int run(
            String[] args,
            PrintStream out,
            PrintStream err,
            Map<String, String> environment,
            Properties properties) {
        return CliCommandRun.run(args, out, err, environment, properties, (tokens, run) -> {
            List<String> commandTokens = tokens.subList(1, tokens.size());
            return tokens.getFirst().equals(CMD_TRACE)
                    ? traceRequirement(commandTokens, run)
                    : requirements(commandTokens, run);
        });
    }

    private int requirements(List<String> tokens, CliCommandRun run) {
        if (tokens.isEmpty() || !tokens.getFirst().equals("find")) {
            throw CliFailure.usage("requirements requires subcommand: find");
        }
        CommandOptions options = CommandOptions.parse(tokens.subList(1, tokens.size()),
                CommandOptions.Declared.values(OPT_PROJECT, "query", "offset", "limit"));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required(OPT_PROJECT));
        PageRequest pageRequest = options.page();
        RequirementSearchQuery query = new RequirementSearchQuery(options.optional("query").orElse(""));
        try (CliRuntime runtime = CliRuntime.recovering(run.databasePath(), recorder)) {
            RequirementSearchPage result = new RequirementQueryService(runtime.snapshots(), runtime.requirements())
                    .findActive(projectId, query, pageRequest)
                    .orElseThrow(() -> CliFailure.notFound("project has no ACTIVE snapshot: " + projectId));
            List<RequirementView> items = result.items().stream().map(MorpheusRequirementCli::requirementView).toList();
            RequirementSearchView view = new RequirementSearchView(
                    result.snapshot().id().toString(),
                    query.text(),
                    result.totalMatches(),
                    result.hasMore(),
                    items);
            run.printPage(view.snapshotId(), view.totalMatches(), view.hasMore(), items, view,
                    item -> item.id() + "\t" + item.key().orElse("") + "\t" + item.title());
            return CliExitCode.SUCCESS.code();
        }
    }

    private int traceRequirement(List<String> tokens, CliCommandRun run) {
        CommandOptions options = CommandOptions.parse(tokens, CommandOptions.Declared.values(OPT_PROJECT, "requirement", "depth"));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required(OPT_PROJECT));
        RequirementId requirementId = RequirementId.parse(options.required("requirement"));
        int depth = options.intValue("depth", 2, 1, 20);
        try (CliRuntime runtime = CliRuntime.recovering(run.databasePath(), recorder)) {
            var result = new TraceRequirementQueryService(
                    runtime.snapshots(), runtime.requirements(), runtime.traceability(), runtime.externalReferences())
                    .active(projectId, requirementId, depth, Set.of())
                    .orElseThrow(() -> CliFailure.notFound("requirement or ACTIVE snapshot not found: " + requirementId));
            var compact = new CompactQueryViewService(runtime.content()).traceRequirement(result);
            if (run.json()) {
                run.printJson(compact);
            } else {
                run.printLine("requirement=" + result.requirement().entityVersion().content().title());
                run.printLine("nodes=" + result.subgraph().nodes().size() + " links=" + result.subgraph().links().size()
                        + " externalReferences=" + result.externalLinks().size());
                result.subgraph().truncationReason()
                        .ifPresent(reason -> run.printLine("truncationReason=" + reason));
            }
            return CliExitCode.SUCCESS.code();
        }
    }

    private static RequirementView requirementView(RequirementVersionRecord versionRecord) {
        var requirement = versionRecord.entityVersion().content();
        return new RequirementView(
                requirement.id().toString(),
                requirement.key(),
                requirement.title(),
                requirement.statement(),
                requirement.specificationId().toString());
    }

    private record RequirementView(String id, Optional<String> key, String title, String statement, String specificationId) {}
    private record RequirementSearchView(String snapshotId, String query, int totalMatches, boolean hasMore, List<RequirementView> items) {}
}
