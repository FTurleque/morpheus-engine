package com.morpheus.cli;

import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationCommand;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationPolicy;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationResult;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationResultState;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationResultView;
import com.morpheus.application.lifecycle.mutation.ControlledChangeLifecycleMutationService;
import com.morpheus.application.lifecycle.mutation.RegisteredProjectWriteCapabilityResolver;
import com.morpheus.application.orchestration.ChangeTransitionEvaluationService;
import com.morpheus.application.provider.ProviderSelectionPolicy;
import com.morpheus.application.provider.SpecificationProviderRegistry;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeAbandonmentReason;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleIdempotencyKey;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleMutationId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleRevision;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.provider.openspec.OpenSpecSpecificationProvider;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** M17 side-effect surface kept separate from read-only change-orchestration commands. */
final class MorpheusControlledLifecycleCli {
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    static boolean handles(String[] args) {
        return "lifecycle".equals(command(args));
    }

    int run(
            String[] args,
            PrintStream out,
            PrintStream err,
            Map<String, String> environment,
            Properties properties) {
        try {
            Parsed parsed = Parsed.parse(args, environment, properties);
            if (parsed.tokens().isEmpty() || !"apply".equals(parsed.tokens().getFirst())) {
                throw new IllegalArgumentException("lifecycle requires action: apply");
            }
            Options options = Options.parse(parsed.tokens().subList(1, parsed.tokens().size()));
            ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
            ChangeId changeId = ChangeId.parse(options.required("change"));
            ChangeLifecycleState target = lifecycleState(options.required("to"));
            Optional<ChangeAbandonmentReason> abandonmentReason = abandonmentReason(options.optional("abandonment-reason"));
            long expectedRevision = options.longValue("expected-revision", 0, Long.MAX_VALUE);
            String actor = options.required("actor");
            ChangeLifecycleIdempotencyKey idempotencyKey =
                    new ChangeLifecycleIdempotencyKey(options.required("idempotency-key"));

            try (CliRuntime runtime = new CliRuntime(parsed.layout().databasePath())) {
                if (runtime.snapshots.findProject(projectId).isEmpty()) {
                    throw new IllegalStateException("project not found: " + projectId);
                }
                var providers = new SpecificationProviderRegistry(
                        List.of(new OpenSpecSpecificationProvider()),
                        new ProviderSelectionPolicy());
                var service = new ControlledChangeLifecycleMutationService(
                        new ChangeTransitionEvaluationService(
                                runtime.snapshots,
                                runtime.content,
                                runtime.requirements,
                                runtime.traceability),
                        runtime.lifecycleMutations,
                        new RegisteredProjectWriteCapabilityResolver(runtime.snapshots, providers));
                ChangeLifecycleMutationResult result = service.apply(
                        new ChangeLifecycleMutationCommand(
                                ChangeLifecycleMutationId.generate(),
                                idempotencyKey,
                                projectId,
                                changeId,
                                new ChangeLifecycleRevision(expectedRevision),
                                target,
                                abandonmentReason,
                                options.flag("confirm"),
                                actor,
                                Instant.now()),
                        ChangeLifecycleMutationPolicy.strict());
                write(result, parsed.json(), out);
                return result.state() == ChangeLifecycleMutationResultState.APPLIED
                                || result.state() == ChangeLifecycleMutationResultState.ALREADY_APPLIED
                        ? CliExitCode.SUCCESS.code()
                        : CliExitCode.STATE_ERROR.code();
            }
        } catch (IllegalArgumentException failure) {
            err.println("MORPHEUS error [" + CliExitCode.USAGE.code() + "]: " + safeMessage(failure));
            return CliExitCode.USAGE.code();
        } catch (RuntimeException failure) {
            err.println("MORPHEUS error [" + CliExitCode.STATE_ERROR.code() + "]: " + safeMessage(failure));
            return CliExitCode.STATE_ERROR.code();
        }
    }

    private void write(ChangeLifecycleMutationResult result, boolean jsonOutput, PrintStream out) {
        ChangeLifecycleMutationResultView view = ChangeLifecycleMutationResultView.from(result);
        if (jsonOutput) {
            out.println(json.toJson(view));
            return;
        }
        out.println("mutationState=" + view.state());
        out.println("reason=" + view.reason());
        view.lifecycleState().ifPresent(state -> {
            out.println("lifecycleState=" + state.lifecycleState());
            out.println("revision=" + state.revision());
        });
        view.audit().ifPresent(audit -> {
            out.println("mutationId=" + audit.mutationId());
            out.println("providerId=" + audit.providerId());
            out.println("appliedAt=" + audit.appliedAt());
        });
    }

    private ChangeLifecycleState lifecycleState(String value) {
        try {
            return ChangeLifecycleState.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("--to is not a valid MORPHEUS lifecycle state: " + value, failure);
        }
    }

    private Optional<ChangeAbandonmentReason> abandonmentReason(Optional<String> value) {
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ChangeAbandonmentReason.valueOf(value.orElseThrow().trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("--abandonment-reason is invalid: " + value.orElseThrow(), failure);
        }
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

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
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
            if (remaining.isEmpty() || !"lifecycle".equals(remaining.getFirst())) {
                throw new IllegalArgumentException("lifecycle command is required");
            }
            return new Parsed(
                    json,
                    CliLayout.resolve(data, config, database, environment, properties),
                    List.copyOf(remaining.subList(1, remaining.size())));
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
        private boolean confirm;

        static Options parse(List<String> tokens) {
            Options result = new Options();
            for (int index = 0; index < tokens.size(); index++) {
                String token = tokens.get(index);
                switch (token) {
                    case "--project", "--change", "--expected-revision", "--to", "--idempotency-key", "--actor", "--abandonment-reason" -> {
                        String key = token.substring(2);
                        if (result.values.putIfAbsent(key, require(tokens, ++index, token)) != null) {
                            throw new IllegalArgumentException("duplicate option: " + token);
                        }
                    }
                    case "--confirm" -> {
                        if (result.confirm) {
                            throw new IllegalArgumentException("duplicate option: --confirm");
                        }
                        result.confirm = true;
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
            return "confirm".equals(key) && confirm;
        }

        long longValue(String key, long min, long max) {
            String value = required(key);
            try {
                long parsed = Long.parseLong(value);
                if (parsed < min || parsed > max) {
                    throw new IllegalArgumentException("--" + key + " must be between " + min + " and " + max);
                }
                return parsed;
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException("--" + key + " must be an integer", failure);
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
