package com.morpheus.cli;

import com.morpheus.application.context.AugmentedContextResult;
import com.morpheus.application.context.AugmentedContextService;
import com.morpheus.application.context.TechnicalContextOptions;
import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.RequirementId;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/** Additive M13 CLI surface; NEXUS selection/ranking stays behind TechnicalContextProvider. */
final class MorpheusAugmentedContextCli {
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();
    private final TechnicalContextProvider provider;
    private final ExternalIntegrationStatusProvider nexusStatus;

    MorpheusAugmentedContextCli(
            TechnicalContextProvider provider,
            ExternalIntegrationStatusProvider nexusStatus) {
        this.provider = java.util.Objects.requireNonNull(provider, "provider");
        this.nexusStatus = java.util.Objects.requireNonNull(nexusStatus, "nexusStatus");
    }

    static boolean handles(String[] args) {
        String command = command(args);
        return "nexus-status".equals(command) || "augmented-context".equals(command);
    }

    int run(
            String[] args,
            PrintStream out,
            PrintStream err,
            Map<String, String> environment,
            Properties properties) {
        try {
            Parsed parsed = Parsed.parse(args, environment, properties);
            return switch (parsed.command()) {
                case "nexus-status" -> status(parsed, out);
                case "augmented-context" -> augmented(parsed, out);
                default -> throw new IllegalArgumentException("unsupported augmented-context command: " + parsed.command());
            };
        } catch (IllegalArgumentException failure) {
            error(err, CliExitCode.USAGE, failure.getMessage());
            return CliExitCode.USAGE.code();
        } catch (RuntimeException failure) {
            error(err, CliExitCode.STATE_ERROR, safeMessage(failure));
            return CliExitCode.STATE_ERROR.code();
        }
    }

    private int status(Parsed parsed, PrintStream out) {
        if (!parsed.tokens().isEmpty()) {
            throw new IllegalArgumentException("nexus-status does not accept command options");
        }
        var status = nexusStatus.status();
        if (parsed.json()) {
            out.println(json.toJson(status));
        } else {
            out.println("system=" + status.system());
            out.println("state=" + status.state());
            out.println("configured=" + status.configured());
            out.println("message=" + status.message());
            status.details().forEach((key, value) -> out.println(key + "=" + value));
        }
        return CliExitCode.SUCCESS.code();
    }

    private int augmented(Parsed parsed, PrintStream out) {
        if (parsed.tokens().isEmpty()) {
            throw new IllegalArgumentException("augmented-context requires subject: requirement | change");
        }
        String subject = parsed.tokens().getFirst();
        ContextOptions options = ContextOptions.parse(parsed.tokens().subList(1, parsed.tokens().size()));
        options.validateSubject(subject);
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
        TechnicalContextOptions technical = options.technical();

        try (CliRuntime runtime = new CliRuntime(parsed.layout().databasePath())) {
            if (runtime.snapshots.findProject(projectId).isEmpty()) {
                throw new IllegalStateException("project not found: " + projectId);
            }
            AugmentedContextService service = new AugmentedContextService(
                    runtime.snapshots,
                    runtime.content,
                    runtime.requirements,
                    runtime.traceability,
                    runtime.externalReferences,
                    provider);
            AugmentedContextResult result = switch (subject) {
                case "requirement" -> service.requirement(
                                projectId,
                                RequirementId.parse(options.required("requirement")),
                                technical)
                        .orElseThrow(() -> new IllegalStateException("project has no ACTIVE snapshot: " + projectId));
                case "change" -> service.change(
                                projectId,
                                ChangeId.parse(options.required("change")),
                                technical)
                        .orElseThrow(() -> new IllegalStateException("project has no ACTIVE snapshot: " + projectId));
                default -> throw new IllegalArgumentException("unknown augmented-context subject: " + subject);
            };
            write(result, parsed.json(), out);
            return CliExitCode.SUCCESS.code();
        }
    }

    private void write(AugmentedContextResult result, boolean jsonOutput, PrintStream out) {
        if (jsonOutput) {
            out.println(json.toJson(result));
            return;
        }
        out.println("snapshotId=" + result.snapshot().id());
        out.println("subjectType=" + result.intentContext().subjectType());
        out.println("subjectId=" + result.intentContext().subjectId());
        out.println("nexusState=" + result.technicalContext().status().state());
        out.println("persisted=false");
        result.technicalContext().bundle().ifPresent(bundle -> {
            out.println("nexusProject=" + bundle.projectName());
            out.println("tokenBudget=" + bundle.tokenBudget());
            out.println("estimatedTokens=" + bundle.estimatedTokens());
            out.println("items=" + bundle.items().size());
        });
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

    private record Parsed(boolean json, CliLayout layout, String command, List<String> tokens) {
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
            if (remaining.isEmpty()) {
                throw new IllegalArgumentException("NEXUS integration command is required");
            }
            CliLayout layout = CliLayout.resolve(data, config, database, environment, properties);
            return new Parsed(json, layout, remaining.getFirst(), List.copyOf(remaining.subList(1, remaining.size())));
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length || args[index].startsWith("--")) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return args[index];
        }
    }

    private static final class ContextOptions {
        private final Map<String, String> values = new LinkedHashMap<>();
        private final Set<String> sources = new LinkedHashSet<>();
        private final Map<String, String> constraints = new LinkedHashMap<>();
        private boolean explain;

        static ContextOptions parse(List<String> tokens) {
            ContextOptions result = new ContextOptions();
            for (int index = 0; index < tokens.size(); index++) {
                String token = tokens.get(index);
                switch (token) {
                    case "--explain" -> result.explain = true;
                    case "--source" -> result.sources.add(require(tokens, ++index, token).trim().toUpperCase(java.util.Locale.ROOT));
                    case "--constraint" -> result.addConstraint(require(tokens, ++index, token));
                    case "--project", "--requirement", "--change", "--nexus-project", "--budget" -> {
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

        void validateSubject(String subject) {
            switch (subject) {
                case "requirement" -> {
                    required("requirement");
                    if (values.containsKey("change")) {
                        throw new IllegalArgumentException("--change is not valid for augmented-context requirement");
                    }
                }
                case "change" -> {
                    required("change");
                    if (values.containsKey("requirement")) {
                        throw new IllegalArgumentException("--requirement is not valid for augmented-context change");
                    }
                }
                default -> throw new IllegalArgumentException("unknown augmented-context subject: " + subject);
            }
        }

        String required(String key) {
            String value = values.get(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("--" + key + " is required");
            }
            return value.trim();
        }

        TechnicalContextOptions technical() {
            int budget = TechnicalContextOptions.DEFAULT_TOKEN_BUDGET;
            String raw = values.get("budget");
            if (raw != null) {
                try {
                    budget = Integer.parseInt(raw.trim());
                } catch (NumberFormatException failure) {
                    throw new IllegalArgumentException("--budget must be an integer", failure);
                }
            }
            return new TechnicalContextOptions(required("nexus-project"), budget, sources, constraints, explain);
        }

        private void addConstraint(String raw) {
            int equals = raw.indexOf('=');
            if (equals <= 0 || equals == raw.length() - 1) {
                throw new IllegalArgumentException("--constraint must use key=value");
            }
            String key = raw.substring(0, equals).trim();
            String value = raw.substring(equals + 1).trim();
            if (constraints.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("duplicate constraint: " + key);
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
