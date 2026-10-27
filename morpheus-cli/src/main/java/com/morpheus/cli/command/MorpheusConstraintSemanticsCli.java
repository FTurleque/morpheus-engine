package com.morpheus.cli.command;

import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.query.ConstraintEvaluationQueryService;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.SnapshotPage;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.constraint.ConstraintEvaluation;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Additive M16 CLI surface for read-only constraint-policy evaluation. */
public final class MorpheusConstraintSemanticsCli {
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();
    private final OperationalRecorder recorder;

    /** Every command counts what it puts on the database on {@code recorder}, the one the launcher handed. */
    public MorpheusConstraintSemanticsCli(OperationalRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public static boolean handles(String[] args) {
        List<String> tokens = GlobalArgs.commandTokens(args);
        return tokens.size() >= 2
                && "constraints".equals(tokens.get(0))
                && "evaluate".equals(tokens.get(1));
    }

    public int run(
            String[] args,
            PrintStream out,
            PrintStream err,
            Map<String, String> environment,
            Properties properties) {
        try {
            Parsed parsed = Parsed.parse(args, environment, properties);
            if (parsed.tokens().isEmpty() || !"evaluate".equals(parsed.tokens().getFirst())) {
                throw new IllegalArgumentException("constraints requires action: evaluate");
            }
            CommandOptions options = CommandOptions.parse(parsed.tokens().subList(1, parsed.tokens().size()),
                    CommandOptions.Declared.values("project", "change", "target", "offset", "limit"));
            ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
            ChangeId changeId = ChangeId.parse(options.required("change"));
            ChangeLifecycleState target = lifecycle(options.required("target"));
            PageRequest pageRequest = options.page();

            try (CliRuntime runtime = CliRuntime.recovering(parsed.layout().databasePath(), recorder)) {
                if (runtime.snapshots().findProject(projectId).isEmpty()) {
                    throw new IllegalStateException("project not found: " + projectId);
                }
                SnapshotPage<ConstraintEvaluation> page = new ConstraintEvaluationQueryService(
                                runtime.snapshots(), runtime.content())
                        .activeEvaluations(projectId, changeId, target, pageRequest)
                        .orElseThrow(() -> new IllegalStateException("project has no ACTIVE snapshot: " + projectId));
                write(page, parsed.json(), out);
                return CliExitCode.SUCCESS.code();
            }
        } catch (IllegalArgumentException failure) {
            return CliErrorLine.report(err, CliExitCode.USAGE, failure);
        } catch (RuntimeException failure) {
            return CliErrorLine.report(err, CliExitCode.STATE_ERROR, failure);
        }
    }

    private void write(SnapshotPage<ConstraintEvaluation> page, boolean jsonOutput, PrintStream out) {
        EvaluationPageView result = new EvaluationPageView(
                page.snapshot().id().toString(),
                page.totalMatches(),
                page.hasMore(),
                page.items().stream().map(this::view).toList());
        if (jsonOutput) {
            out.println(json.toJson(result));
            return;
        }
        out.println("snapshotId=" + result.snapshotId()
                + " total=" + result.totalMatches()
                + " hasMore=" + result.hasMore());
        result.items().forEach(item -> out.println(
                item.constraintId() + "\t" + item.state() + "\t" + item.severity() + "\t" + item.reason()));
    }

    private EvaluationView view(ConstraintEvaluation evaluation) {
        return new EvaluationView(
                evaluation.constraintId().toString(),
                evaluation.changeId().toString(),
                evaluation.targetState().name(),
                evaluation.state().name(),
                evaluation.applicability().name(),
                evaluation.severity().name(),
                evaluation.satisfaction().name(),
                evaluation.blockingPolicy().mode().name(),
                evaluation.blockingPolicy().targetStates().stream().map(Enum::name).toList(),
                evaluation.reason(),
                evaluation.supportingEvidenceIds().stream().map(Object::toString).toList(),
                evaluation.sourceEvidenceId().toString());
    }

    private ChangeLifecycleState lifecycle(String value) {
        try {
            return ChangeLifecycleState.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("--target is not a valid MORPHEUS lifecycle state: " + value, failure);
        }
    }

    private record Parsed(boolean json, CliLayout layout, List<String> tokens) {
        private static Parsed parse(String[] args, Map<String, String> environment, Properties properties) {
            GlobalArgs.Parsed global = GlobalArgs.parse(args);
            List<String> remaining = global.remaining();
            if (remaining.isEmpty() || !"constraints".equals(remaining.getFirst())) {
                throw new IllegalArgumentException("constraints command is required");
            }
            return new Parsed(
                    global.json(),
                    CliLayout.resolve(global.dataDirectory(), global.configDirectory(), global.databasePath(),
                            environment, properties),
                    List.copyOf(remaining.subList(1, remaining.size())));
        }
    }

    private record EvaluationPageView(
            String snapshotId,
            int totalMatches,
            boolean hasMore,
            List<EvaluationView> items) {
    }

    private record EvaluationView(
            String constraintId,
            String changeId,
            String targetState,
            String state,
            String applicability,
            String severity,
            String satisfaction,
            String blockingMode,
            List<String> blockingTargets,
            String reason,
            List<String> supportingEvidenceIds,
            String sourceEvidenceId) {
    }
}
