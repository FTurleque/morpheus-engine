package com.morpheus.cli.command;

import com.morpheus.application.lifecycle.ChangeLifecyclePolicy;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.orchestration.ChangeLifecycleObservation;
import com.morpheus.application.orchestration.ChangeOrchestrationState;
import com.morpheus.application.orchestration.ChangeOrchestrationStateService;
import com.morpheus.application.orchestration.ChangeTransitionEvaluation;
import com.morpheus.application.orchestration.ChangeTransitionEvaluationService;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeAbandonmentReason;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/** Additive M14 CLI surface exposing facts and transition decisions without orchestrating actions. */
public final class MorpheusJarvisOrchestrationCli {
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();
    private final OperationalRecorder recorder;

    /** Every command counts what it puts on the database on {@code recorder}, the one the launcher handed. */
    public MorpheusJarvisOrchestrationCli(OperationalRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public static boolean handles(String[] args) {
        return "change-orchestration".equals(GlobalArgs.command(args));
    }

    public int run(
            String[] args,
            PrintStream out,
            PrintStream err,
            Map<String, String> environment,
            Properties properties) {
        try {
            Parsed parsed = Parsed.parse(args, environment, properties);
            if (parsed.tokens().isEmpty()) {
                throw new IllegalArgumentException("change-orchestration requires action: state | transition-check");
            }
            String action = parsed.tokens().getFirst();
            CommandOptions options = CommandOptions.parse(parsed.tokens().subList(1, parsed.tokens().size()),
                    CommandOptions.Declared.values("project", "change", "lifecycle", "from", "to",
                            "from-abandonment-reason", "abandonment-reason")
                            .andFlags("allow-backward", "allow-completed-reopen"));
            switch (action) {
                case "state" -> notValidForThisAction(options,
                        "allow-backward", "allow-completed-reopen", "from", "to", "from-abandonment-reason");
                case "transition-check" -> notValidForThisAction(options, "lifecycle");
                default -> throw new IllegalArgumentException("unknown change-orchestration action: " + action);
            }
            ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
            ChangeId changeId = ChangeId.parse(options.required("change"));
            try (CliRuntime runtime = CliRuntime.recovering(parsed.layout().databasePath(), recorder)) {
                if (runtime.snapshots().findProject(projectId).isEmpty()) {
                    throw new IllegalStateException("project not found: " + projectId);
                }
                return switch (action) {
                    case "state" -> state(runtime, parsed, options, projectId, changeId, out);
                    case "transition-check" -> transition(runtime, parsed, options, projectId, changeId, out);
                    default -> throw new IllegalArgumentException("unknown change-orchestration action: " + action);
                };
            }
        } catch (IllegalArgumentException failure) {
            return CliErrorLine.report(err, CliExitCode.USAGE, failure);
        } catch (RuntimeException failure) {
            return CliErrorLine.report(err, CliExitCode.STATE_ERROR, failure);
        }
    }

    private int state(
            CliRuntime runtime,
            Parsed parsed,
            CommandOptions options,
            ProjectSpecificationId projectId,
            ChangeId changeId,
            PrintStream out) {
        ChangeLifecycleObservation observation = observation(options);
        ChangeOrchestrationState result = new ChangeOrchestrationStateService(
                        runtime.snapshots(),
                        runtime.content(),
                        runtime.requirements(),
                        runtime.traceability(),
                        runtime.externalReferences())
                .active(projectId, changeId, observation)
                .orElseThrow(() -> new IllegalStateException("project has no ACTIVE snapshot: " + projectId));
        writeState(result, parsed.json(), out);
        return CliExitCode.SUCCESS.code();
    }

    private int transition(
            CliRuntime runtime,
            Parsed parsed,
            CommandOptions options,
            ProjectSpecificationId projectId,
            ChangeId changeId,
            PrintStream out) {
        ChangeLifecycleState from = lifecycleState(options.required("from"), "--from");
        ChangeLifecycleState target = lifecycleState(options.required("to"), "--to");
        Optional<ChangeAbandonmentReason> fromReason = abandonmentReason(
                options.optional("from-abandonment-reason"), "--from-abandonment-reason");
        Optional<ChangeAbandonmentReason> targetReason = abandonmentReason(
                options.optional("abandonment-reason"), "--abandonment-reason");
        ChangeLifecycle source = from == ChangeLifecycleState.ABANDONED
                ? ChangeLifecycle.abandoned(changeId, fromReason.orElseThrow(() ->
                        new IllegalArgumentException("--from-abandonment-reason is required when --from ABANDONED")))
                : ChangeLifecycle.of(changeId, from);
        if (from != ChangeLifecycleState.ABANDONED && fromReason.isPresent()) {
            throw new IllegalArgumentException("--from-abandonment-reason is only valid when --from ABANDONED");
        }
        ChangeLifecyclePolicy policy = new ChangeLifecyclePolicy(
                options.flag("allow-backward"),
                options.flag("allow-completed-reopen"));
        ChangeTransitionEvaluation result = new ChangeTransitionEvaluationService(
                        runtime.snapshots(),
                        runtime.content(),
                        runtime.requirements(),
                        runtime.traceability())
                .evaluateActive(projectId, source, target, policy, targetReason)
                .orElseThrow(() -> new IllegalStateException("project has no ACTIVE snapshot: " + projectId));
        writeTransition(result, parsed.json(), out);
        return CliExitCode.SUCCESS.code();
    }

    private ChangeLifecycleObservation observation(CommandOptions options) {
        Optional<String> lifecycle = options.optional("lifecycle");
        Optional<ChangeAbandonmentReason> reason = abandonmentReason(
                options.optional("abandonment-reason"), "--abandonment-reason");
        if (lifecycle.isEmpty()) {
            if (reason.isPresent()) {
                throw new IllegalArgumentException("--abandonment-reason requires --lifecycle ABANDONED");
            }
            return ChangeLifecycleObservation.unavailable();
        }
        return ChangeLifecycleObservation.callerSupplied(
                lifecycleState(lifecycle.orElseThrow(), "--lifecycle"), reason);
    }

    private void writeState(ChangeOrchestrationState result, boolean jsonOutput, PrintStream out) {
        if (jsonOutput) {
            out.println(json.toJson(result));
            return;
        }
        out.println("snapshotId=" + result.snapshot().id());
        out.println("changeId=" + result.change().id());
        out.println("lifecycleState=" + result.lifecycle().state().map(Enum::name).orElse("UNAVAILABLE"));
        out.println("lifecycleSource=" + result.lifecycle().source().name());
        out.println("missingArtifacts=" + String.join(",", result.missingArtifacts()));
        out.println("unavailableFacts=" + String.join(",", result.unavailableFacts()));
        out.println("unresolvedLinks=" + result.unresolvedLinks().size());
        out.println("nextAllowedTransitions=" + result.nextAllowedTransitions().stream().map(Enum::name).toList());
        out.println("persisted=false");
    }

    private void writeTransition(ChangeTransitionEvaluation result, boolean jsonOutput, PrintStream out) {
        if (jsonOutput) {
            out.println(json.toJson(result));
            return;
        }
        out.println("fromState=" + result.fromState());
        out.println("targetState=" + result.targetState());
        out.println("decision=" + result.state());
        out.println("blockers=" + result.blockers());
        out.println("unavailableRequiredFacts=" + result.unavailableRequiredFacts());
        out.println("reason=" + result.reason());
        out.println("persisted=false");
    }

    private ChangeLifecycleState lifecycleState(String value, String option) {
        try {
            return ChangeLifecycleState.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(option + " is not a valid MORPHEUS lifecycle state: " + value, failure);
        }
    }

    private Optional<ChangeAbandonmentReason> abandonmentReason(Optional<String> value, String option) {
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ChangeAbandonmentReason.valueOf(value.orElseThrow().trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(option + " is not a valid MORPHEUS abandonment reason: " + value.orElseThrow(), failure);
        }
    }

    private record Parsed(boolean json, CliLayout layout, List<String> tokens) {
        private static Parsed parse(String[] args, Map<String, String> environment, Properties properties) {
            GlobalArgs.Parsed global = GlobalArgs.parse(args);
            List<String> remaining = global.remaining();
            if (remaining.isEmpty() || !"change-orchestration".equals(remaining.getFirst())) {
                throw new IllegalArgumentException("change-orchestration command is required");
            }
            CliLayout layout = CliLayout.resolve(
                    global.dataDirectory(), global.configDirectory(), global.databasePath(), environment, properties);
            return new Parsed(global.json(), layout, List.copyOf(remaining.subList(1, remaining.size())));
        }
    }

    private static void notValidForThisAction(CommandOptions options, String... keys) {
        for (String key : keys) {
            if (options.given(key)) {
                throw new IllegalArgumentException("--" + key + " is not valid for this change-orchestration action");
            }
        }
    }
}
