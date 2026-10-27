package com.morpheus.cli.command;

import com.morpheus.application.context.AugmentedContextResult;
import com.morpheus.application.context.AugmentedContextService;
import com.morpheus.application.context.TechnicalContextOptions;
import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.operability.OperationalExecution;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.RequirementId;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/** Additive M13 CLI surface; NEXUS selection/ranking stays behind TechnicalContextProvider. */
public final class MorpheusAugmentedContextCli {
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();
    private final OperationalRecorder recorder;
    private final TechnicalContextProvider provider;
    private final ExternalIntegrationStatusProvider nexusStatus;

    public MorpheusAugmentedContextCli(
            OperationalRecorder recorder,
            TechnicalContextProvider provider,
            ExternalIntegrationStatusProvider nexusStatus) {
        this.recorder = java.util.Objects.requireNonNull(recorder, "recorder");
        this.provider = java.util.Objects.requireNonNull(provider, "provider");
        this.nexusStatus = java.util.Objects.requireNonNull(nexusStatus, "nexusStatus");
    }

    public static boolean handles(String[] args) {
        String command = GlobalArgs.command(args);
        return "nexus-status".equals(command) || "augmented-context".equals(command);
    }

    public int run(
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
            return CliErrorLine.report(err, CliExitCode.USAGE, failure);
        } catch (RuntimeException failure) {
            return CliErrorLine.report(err, CliExitCode.STATE_ERROR, failure);
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
            // The status record freezes its details with Map.copyOf, whose iteration order is salted per JVM.
            new TreeMap<>(status.details()).forEach((key, value) -> out.println(key + "=" + value));
        }
        return CliExitCode.SUCCESS.code();
    }

    private int augmented(Parsed parsed, PrintStream out) {
        if (parsed.tokens().isEmpty()) {
            throw new IllegalArgumentException("augmented-context requires subject: requirement | change");
        }
        String subject = parsed.tokens().getFirst();
        CommandOptions options = CommandOptions.parse(parsed.tokens().subList(1, parsed.tokens().size()),
                CommandOptions.Declared.values("project", "requirement", "change", "nexus-project", "budget")
                        .andRepeated("source", "constraint").andFlags("explain"));
        Map<String, String> constraints = constraints(options);
        validateSubject(subject, options);
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
        TechnicalContextOptions technical = technical(options, constraints);

        try (CliRuntime runtime = CliRuntime.recovering(parsed.layout().databasePath(), recorder)) {
            if (runtime.snapshots().findProject(projectId).isEmpty()) {
                throw new IllegalStateException("project not found: " + projectId);
            }
            AugmentedContextService service = new AugmentedContextService(
                    runtime.snapshots(),
                    runtime.content(),
                    runtime.requirements(),
                    runtime.traceability(),
                    runtime.externalReferences(),
                    provider, new OperationalExecution(recorder));
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

    private record Parsed(boolean json, CliLayout layout, String command, List<String> tokens) {
        private static Parsed parse(String[] args, Map<String, String> environment, Properties properties) {
            GlobalArgs.Parsed global = GlobalArgs.parse(args);
            List<String> remaining = global.remaining();
            if (remaining.isEmpty()) {
                throw new IllegalArgumentException("NEXUS integration command is required");
            }
            CliLayout layout = CliLayout.resolve(
                    global.dataDirectory(), global.configDirectory(), global.databasePath(), environment, properties);
            return new Parsed(
                    global.json(), layout, remaining.getFirst(), List.copyOf(remaining.subList(1, remaining.size())));
        }
    }

    private static void validateSubject(String subject, CommandOptions options) {
        switch (subject) {
            case "requirement" -> {
                options.required("requirement");
                if (options.given("change")) {
                    throw new IllegalArgumentException("--change is not valid for augmented-context requirement");
                }
            }
            case "change" -> {
                options.required("change");
                if (options.given("requirement")) {
                    throw new IllegalArgumentException("--requirement is not valid for augmented-context change");
                }
            }
            default -> throw new IllegalArgumentException("unknown augmented-context subject: " + subject);
        }
    }

    private static TechnicalContextOptions technical(CommandOptions options, Map<String, String> constraints) {
        int budget = TechnicalContextOptions.DEFAULT_TOKEN_BUDGET;
        Optional<String> raw = options.optional("budget");
        if (raw.isPresent()) {
            try {
                budget = Integer.parseInt(raw.orElseThrow());
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException("--budget must be an integer", failure);
            }
        }
        Set<String> sources = new LinkedHashSet<>();
        options.all("source").forEach(source -> sources.add(source.trim().toUpperCase(Locale.ROOT)));
        return new TechnicalContextOptions(
                options.required("nexus-project"), budget, sources, constraints, options.flag("explain"));
    }

    private static Map<String, String> constraints(CommandOptions options) {
        Map<String, String> constraints = new LinkedHashMap<>();
        options.all("constraint").forEach(constraint -> addConstraint(constraints, constraint));
        return constraints;
    }

    private static void addConstraint(Map<String, String> constraints, String raw) {
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
}
