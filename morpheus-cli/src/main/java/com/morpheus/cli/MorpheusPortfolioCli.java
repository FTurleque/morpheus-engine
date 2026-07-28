package com.morpheus.cli;

import com.morpheus.application.portfolio.PortfolioQueryService;
import com.morpheus.application.portfolio.PortfolioRegistryService;
import com.morpheus.application.portfolio.PortfolioTraversalDirection;
import com.morpheus.application.portfolio.PortfolioTraversalService;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.portfolio.PortfolioEntityRef;
import com.morpheus.domain.portfolio.PortfolioFreshnessState;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.store.sqlite.SqlitePortfolioStore;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/** M23 CLI for explicit portfolio registry, queries, references and bounded traversal. */
final class MorpheusPortfolioCli {
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    static boolean handles(String[] args) {
        return "portfolio".equals(command(args));
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
                throw new IllegalArgumentException("portfolio requires an action");
            }
            String action = parsed.tokens().getFirst();
            Options options = Options.parse(parsed.tokens().subList(1, parsed.tokens().size()));
            try (SqlitePortfolioStore store = new SqlitePortfolioStore(parsed.layout().databasePath())) {
                PortfolioRegistryService registry = new PortfolioRegistryService(store);
                PortfolioQueryService query = new PortfolioQueryService(store);
                PortfolioTraversalService traversal = new PortfolioTraversalService(store);
                Object result = switch (action) {
                    case "create" -> registry.create(options.required("name"));
                    case "add-project" -> registry.registerProject(
                            portfolio(options),
                            ProjectSpecificationId.parse(options.required("project")),
                            options.required("name"),
                            options.optional("workspace").map(SourceLocator::file),
                            options.optional("repository").map(MorpheusPortfolioCli::locator),
                            providers(options.optional("providers")));
                    case "missing" -> registry.markMissing(
                            portfolio(options), ProjectSpecificationId.parse(options.required("project")));
                    case "freshness" -> registry.observeFreshness(
                            portfolio(options),
                            ProjectSpecificationId.parse(options.required("project")),
                            PortfolioFreshnessState.valueOf(options.required("state").toUpperCase()),
                            options.optional("revision"),
                            options.optional("explanation"));
                    case "add-reference" -> registry.addReference(
                            portfolio(options),
                            entity(options, "source"),
                            entity(options, "target"),
                            options.required("relation"),
                            new ProviderId(options.required("provider")),
                            options.optional("source-locator").map(MorpheusPortfolioCli::locator),
                            options.optional("evidence").map(EvidenceId::parse));
                    case "list" -> query.listPortfolios(integer(options, "offset", 0), integer(options, "limit", 100));
                    case "overview" -> query.overview(portfolio(options));
                    case "members" -> query.memberships(
                            portfolio(options), integer(options, "offset", 0), integer(options, "limit", 100));
                    case "references" -> options.optional("project")
                            .map(ProjectSpecificationId::parse)
                            .map(projectId -> query.projectReferences(
                                    portfolio(options), projectId,
                                    integer(options, "offset", 0), integer(options, "limit", 100)))
                            .orElseGet(() -> query.references(
                                    portfolio(options), integer(options, "offset", 0), integer(options, "limit", 100)));
                    case "conflicts" -> query.conflicts(portfolio(options));
                    case "traverse" -> traversal.traverse(
                            portfolio(options),
                            entity(options, "start"),
                            integer(options, "depth", 4),
                            integer(options, "nodes", 250),
                            integer(options, "links", 1000),
                            PortfolioTraversalDirection.valueOf(
                                    options.optional("direction").orElse("BOTH").toUpperCase()));
                    default -> throw new IllegalArgumentException("unknown portfolio action: " + action);
                };
                write(result, parsed.json(), out);
                return CliExitCode.SUCCESS.code();
            }
        } catch (IllegalArgumentException failure) {
            err.println("MORPHEUS error [" + CliExitCode.USAGE.code() + "]: " + safeMessage(failure));
            return CliExitCode.USAGE.code();
        } catch (RuntimeException failure) {
            err.println("MORPHEUS error [" + CliExitCode.STATE_ERROR.code() + "]: " + safeMessage(failure));
            return CliExitCode.STATE_ERROR.code();
        }
    }

    private void write(Object value, boolean jsonOutput, PrintStream out) {
        if (jsonOutput) {
            out.println(json.toJson(value));
        } else {
            out.println(value);
        }
    }

    private static PortfolioId portfolio(Options options) {
        return PortfolioId.parse(options.required("portfolio"));
    }

    private static PortfolioEntityRef entity(Options options, String prefix) {
        return new PortfolioEntityRef(
                ProjectSpecificationId.parse(options.required(prefix + "-project")),
                options.required(prefix + "-type"),
                DomainIdentity.parse(options.required(prefix + "-id")));
    }

    private static SourceLocator locator(String encoded) {
        int separator = encoded.indexOf(':');
        if (separator <= 0 || separator == encoded.length() - 1) {
            throw new IllegalArgumentException("locator must use scheme:value syntax");
        }
        return new SourceLocator(encoded.substring(0, separator), encoded.substring(separator + 1));
    }

    private static Set<ProviderId> providers(Optional<String> encoded) {
        return encoded.stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(ProviderId::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static int integer(Options options, String key, int fallback) {
        return options.optional(key).map(Integer::parseInt).orElse(fallback);
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
            if (remaining.isEmpty() || !"portfolio".equals(remaining.getFirst())) {
                throw new IllegalArgumentException("portfolio command is required");
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

        private static String require(List<String> tokens, int index, String option) {
            if (index >= tokens.size() || tokens.get(index).startsWith("--")) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return tokens.get(index);
        }
    }
}
