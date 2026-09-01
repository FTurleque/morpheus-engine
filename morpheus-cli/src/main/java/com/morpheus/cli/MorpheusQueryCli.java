package com.morpheus.cli;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.query.dsl.PortfolioQueryScope;
import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.application.query.dsl.QueryDslParser;
import com.morpheus.application.query.dsl.QueryExecutionService;
import com.morpheus.application.query.dsl.QueryPublicViews;
import com.morpheus.application.query.dsl.QueryScope;
import com.morpheus.application.query.export.QueryExportFormat;
import com.morpheus.application.query.export.QueryExportService;
import com.morpheus.application.query.saved.SavedViewId;
import com.morpheus.application.query.saved.SavedViewService;
import com.morpheus.application.query.saved.SavedViewStatus;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.store.sqlite.SqlitePortfolioStore;
import com.morpheus.store.sqlite.SqliteSavedViewStore;
import com.morpheus.store.sqlite.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteVersionedRequirementStore;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/** M24 CLI adapter. All query semantics remain centralized in application services. */
final class MorpheusQueryCli {
    private static final int DEFAULT_LIMIT = 100;
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();
    private final QueryDslParser parser = new QueryDslParser();

    static boolean handles(String[] args) {
        String command = command(args);
        return command.equals("query") || command.equals("views") || command.equals("export");
    }

    int run(
            String[] args,
            PrintStream out,
            PrintStream err,
            Map<String, String> environment,
            Properties properties) {
        try {
            Parsed parsed = Parsed.parse(args, environment, properties);
            try (Runtime runtime = new Runtime(parsed.layout().databasePath())) {
                return switch (parsed.command()) {
                    case "query" -> query(parsed, runtime, out);
                    case "views" -> views(parsed, runtime, out);
                    case "export" -> export(parsed, runtime, out);
                    default -> throw new IllegalArgumentException("unknown M24 command: " + parsed.command());
                };
            }
        } catch (IllegalArgumentException failure) {
            err.println("MORPHEUS error [" + CliExitCode.USAGE.code() + "]: " + safeMessage(failure));
            return CliExitCode.USAGE.code();
        } catch (RuntimeException failure) {
            err.println("MORPHEUS error [" + CliExitCode.STATE_ERROR.code() + "]: " + safeMessage(failure));
            return CliExitCode.STATE_ERROR.code();
        }
    }

    private int query(Parsed parsed, Runtime runtime, PrintStream out) {
        requireAction(parsed, "execute");
        Options options = Options.parse(parsed.arguments());
        options.rejectUnknown(Set.of("project", "portfolio", "entity", "filter", "sort", "fields", "offset", "limit"));
        QueryDefinition query = query(options, scope(options));
        write(QueryPublicViews.result(runtime.queries.execute(query)), parsed.json(), out);
        return CliExitCode.SUCCESS.code();
    }

    private int views(Parsed parsed, Runtime runtime, PrintStream out) {
        if (parsed.action().isEmpty()) {
            throw new IllegalArgumentException("views requires an action");
        }
        String action = parsed.action().orElseThrow();
        Options options = Options.parse(parsed.arguments());
        Object result = switch (action) {
            case "create" -> {
                options.rejectUnknown(Set.of(
                        "name", "project", "portfolio", "entity", "filter", "sort", "fields", "offset", "limit"));
                QueryDefinition definition = query(options, scope(options));
                yield QueryPublicViews.savedView(runtime.views.create(options.required("name"), definition));
            }
            case "list" -> {
                options.rejectUnknown(Set.of("project", "portfolio"));
                yield QueryPublicViews.savedViews(runtime.views.list(scope(options)));
            }
            case "get" -> {
                options.rejectUnknown(Set.of("id"));
                yield QueryPublicViews.savedView(runtime.views.get(savedView(options)));
            }
            case "versions" -> {
                options.rejectUnknown(Set.of("id"));
                yield QueryPublicViews.savedVersions(runtime.views.versions(savedView(options)));
            }
            case "update" -> {
                options.rejectUnknown(Set.of(
                        "id", "expected-revision", "name", "entity", "filter", "sort", "fields", "offset", "limit"));
                SavedViewId id = savedView(options);
                var current = runtime.views.get(id);
                QueryDefinition definition = query(options, current.query().scope());
                yield QueryPublicViews.savedView(runtime.views.update(
                        id, positiveLong(options, "expected-revision"), options.required("name"), definition));
            }
            case "archive" -> {
                options.rejectUnknown(Set.of("id", "expected-revision"));
                yield QueryPublicViews.savedView(runtime.views.archive(
                        savedView(options), positiveLong(options, "expected-revision")));
            }
            case "execute" -> {
                options.rejectUnknown(Set.of("id"));
                yield QueryPublicViews.result(runtime.views.execute(savedView(options)));
            }
            default -> throw new IllegalArgumentException("unknown views action: " + action);
        };
        write(result, parsed.json(), out);
        return CliExitCode.SUCCESS.code();
    }

