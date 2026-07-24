package com.morpheus.cli;

import com.morpheus.application.lifecycle.ChangeLifecyclePolicy;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** Additive M14 CLI surface exposing facts and transition decisions without orchestrating actions. */
final class MorpheusJarvisOrchestrationCli {
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    static boolean handles(String[] args) {
        return "change-orchestration".equals(command(args));
    }

    int run(
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
            Options options = Options.parse(parsed.tokens().subList(1, parsed.tokens().size()));
            ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
            ChangeId changeId = ChangeId.parse(options.required("change"));
            try (CliRuntime runtime = new CliRuntime(parsed.layout().databasePath())) {
                if (runtime.snapshots.findProject(projectId).isEmpty()) {
                    throw new IllegalStateException("project not found: " + projectId);
                }
                return switch (action) {
                    case "state" -> state(runtime, parsed, options, projectId, changeId, out);
                    case "transition-check" -> transition(runtime, parsed, options, projectId, changeId, out);
                    default -> throw new IllegalArgumentException("unknown change-orchestration action: " + action);
                };
            }
        } catch (IllegalArgumentException failure) {
            error(err, CliExitCode.USAGE, failure.getMessage());
            return CliExitCode.USAGE.code();
        } catch (RuntimeException failure) {
            error(err, CliExitCode.STATE_ERROR, safeMessage(failure));
            return CliExitCode.STATE_ERROR.code();
        }
    }

    private int state(
            CliRuntime runtime,
            Parsed parsed,
            Options options,
            ProjectSpecificationId projectId,
            ChangeId changeId,
            PrintStream out) {
        options.rejectFlags("allow-backward", "allow-completed-reopen");
        options.rejectKeys("from", "to", "from-abandonment-reason");
        ChangeLifecycleObservation observation = observation(options);
        ChangeOrchestrationState result = new ChangeOrchestrationStateService(
                        runtime.snapshots,
                        runtime.content,
                        runtime.requirements,
                        runtime.traceability,
                        runtime.externalReferences)
                .active(projectId, changeId, observation)
                .orElseThrow(() -> new IllegalStateException("project has no ACTIVE snapshot: " + projectId));
        writeState(result, parsed.json(), out);
        return CliExitCode.SUCCESS.code();
    }

    private int transition(
            CliRuntime runtime,
            Parsed parsed,
            Options options,
            ProjectSpecificationId projectId,
            ChangeId changeId,
            PrintStream out) {
        options.rejectKeys("lifecycle");
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
                        runtime.snapshots,
                        runtime.content,
                        runtime.requirements,
                        runtime.traceability)
                .evaluateActive(projectId, source, target, policy, targetReason)
                .orElseThrow(() -> new IllegalStateException("project has no ACTIVE snapshot: " + projectId));
        writeTransition(result, parsed.json(), out);
        return CliExitCode.SUCCESS.code();
    }

    private ChangeLifecycleObservation observation(Options options) {
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

    private void error(PrintStream err, CliExitCode code, String message) {
        err.println("MORPHEUS error [" + code.code() + "]: " + message);
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static String command(String[] args) {
        for (int index = 0; index < args.length; index++) {
            String token = args[index];
            if ("--json".equals(token)) {
                continue;
            }
            if ("--data-dir".equals(token) || "--config-dir".equals(token) || "--db".equals(token)) {
                index++;
                continue;
            }
            return token;
        }
        return "";
    }

    private record Parsed(boolean json, CliLayout layout, List<String> tokens) {
        private static Parsed parse(String[] args, Map<String, String> environment, Properties properties) {
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
            if (remaining.isEmpty() || !"change-orchestration".equals(remaining.getFirst())) {
                throw new IllegalArgumentException("change-orchestration command is required");
            }
            CliLayout layout = CliLayout.resolve(data, config, database, environment, properties);
            return new Parsed(json, layout, List.copyOf(remaining.subList(1, remaining.size())));
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length || args[index].startsWith("--")) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return args[index];
        }
    }

    private static final class Options {
        private final Map<String, String> values = new LinkedHashMap<>();
        private final Map<String, Boolean> flags = new LinkedHashMap<>();

        static Options parse(List<String> tokens) {
            Options result = new Options();
            for (int index = 0; index < tokens.size(); index++) {
                String token = tokens.get(index);
                switch (token) {
                    case "--allow-backward", "--allow-completed-reopen" -> {
                        String key = token.substring(2);
                        if (result.flags.putIfAbsent(key, true) != null) {
                            throw new IllegalArgumentException("duplicate option: " + token);
                        }
                    }
                    case "--project", "--change", "--lifecycle", "--from", "--to",
                         "--from-abandonment-reason", "--abandonment-reason" -> {
                        String key = token.substring(2);
                        if (result.values.putIfAbsent(key, require(tokens, ++index, token)) != null) {
                            throw new IllegalArgumentException("duplicate option: " + token);
                        }
                    }
                    default -> throw new IllegalArgumentException("unknown option: " + token);
                }
            }
            return result;
        }

        String required(String key) {
            return optional(key).orElseThrow(() -> new IllegalArgumentException("--" + key + " is required"));
        }

        Optional<String> optional(String key) {
            return Optional.ofNullable(values.get(key)).map(String::trim).filter(value -> !value.isEmpty());
        }

        boolean flag(String key) {
            return flags.getOrDefault(key, false);
        }

        void rejectKeys(String... keys) {
            for (String key : keys) {
                if (values.containsKey(key)) {
                    throw new IllegalArgumentException("--" + key + " is not valid for this change-orchestration action");
                }
            }
        }

        void rejectFlags(String... keys) {
            for (String key : keys) {
                if (flags.containsKey(key)) {
                    throw new IllegalArgumentException("--" + key + " is not valid for this change-orchestration action");
                }
            }
        }

        private static String require(List<String> tokens, int index, String option) {
            if (index >= tokens.size() || tokens.get(index).startsWith("--")) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return tokens.get(index);
        }
    }
}
