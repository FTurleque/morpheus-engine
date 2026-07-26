package com.morpheus.cli;

import com.morpheus.application.query.ConstraintEvaluationQueryService;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.SnapshotPage;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.constraint.ConstraintEvaluation;
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

/** Additive M16 CLI surface for read-only constraint-policy evaluation. */
final class MorpheusConstraintSemanticsCli {
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    static boolean handles(String[] args) {
        List<String> tokens = commandTokens(args);
        return tokens.size() >= 2
                && "constraints".equals(tokens.get(0))
                && "evaluate".equals(tokens.get(1));
    }

    int run(
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
            Options options = Options.parse(parsed.tokens().subList(1, parsed.tokens().size()));
            ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
            ChangeId changeId = ChangeId.parse(options.required("change"));
            ChangeLifecycleState target = lifecycle(options.required("target"));
            PageRequest pageRequest = new PageRequest(
                    options.intValue("offset", 0, 0, Integer.MAX_VALUE),
                    options.intValue("limit", 20, 1, PageRequest.MAX_LIMIT));

            try (CliRuntime runtime = new CliRuntime(parsed.layout().databasePath())) {
                if (runtime.snapshots.findProject(projectId).isEmpty()) {
                    throw new IllegalStateException("project not found: " + projectId);
                }
                SnapshotPage<ConstraintEvaluation> page = new ConstraintEvaluationQueryService(
                                runtime.snapshots, runtime.content)
                        .activeEvaluations(projectId, changeId, target, pageRequest)
                        .orElseThrow(() -> new IllegalStateException("project has no ACTIVE snapshot: " + projectId));
                write(page, parsed.json(), out);
                return CliExitCode.SUCCESS.code();
            }
        } catch (IllegalArgumentException failure) {
            error(err, CliExitCode.USAGE, safeMessage(failure));
            return CliExitCode.USAGE.code();
        } catch (RuntimeException failure) {
            error(err, CliExitCode.STATE_ERROR, safeMessage(failure));
            return CliExitCode.STATE_ERROR.code();
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

    private void error(PrintStream err, CliExitCode code, String message) {
        err.println("MORPHEUS error [" + code.code() + "]: " + message);
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static List<String> commandTokens(String[] args) {
        List<String> remaining = new ArrayList<>();
        for (int index = 0; index < args.length; index++) {
            String token = args[index];
            if ("--json".equals(token)) {
                continue;
            }
            if ("--data-dir".equals(token) || "--config-dir".equals(token) || "--db".equals(token)) {
                index++;
                continue;
            }
            remaining.add(token);
        }
        return List.copyOf(remaining);
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
            if (remaining.isEmpty() || !"constraints".equals(remaining.getFirst())) {
                throw new IllegalArgumentException("constraints command is required");
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

        static Options parse(List<String> tokens) {
            Options result = new Options();
            for (int index = 0; index < tokens.size(); index++) {
                String token = tokens.get(index);
                switch (token) {
                    case "--project", "--change", "--target", "--offset", "--limit" -> {
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

        int intValue(String key, int defaultValue, int min, int max) {
            Optional<String> value = optional(key);
            if (value.isEmpty()) {
                return defaultValue;
            }
            try {
                int parsed = Integer.parseInt(value.orElseThrow());
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
