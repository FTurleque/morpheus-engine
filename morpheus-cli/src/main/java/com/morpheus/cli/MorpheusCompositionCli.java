package com.morpheus.cli;

import com.morpheus.application.composition.CompositionQueryService;
import com.morpheus.application.composition.CompositionSnapshotState;
import com.morpheus.application.composition.CompositionStateView;
import com.morpheus.application.composition.MultiProviderCompositionService;
import com.morpheus.application.composition.MultiProviderReadService;
import com.morpheus.application.composition.ProviderCompositionSource;
import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.ingestion.ObservedProjectSnapshotPublisher;
import com.morpheus.application.operability.LocalOperationalRuntime;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.provider.markdown.StructuredMarkdownSpecificationContentReader;
import com.morpheus.provider.markdown.StructuredMarkdownSpecificationProvider;
import com.morpheus.provider.openspec.OpenSpecSpecificationContentReader;
import com.morpheus.provider.openspec.OpenSpecSpecificationProvider;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** M18 CLI surface for explicit multi-provider synchronization and composition diagnostics. */
final class MorpheusCompositionCli {
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    static boolean handles(String[] args) {
        return "composition".equals(command(args));
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
                throw new IllegalArgumentException("composition requires action: sync | status | conflicts");
            }
            String action = parsed.tokens().getFirst();
            Options options = Options.parse(parsed.tokens().subList(1, parsed.tokens().size()));
            ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
            return switch (action) {
                case "sync" -> sync(projectId, parsed, options, out);
                case "status" -> status(projectId, parsed, out, false);
                case "conflicts" -> status(projectId, parsed, out, true);
                default -> throw new IllegalArgumentException("unknown composition action: " + action);
            };
        } catch (IllegalArgumentException failure) {
            error(err, CliExitCode.USAGE, safeMessage(failure));
            return CliExitCode.USAGE.code();
        } catch (RuntimeException failure) {
            error(err, CliExitCode.STATE_ERROR, safeMessage(failure));
            return CliExitCode.STATE_ERROR.code();
        }
    }

    private int sync(
            ProjectSpecificationId projectId,
            Parsed parsed,
            Options options,
            PrintStream out) {
        options.rejectUnknown("project", "revision");
        try (CliRuntime runtime = new CliRuntime(parsed.layout().databasePath())) {
            var project = runtime.snapshots.findProject(projectId)
                    .orElseThrow(() -> new IllegalStateException("project not found: " + projectId));
            Path workspace = Path.of(project.rootLocator().value()).toAbsolutePath().normalize();
            var openspecProvider = new OpenSpecSpecificationProvider();
            var markdownProvider = new StructuredMarkdownSpecificationProvider();
            List<ProviderCompositionSource> sources = new ArrayList<>();
            if (openspecProvider.probe(workspace).status() == ProviderProbeStatus.SUPPORTED) {
                sources.add(new ProviderCompositionSource(OpenSpecSpecificationProvider.ID, 100, true));
            }
            if (markdownProvider.probe(workspace).status() == ProviderProbeStatus.SUPPORTED) {
                sources.add(new ProviderCompositionSource(StructuredMarkdownSpecificationProvider.ID, 50, true));
            }
            if (sources.isEmpty()) {
                throw new IllegalStateException("no supported real provider detected in workspace: " + workspace);
            }

            var identityResolver = new PersistentEntityIdentityResolver(runtime.identities);
            var result = new MultiProviderReadService(
                    List.of(
                            new OpenSpecSpecificationContentReader(),
                            new StructuredMarkdownSpecificationContentReader()),
                    new MultiProviderCompositionService())
                    .read(ProviderReadRequest.all(workspace, projectId), identityResolver, sources);

            var imported = new ObservedProjectSnapshotPublisher(
                    new ProjectSnapshotImportService(
                            runtime.snapshots,
                            runtime.requirements,
                            runtime.content,
                            runtime.traceability),
                    LocalOperationalRuntime.recorder())
                    .publishFull(result.content(), options.optional("revision"), Instant.now());
            CompositionSnapshotState state = CompositionSnapshotState.from(imported.snapshot().id(), result);
            runtime.compositions.save(state);
            CompositionStateView view = CompositionStateView.from(state);
            write(view, parsed.json(), out, false);
            return CliExitCode.SUCCESS.code();
        }
    }

    private int status(
            ProjectSpecificationId projectId,
            Parsed parsed,
            PrintStream out,
            boolean conflictsOnly) {
        try (CliRuntime runtime = new CliRuntime(parsed.layout().databasePath())) {
            CompositionStateView view = new CompositionQueryService(runtime.snapshots, runtime.compositions)
                    .findActive(projectId)
                    .orElseThrow(() -> new IllegalStateException(
                            "project has no ACTIVE snapshot with composition state: " + projectId));
            write(view, parsed.json(), out, conflictsOnly);
            return CliExitCode.SUCCESS.code();
        }
    }

    private void write(CompositionStateView view, boolean jsonOutput, PrintStream out, boolean conflictsOnly) {
        if (jsonOutput) {
            out.println(json.toJson(conflictsOnly ? view.conflicts() : view));
            return;
        }
        if (conflictsOnly) {
            out.println("snapshotId=" + view.snapshotId() + " conflicts=" + view.conflicts().size());
            view.conflicts().forEach(item -> out.println(
                    item.entityType() + "\t" + item.logicalKey() + "\t" + item.field()
                            + "\t" + item.resolution() + "\t" + item.selectedProviderId().orElse("none")));
            return;
        }
        out.println("snapshotId=" + view.snapshotId());
        out.println("primaryProvider=" + view.primaryProviderId());
        out.println("providers=" + view.providers().size());
        out.println("conflicts=" + view.conflicts().size());
        view.providers().forEach(item -> out.println(
                item.providerId() + "\tpriority=" + item.priority()
                        + "\trequired=" + item.required() + "\tavailable=" + item.available()));
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
            if (remaining.isEmpty() || !"composition".equals(remaining.getFirst())) {
                throw new IllegalArgumentException("composition command is required");
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
                if (!token.equals("--project") && !token.equals("--revision")) {
                    throw new IllegalArgumentException("unknown option: " + token);
                }
                String key = token.substring(2);
                if (result.values.putIfAbsent(key, require(tokens, ++index, token)) != null) {
                    throw new IllegalArgumentException("duplicate option: " + token);
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

        void rejectUnknown(String... allowed) {
            List<String> accepted = List.of(allowed);
            values.keySet().stream()
                    .filter(key -> !accepted.contains(key))
                    .findFirst()
                    .ifPresent(key -> {
                        throw new IllegalArgumentException("unknown option: --" + key);
                    });
        }

        private static String require(List<String> tokens, int index, String option) {
            if (index >= tokens.size() || tokens.get(index).startsWith("--")) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return tokens.get(index);
        }
    }
}
