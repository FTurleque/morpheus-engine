package com.morpheus.cli;

import com.morpheus.application.operability.ExhaustiveShutdown;
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
    private static final String CMD_QUERY = "query";
    private static final String CMD_EXPORT = "export";
    private static final String CMD_VIEWS = "views";
    private static final String OPT_OFFSET = "offset";
    private static final String OPT_FILTER = "filter";
    private static final String OPT_FIELDS = "fields";
    private static final String OPT_ENTITY = "entity";
    private static final String OPT_PROJECT = "project";
    private static final String OPT_PORTFOLIO = "portfolio";
    private static final String OPT_LIMIT = "limit";
    private static final String OPT_EXPECTED_REVISION = "expected-revision";
    private static final String OPT_FORMAT = "format";
    private static final int DEFAULT_LIMIT = 100;
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();
    private final QueryDslParser parser = new QueryDslParser();

    static boolean handles(String[] args) {
        String command = command(args);
        return command.equals(CMD_QUERY) || command.equals(CMD_VIEWS) || command.equals(CMD_EXPORT);
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
                    case CMD_QUERY -> query(parsed, runtime, out);
                    case CMD_VIEWS -> views(parsed, runtime, out);
                    case CMD_EXPORT -> export(parsed, runtime, out);
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
        SimpleOptions options = SimpleOptions.parse(parsed.arguments());
        options.rejectUnknown(Set.of(OPT_PROJECT, OPT_PORTFOLIO, OPT_ENTITY, OPT_FILTER, "sort", OPT_FIELDS, OPT_OFFSET, OPT_LIMIT));
        QueryDefinition query = query(options, scope(options));
        write(QueryPublicViews.result(runtime.queries.execute(query)), parsed.json(), out);
        return CliExitCode.SUCCESS.code();
    }

    private int views(Parsed parsed, Runtime runtime, PrintStream out) {
        if (parsed.action().isEmpty()) {
            throw new IllegalArgumentException("views requires an action");
        }
        String action = parsed.action().orElseThrow();
        SimpleOptions options = SimpleOptions.parse(parsed.arguments());
        Object result = switch (action) {
            case "create" -> {
                options.rejectUnknown(Set.of(
                        "name", OPT_PROJECT, OPT_PORTFOLIO, OPT_ENTITY, OPT_FILTER, "sort", OPT_FIELDS, OPT_OFFSET, OPT_LIMIT));
                QueryDefinition definition = query(options, scope(options));
                yield QueryPublicViews.savedView(runtime.views.create(options.required("name"), definition));
            }
            case "list" -> {
                options.rejectUnknown(Set.of(OPT_PROJECT, OPT_PORTFOLIO));
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
                        "id", OPT_EXPECTED_REVISION, "name", OPT_ENTITY, OPT_FILTER, "sort", OPT_FIELDS, OPT_OFFSET, OPT_LIMIT));
                SavedViewId id = savedView(options);
                var current = runtime.views.get(id);
                QueryDefinition definition = query(options, current.query().scope());
                yield QueryPublicViews.savedView(runtime.views.update(
                        id, positiveLong(options, OPT_EXPECTED_REVISION), options.required("name"), definition));
            }
            case "archive" -> {
                options.rejectUnknown(Set.of("id", OPT_EXPECTED_REVISION));
                yield QueryPublicViews.savedView(runtime.views.archive(
                        savedView(options), positiveLong(options, OPT_EXPECTED_REVISION)));
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
        SimpleOptions options = SimpleOptions.parse(parsed.arguments());
        QueryExportFormat format = format(options);
        QueryDefinition definition = switch (action) {
            case CMD_QUERY -> {
                options.rejectUnknown(Set.of(
                        OPT_FORMAT, OPT_PROJECT, OPT_PORTFOLIO, OPT_ENTITY, OPT_FILTER, "sort", OPT_FIELDS, OPT_OFFSET, OPT_LIMIT));
                yield query(options, scope(options));
            }
            case "view" -> {
                options.rejectUnknown(Set.of(OPT_FORMAT, "id"));
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

    private QueryDefinition query(SimpleOptions options, QueryScope scope) {
        return parser.parse(
                scope,
                options.required(OPT_ENTITY),
                options.optional(OPT_FILTER).orElse(null),
                options.optional("sort").orElse(null),
                options.optional(OPT_FIELDS).orElse(null),
                integer(options, OPT_OFFSET, 0),
                integer(options, OPT_LIMIT, DEFAULT_LIMIT));
    }

    private QueryScope scope(SimpleOptions options) {
        Optional<String> project = options.optional(OPT_PROJECT);
        Optional<String> portfolio = options.optional(OPT_PORTFOLIO);
        if (project.isPresent() == portfolio.isPresent()) {
            throw new IllegalArgumentException("exactly one of --project or --portfolio is required");
        }
        return project.<QueryScope>map(value -> new ProjectQueryScope(ProjectSpecificationId.parse(value)))
                .orElseGet(() -> new PortfolioQueryScope(PortfolioId.parse(portfolio.orElseThrow())));
    }

    private SavedViewId savedView(SimpleOptions options) {
        return SavedViewId.parse(options.required("id"));
    }

    private QueryExportFormat format(SimpleOptions options) {
        try {
            return QueryExportFormat.valueOf(options.required(OPT_FORMAT).replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("--format must be json, csv or markdown");
        }
    }

    private static int integer(SimpleOptions options, String key, int fallback) {
        try {
            return options.optional(key).map(Integer::parseInt).orElse(fallback);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("--" + key + " must be an integer");
        }
    }

    private static long positiveLong(SimpleOptions options, String key) {
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
        return GlobalArgs.command(args);
    }

    private record Parsed(boolean json, CliLayout layout, String command, Optional<String> action, List<String> arguments) {
        private static Parsed parse(String[] args, Map<String, String> environment, Properties properties) {
            GlobalArgs.Parsed global = GlobalArgs.parse(args);
            List<String> remaining = global.remaining();
            if (remaining.isEmpty() || !(remaining.getFirst().equals(CMD_QUERY)
                    || remaining.getFirst().equals(CMD_VIEWS) || remaining.getFirst().equals(CMD_EXPORT))) {
                throw new IllegalArgumentException("query, views or export command is required");
            }
            String command = remaining.getFirst();
            Optional<String> action = remaining.size() > 1 && !remaining.get(1).startsWith("--")
                    ? Optional.of(remaining.get(1)) : Optional.empty();
            int argumentStart = action.isPresent() ? 2 : 1;
            return new Parsed(
                    global.json(),
                    CliLayout.resolve(global.dataDirectory(), global.configDirectory(), global.databasePath(),
                            environment, properties),
                    command,
                    action,
                    List.copyOf(remaining.subList(argumentStart, remaining.size())));
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
            ExhaustiveShutdown.releaseAll(
                    "cannot close the query CLI runtime",
                    saved,
                    portfolios,
                    content,
                    requirements,
                    snapshots);
        }
    }
}
