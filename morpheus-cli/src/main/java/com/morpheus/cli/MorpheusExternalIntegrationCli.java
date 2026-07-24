package com.morpheus.cli;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.application.reference.LiveExternalReferenceResolutionService;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/** Additive M12 CLI surface. Business resolution remains in application services. */
final class MorpheusExternalIntegrationCli {
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();
    private final ExternalReferenceResolverRegistry resolverRegistry;
    private final ExternalIntegrationStatusProvider minosStatus;

    MorpheusExternalIntegrationCli(
            ExternalReferenceResolverRegistry resolverRegistry,
            ExternalIntegrationStatusProvider minosStatus) {
        this.resolverRegistry = Objects.requireNonNull(resolverRegistry, "resolverRegistry");
        this.minosStatus = Objects.requireNonNull(minosStatus, "minosStatus");
    }

    static boolean handles(String[] args) {
        String command = command(args);
        return "minos-status".equals(command) || "external-references".equals(command);
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
                case "minos-status" -> minosStatus(parsed, out);
                case "external-references" -> externalReferences(parsed, out);
                default -> throw new IllegalArgumentException("unsupported external integration command: " + parsed.command());
            };
        } catch (IllegalArgumentException failure) {
            error(err, CliExitCode.USAGE, failure.getMessage());
            return CliExitCode.USAGE.code();
        } catch (RuntimeException failure) {
            error(err, CliExitCode.STATE_ERROR, safeMessage(failure));
            return CliExitCode.STATE_ERROR.code();
        }
    }

    private int minosStatus(Parsed parsed, PrintStream out) {
        if (!parsed.tokens().isEmpty()) {
            throw new IllegalArgumentException("minos-status does not accept command options");
        }
        var status = minosStatus.status();
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

    private int externalReferences(Parsed parsed, PrintStream out) {
        if (parsed.tokens().isEmpty()) {
            throw new IllegalArgumentException("external-references requires subcommand: list | resolve");
        }
        String subcommand = parsed.tokens().getFirst();
        Options options = Options.parse(parsed.tokens().subList(1, parsed.tokens().size()));
        try (CliRuntime runtime = new CliRuntime(parsed.layout().databasePath())) {
            ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
            if (runtime.snapshots.findProject(projectId).isEmpty()) {
                throw new IllegalStateException("project not found: " + projectId);
            }
            LiveExternalReferenceResolutionService service = new LiveExternalReferenceResolutionService(
                    runtime.snapshots, runtime.externalReferences, resolverRegistry, Clock.systemUTC());
            return switch (subcommand) {
                case "list" -> list(service, projectId, options, parsed.json(), out);
                case "resolve" -> resolve(service, projectId, options, parsed.json(), out);
                default -> throw new IllegalArgumentException("unknown external-references subcommand: " + subcommand);
            };
        }
    }

    private int list(
            LiveExternalReferenceResolutionService service,
            ProjectSpecificationId projectId,
            Options options,
            boolean jsonOutput,
            PrintStream out) {
        options.rejectUnknown(Set.of("project", "owner"));
        DomainIdentity ownerId = DomainIdentity.parse(options.required("owner"));
        List<ExternalReference> references = service.listActive(projectId, ownerId)
                .orElseThrow(() -> new IllegalStateException("project has no ACTIVE snapshot: " + projectId));
        List<Object> items = references.stream().map(this::reference).toList();
        if (jsonOutput) {
            out.println(json.toJson(Map.of("projectId", projectId.toString(), "ownerId", ownerId.toString(), "items", items)));
        } else if (items.isEmpty()) {
            out.println("No external references.");
        } else {
            references.forEach(item -> out.println(item.id() + "\t" + item.target().system() + "\t"
                    + item.target().resourceType() + "\t" + item.target().externalId() + "\t" + item.resolutionState()));
        }
        return CliExitCode.SUCCESS.code();
    }

    private int resolve(
            LiveExternalReferenceResolutionService service,
            ProjectSpecificationId projectId,
            Options options,
            boolean jsonOutput,
            PrintStream out) {
        options.rejectUnknown(Set.of("project", "reference"));
        ExternalReferenceId referenceId = ExternalReferenceId.parse(options.required("reference"));
        var result = service.resolveActive(projectId, referenceId)
                .orElseThrow(() -> new IllegalStateException("project has no ACTIVE snapshot: " + projectId));
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("snapshotId", result.snapshot().id().toString());
        view.put("stored", reference(result.storedReference()));
        view.put("observed", reference(result.observedReference()));
        view.put("persisted", false);
        if (jsonOutput) {
            out.println(json.toJson(view));
        } else {
            out.println("referenceId=" + referenceId);
            out.println("storedState=" + result.storedReference().resolutionState());
            out.println("observedState=" + result.observedReference().resolutionState());
            out.println("observedReason=" + result.observedReference().resolutionReason());
            out.println("persisted=false");
        }
        return CliExitCode.SUCCESS.code();
    }

    private Map<String, Object> reference(ExternalReference reference) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("system", reference.target().system());
        target.put("project", reference.target().project().orElse(null));
        target.put("resourceType", reference.target().resourceType());
        target.put("externalId", reference.target().externalId());
        target.put("revision", reference.target().revision().orElse(null));

        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", reference.id().toString());
        value.put("ownerId", reference.ownerId().toString());
        value.put("target", target);
        value.put("resolutionState", reference.resolutionState().name());
        value.put("resolutionReason", reference.resolutionReason().name());
        value.put("resolvedTarget", reference.resolvedTarget().map(resolved -> Map.of(
                "target", Map.of(
                        "system", resolved.target().system(),
                        "project", resolved.target().project().orElse(""),
                        "resourceType", resolved.target().resourceType(),
                        "externalId", resolved.target().externalId(),
                        "revision", resolved.target().revision().orElse("")),
                "attributes", resolved.attributes())).orElse(null));
        value.put("historyCount", reference.history().size());
        return value;
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
                throw new IllegalArgumentException("external integration command is required");
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

    private static final class Options {
        private final Map<String, String> values;

        private Options(Map<String, String> values) {
            this.values = Map.copyOf(values);
        }

        static Options parse(List<String> tokens) {
            Map<String, String> values = new LinkedHashMap<>();
            for (int index = 0; index < tokens.size(); index++) {
                String token = tokens.get(index);
                if (!token.startsWith("--")) {
                    throw new IllegalArgumentException("unexpected positional argument: " + token);
                }
                String key = token.substring(2);
                if (index + 1 >= tokens.size() || tokens.get(index + 1).startsWith("--")) {
                    throw new IllegalArgumentException(token + " requires a value");
                }
                if (values.putIfAbsent(key, tokens.get(++index)) != null) {
                    throw new IllegalArgumentException("duplicate option: --" + key);
                }
            }
            return new Options(values);
        }

        String required(String key) {
            String value = values.get(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("--" + key + " is required");
            }
            return value.trim();
        }

        void rejectUnknown(Set<String> allowed) {
            Set<String> unknown = new java.util.HashSet<>(values.keySet());
            unknown.removeAll(allowed);
            if (!unknown.isEmpty()) {
                throw new IllegalArgumentException("unknown options: " + unknown);
            }
        }
    }
}