    private int export(Parsed parsed, Runtime runtime, PrintStream out) {
        if (parsed.action().isEmpty()) {
            throw new IllegalArgumentException("export requires query or view");
        }
        String action = parsed.action().orElseThrow();
        Options options = Options.parse(parsed.arguments());
        QueryExportFormat format = format(options);
        QueryDefinition definition = switch (action) {
            case "query" -> {
                options.rejectUnknown(Set.of(
                        "format", "project", "portfolio", "entity", "filter", "sort", "fields", "offset", "limit"));
                yield query(options, scope(options));
            }
            case "view" -> {
                options.rejectUnknown(Set.of("format", "id"));
                var view = runtime.views.get(savedView(options));
                if (view.status() != SavedViewStatus.ACTIVE) {
                    throw new IllegalStateException("saved view is archived: " + view.id());
                }
                yield view.query();
            }
            default -> throw new IllegalArgumentException("unknown export action: " + action);
        };
        var export = runtime.exports.export(definition, format);
        out.print(export.content());
        if (!export.content().endsWith("\n")) {
            out.println();
        }
        return CliExitCode.SUCCESS.code();
    }

    private QueryDefinition query(Options options, QueryScope scope) {
        return parser.parse(
                scope,
                options.required("entity"),
                options.optional("filter").orElse(null),
                options.optional("sort").orElse(null),
                options.optional("fields").orElse(null),
                integer(options, "offset", 0),
                integer(options, "limit", DEFAULT_LIMIT));
    }

    private QueryScope scope(Options options) {
        Optional<String> project = options.optional("project");
        Optional<String> portfolio = options.optional("portfolio");
        if (project.isPresent() == portfolio.isPresent()) {
            throw new IllegalArgumentException("exactly one of --project or --portfolio is required");
        }
        return project.<QueryScope>map(value -> new ProjectQueryScope(ProjectSpecificationId.parse(value)))
                .orElseGet(() -> new PortfolioQueryScope(PortfolioId.parse(portfolio.orElseThrow())));
    }

    private SavedViewId savedView(Options options) {
        return SavedViewId.parse(options.required("id"));
    }

    private QueryExportFormat format(Options options) {
        try {
            return QueryExportFormat.valueOf(options.required("format").replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("--format must be json, csv or markdown");
        }
    }

    private static int integer(Options options, String key, int fallback) {
        try {
            return options.optional(key).map(Integer::parseInt).orElse(fallback);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("--" + key + " must be an integer");
        }
    }

    private static long positiveLong(Options options, String key) {
        try {
            long value = Long.parseLong(options.required(key));
            if (value <= 0) {
                throw new IllegalArgumentException("--" + key + " must be positive");
            }
            return value;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("--" + key + " must be a positive integer");
        }
    }

    private void write(Object value, boolean jsonOutput, PrintStream out) {
        if (jsonOutput) {
            out.println(json.toJson(value));
        } else {
            out.println(value);
        }
    }

    private static void requireAction(Parsed parsed, String expected) {
        if (parsed.action().isEmpty() || !parsed.action().orElseThrow().equals(expected)) {
            throw new IllegalArgumentException(parsed.command() + " requires action " + expected);
        }
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static String command(String[] args) {
        for (int index = 0; index < args.length; index++) {
            String token = args[index];
            if (token.equals("--json")) {
                continue;
            }
            if (token.equals("--data-dir") || token.equals("--config-dir") || token.equals("--db")) {
                index++;
                continue;
            }
            return token;
        }
        return "";
    }

    private record Parsed(boolean json, CliLayout layout, String command, Optional<String> action, List<String> arguments) {
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
            if (remaining.isEmpty() || !(remaining.getFirst().equals("query")
                    || remaining.getFirst().equals("views") || remaining.getFirst().equals("export"))) {
                throw new IllegalArgumentException("query, views or export command is required");
            }
            String command = remaining.getFirst();
            Optional<String> action = remaining.size() > 1 && !remaining.get(1).startsWith("--")
                    ? Optional.of(remaining.get(1)) : Optional.empty();
            int argumentStart = action.isPresent() ? 2 : 1;
            return new Parsed(
                    json,
                    CliLayout.resolve(data, config, database, environment, properties),
                    command,
                    action,
                    List.copyOf(remaining.subList(argumentStart, remaining.size())));
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
                if (!token.startsWith("--")) {
                    throw new IllegalArgumentException("unknown token: " + token);
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

        void rejectUnknown(Set<String> allowed) {
            values.keySet().stream().filter(key -> !allowed.contains(key)).findFirst()
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

    private static final class Runtime implements AutoCloseable {
        private final SqliteSpecificationKnowledgeStore snapshots;
        private final SqliteVersionedRequirementStore requirements;
        private final SqliteSnapshotBusinessContentStore content;
        private final SqlitePortfolioStore portfolios;
        private final SqliteSavedViewStore saved;
        private final QueryExecutionService queries;
        private final SavedViewService views;
        private final QueryExportService exports;

        private Runtime(Path databasePath) {
            snapshots = new SqliteSpecificationKnowledgeStore(databasePath);
            requirements = new SqliteVersionedRequirementStore(databasePath);
            content = new SqliteSnapshotBusinessContentStore(databasePath);
            portfolios = new SqlitePortfolioStore(databasePath);
            saved = new SqliteSavedViewStore(databasePath);
            queries = new QueryExecutionService(snapshots, requirements, content, portfolios);
            views = new SavedViewService(saved, queries);
            exports = new QueryExportService(queries);
        }

        @Override
        public void close() {
            saved.close();
            portfolios.close();
            content.close();
            requirements.close();
            snapshots.close();
        }
    }
}
