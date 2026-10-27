package com.morpheus.cli.command;

import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationCommand;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationPolicy;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationResult;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationResultView;
import com.morpheus.application.lifecycle.mutation.ControlledChangeLifecycleMutationService;
import com.morpheus.application.lifecycle.mutation.RegisteredProjectWriteCapabilityResolver;
import com.morpheus.application.operability.OperationalRecorder;
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
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/** M17 side-effect surface kept separate from read-only change-orchestration commands. */
public final class MorpheusControlledLifecycleCli {
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();
    private final OperationalRecorder recorder;

    /** Every command counts what it puts on the database on {@code recorder}, the one the launcher handed. */
    public MorpheusControlledLifecycleCli(OperationalRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public static boolean handles(String[] args) {
        return "lifecycle".equals(GlobalArgs.command(args));
    }

    public int run(
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
            CommandOptions options = CommandOptions.parse(parsed.tokens().subList(1, parsed.tokens().size()),
                    CommandOptions.Declared.values("project", "change", "expected-revision", "to", "idempotency-key",
                            "actor", "abandonment-reason").andFlags("confirm"));
            ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
            ChangeId changeId = ChangeId.parse(options.required("change"));
            ChangeLifecycleState target = lifecycleState(options.required("to"));
            Optional<ChangeAbandonmentReason> abandonmentReason = abandonmentReason(options.optional("abandonment-reason"));
            long expectedRevision = options.requiredLong("expected-revision", 0, Long.MAX_VALUE);
            String actor = options.required("actor");
            ChangeLifecycleIdempotencyKey idempotencyKey =
                    new ChangeLifecycleIdempotencyKey(options.required("idempotency-key"));

            try (CliRuntime runtime = CliRuntime.recovering(parsed.layout().databasePath(), recorder)) {
                if (runtime.snapshots().findProject(projectId).isEmpty()) {
                    throw new IllegalStateException("project not found: " + projectId);
                }
                var providers = new SpecificationProviderRegistry(
                        List.of(new OpenSpecSpecificationProvider()),
                        new ProviderSelectionPolicy());
                var service = new ControlledChangeLifecycleMutationService(
                        new ChangeTransitionEvaluationService(
                                runtime.snapshots(),
                                runtime.content(),
                                runtime.requirements(),
                                runtime.traceability()),
                        runtime.lifecycleMutations(),
                        new RegisteredProjectWriteCapabilityResolver(runtime.snapshots(), providers));
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
                return result.state().successful()
                        ? CliExitCode.SUCCESS.code()
                        : CliExitCode.STATE_ERROR.code();
            }
        } catch (IllegalArgumentException failure) {
            return CliErrorLine.report(err, CliExitCode.USAGE, failure);
        } catch (RuntimeException failure) {
            return CliErrorLine.report(err, CliExitCode.STATE_ERROR, failure);
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

    private record Parsed(boolean json, CliLayout layout, List<String> tokens) {
        private static Parsed parse(String[] args, Map<String, String> environment, Properties properties) {
            GlobalArgs.Parsed global = GlobalArgs.parse(args);
            List<String> remaining = global.remaining();
            if (remaining.isEmpty() || !"lifecycle".equals(remaining.getFirst())) {
                throw new IllegalArgumentException("lifecycle command is required");
            }
            return new Parsed(
                    global.json(),
                    CliLayout.resolve(global.dataDirectory(), global.configDirectory(), global.databasePath(),
                            environment, properties),
                    List.copyOf(remaining.subList(1, remaining.size())));
        }
    }
}
