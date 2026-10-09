package com.morpheus.cli;

import com.morpheus.application.operability.ExhaustiveShutdown;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.query.dsl.PortfolioQueryScope;
import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryBudgets;
import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.application.query.dsl.QueryDslParser;
import com.morpheus.application.query.dsl.QueryPublicViews;
import com.morpheus.application.query.dsl.QueryScope;
import com.morpheus.application.query.export.QueryExportFormat;
import com.morpheus.application.query.saved.SavedViewId;
import com.morpheus.application.query.saved.SavedViewStatus;
import com.morpheus.application.store.EntityNotFoundException;
import com.morpheus.application.store.EntityStateException;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.store.sqlite.SqliteQueryRuntime;

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

/**
 * M24 CLI adapter. All query semantics remain centralized in application services.
 *
 * <p>The command, its action and its options are accepted before the store is opened: opening it creates the database,
 * its parent directory and its schema, so a refusal that came after it left a database where the caller had asked for
 * nothing. The options each action accepts are therefore tables ({@link #EXECUTE_OPTIONS}, {@link #VIEW_ACTION_OPTIONS},
 * {@link #EXPORT_ACTION_OPTIONS}), read by {@link #accepted} before the store is opened; each entry is the set of options
 * its {@code case} reads, no more and no less.</p>
 */
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
    private static final String EXPORT_VIEW = "view";
    private static final String OPT_ID = "id";
    private static final String OPT_NAME = "name";
    private static final String OPT_SORT = "sort";

    static final Set<String> EXECUTE_OPTIONS = Set.of(
            OPT_PROJECT, OPT_PORTFOLIO, OPT_ENTITY, OPT_FILTER, OPT_SORT, OPT_FIELDS, OPT_OFFSET, OPT_LIMIT);
    static final Map<String, Set<String>> VIEW_ACTION_OPTIONS = Map.ofEntries(
            Map.entry("create", Set.of(
                    OPT_NAME, OPT_PROJECT, OPT_PORTFOLIO, OPT_ENTITY, OPT_FILTER, OPT_SORT, OPT_FIELDS, OPT_OFFSET, OPT_LIMIT)),
            Map.entry("list", Set.of(OPT_PROJECT, OPT_PORTFOLIO)),
            Map.entry("get", Set.of(OPT_ID)),
            Map.entry("versions", Set.of(OPT_ID)),
            Map.entry("update", Set.of(
                    OPT_ID, OPT_EXPECTED_REVISION, OPT_NAME, OPT_ENTITY, OPT_FILTER, OPT_SORT, OPT_FIELDS, OPT_OFFSET, OPT_LIMIT)),
            Map.entry("archive", Set.of(OPT_ID, OPT_EXPECTED_REVISION)),
            Map.entry("execute", Set.of(OPT_ID)));
    /** {@code --offset} and {@code --limit} are refused by {@link #accepted} with their own reason, not as unknown. */
    static final Map<String, Set<String>> EXPORT_ACTION_OPTIONS = Map.of(
            CMD_QUERY, Set.of(OPT_FORMAT, OPT_PROJECT, OPT_PORTFOLIO, OPT_ENTITY, OPT_FILTER, OPT_SORT, OPT_FIELDS),
            EXPORT_VIEW, Set.of(OPT_FORMAT, OPT_ID));
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
            SimpleOptions options = accepted(parsed);
            try (SqliteQueryRuntime runtime = SqliteQueryRuntime.open(parsed.layout().databasePath())) {
                return switch (parsed.command()) {
                    case CMD_QUERY -> query(options, runtime, parsed.json(), out);
                    case CMD_VIEWS -> views(parsed, options, runtime, out);
                    case CMD_EXPORT -> export(parsed, options, runtime, out);
                    default -> throw new IllegalStateException("M24 command accepted without a handler: " + parsed.command());
                };
            }
        } catch (EntityNotFoundException failure) {
            err.println("MORPHEUS error [" + CliExitCode.NOT_FOUND.code() + "]: " + safeMessage(failure));
            return CliExitCode.NOT_FOUND.code();
        } catch (EntityStateException failure) {
            err.println("MORPHEUS error [" + CliExitCode.STATE_ERROR.code() + "]: " + safeMessage(failure));
            return CliExitCode.STATE_ERROR.code();
        } catch (IllegalArgumentException failure) {
            err.println("MORPHEUS error [" + CliExitCode.USAGE.code() + "]: " + safeMessage(failure));
            return CliExitCode.USAGE.code();
        } catch (RuntimeException failure) {
            err.println("MORPHEUS error [" + CliExitCode.STATE_ERROR.code() + "]: " + safeMessage(failure));
            return CliExitCode.STATE_ERROR.code();
        }
    }

    /**
     * Everything that can be refused without the store: the action, the parsing of the options, an export's format and
     * page, and the options the action does not read. The checks keep the order they had in each command's own method,
     * so a refusal names the same thing as before.
     */
    private static SimpleOptions accepted(Parsed parsed) {
        return switch (parsed.command()) {
            case CMD_QUERY -> {
                requireAction(parsed, "execute");
                SimpleOptions options = SimpleOptions.parse(parsed.arguments());
                options.rejectUnknown(EXECUTE_OPTIONS);
                yield options;
            }
            case CMD_VIEWS -> {
                String action = parsed.action().orElseThrow(() -> new IllegalArgumentException("views requires an action"));
                SimpleOptions options = SimpleOptions.parse(parsed.arguments());
                options.rejectUnknown(allowed(VIEW_ACTION_OPTIONS, action, "unknown views action: "));
                yield options;
            }
            case CMD_EXPORT -> {
                String action = parsed.action().orElseThrow(() -> new IllegalArgumentException("export requires query or view"));
                SimpleOptions options = SimpleOptions.parse(parsed.arguments());
                format(options);
                Set<String> allowed = allowed(EXPORT_ACTION_OPTIONS, action, "unknown export action: ");
                if (action.equals(CMD_QUERY)) {
                    for (String paging : List.of(OPT_OFFSET, OPT_LIMIT)) {
                        if (options.optional(paging).isPresent()) {
                            throw new IllegalArgumentException("--" + paging + " is not accepted by export: an export is always"
                                    + " complete, bounded by " + QueryBudgets.MAX_EXPORT_ROWS + " rows and never by a page");
                        }
                    }
                }
                options.rejectUnknown(allowed);
                yield options;
            }
            default -> throw new IllegalStateException("M24 command parsed without a validation: " + parsed.command());
        };
    }

    private static Set<String> allowed(Map<String, Set<String>> actionOptions, String action, String unknown) {
        Set<String> allowed = actionOptions.get(action);
        if (allowed == null) {
            throw new IllegalArgumentException(unknown + action);
        }
        return allowed;
    }

    private int query(SimpleOptions options, SqliteQueryRuntime runtime, boolean jsonOutput, PrintStream out) {
        QueryDefinition query = query(options, scope(options));
        write(QueryPublicViews.result(runtime.queries().execute(query)), jsonOutput, out);
        return CliExitCode.SUCCESS.code();
    }

    private int views(Parsed parsed, SimpleOptions options, SqliteQueryRuntime runtime, PrintStream out) {
        String action = parsed.action().orElseThrow();
        Object result = switch (action) {
            case "create" -> {
                QueryDefinition definition = query(options, scope(options));
                yield QueryPublicViews.savedView(runtime.views().create(options.required(OPT_NAME), definition));
            }
            case "list" -> QueryPublicViews.savedViews(runtime.views().list(scope(options)));
            case "get" -> QueryPublicViews.savedView(runtime.views().get(savedView(options)));
            case "versions" -> QueryPublicViews.savedVersions(runtime.views().versions(savedView(options)));
            case "update" -> {
                SavedViewId id = savedView(options);
                var current = runtime.views().get(id);
                QueryDefinition definition = query(options, current.query().scope());
                yield QueryPublicViews.savedView(runtime.views().update(
                        id, positiveLong(options, OPT_EXPECTED_REVISION), options.required(OPT_NAME), definition));
            }
            case "archive" -> QueryPublicViews.savedView(runtime.views().archive(
                    savedView(options), positiveLong(options, OPT_EXPECTED_REVISION)));
            case "execute" -> QueryPublicViews.result(runtime.views().execute(savedView(options)));
            default -> throw new IllegalStateException("views action accepted without a handler: " + action);
        };
        write(result, parsed.json(), out);
        return CliExitCode.SUCCESS.code();
    }

    private int export(Parsed parsed, SimpleOptions options, SqliteQueryRuntime runtime, PrintStream out) {
        String action = parsed.action().orElseThrow();
        QueryExportFormat format = format(options);
        QueryDefinition definition = switch (action) {
            case CMD_QUERY -> query(options, scope(options), 0, DEFAULT_LIMIT);
            case EXPORT_VIEW -> {
                var view = runtime.views().get(savedView(options));
                if (view.status() != SavedViewStatus.ACTIVE) {
                    throw new IllegalStateException("saved view is archived: " + view.id());
                }
                yield view.query();
            }
            default -> throw new IllegalStateException("export action accepted without a handler: " + action);
        };
        var export = runtime.exports().export(definition, format);
        out.print(export.content());
        if (!export.content().endsWith("\n")) {
            out.println();
        }
        return CliExitCode.SUCCESS.code();
    }

    private QueryDefinition query(SimpleOptions options, QueryScope scope) {
        return query(options, scope, integer(options, OPT_OFFSET, 0), integer(options, OPT_LIMIT, DEFAULT_LIMIT));
    }

    private QueryDefinition query(SimpleOptions options, QueryScope scope, int offset, int limit) {
        return parser.parse(
                scope,
                options.required(OPT_ENTITY),
                options.optional(OPT_FILTER).orElse(null),
                options.optional(OPT_SORT).orElse(null),
                options.optional(OPT_FIELDS).orElse(null),
                offset,
                limit);
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
        return SavedViewId.parse(options.required(OPT_ID));
    }

    private static QueryExportFormat format(SimpleOptions options) {
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

}
