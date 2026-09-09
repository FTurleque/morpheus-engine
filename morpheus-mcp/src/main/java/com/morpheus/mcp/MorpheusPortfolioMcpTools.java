package com.morpheus.mcp;

import com.morpheus.application.portfolio.PortfolioPublicViews;
import com.morpheus.application.portfolio.PortfolioQueryService;
import com.morpheus.application.portfolio.PortfolioRegistryService;
import com.morpheus.application.portfolio.PortfolioTraversalDirection;
import com.morpheus.application.portfolio.PortfolioTraversalService;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.portfolio.PortfolioEntityRef;
import com.morpheus.domain.portfolio.PortfolioFreshnessState;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.store.sqlite.SqlitePortfolioStore;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** M23 MCP tools for explicit portfolio registry and bounded cross-project intelligence. */
final class MorpheusPortfolioMcpTools {
    static final String CREATE = "create_portfolio";
    static final String REGISTER_PROJECT = "register_portfolio_project";
    static final String MARK_MISSING = "mark_portfolio_project_missing";
    static final String OBSERVE_FRESHNESS = "observe_portfolio_freshness";
    static final String ADD_REFERENCE = "add_cross_project_reference";
    static final String OVERVIEW = "get_portfolio_overview";
    static final String REFERENCES = "list_portfolio_references";
    static final String TRAVERSE = "traverse_portfolio";

    private final Path databasePath;
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    MorpheusPortfolioMcpTools(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    List<McpServerFeatures.SyncToolSpecification> specifications() {
        return List.of(
                tool(CREATE, "Create a stable MORPHEUS portfolio identity.", schema(required("name"), strings("name"))),
                tool(REGISTER_PROJECT, "Register or refresh one project membership without deriving identity from paths.",
                        schema(required("portfolioId", "projectId", "name"), strings(
                                "portfolioId", "projectId", "name", "workspace", "repository", "providers"))),
                tool(MARK_MISSING, "Mark one portfolio project missing without deleting identity or references.",
                        schema(required("portfolioId", "projectId"), strings("portfolioId", "projectId"))),
                tool(OBSERVE_FRESHNESS, "Record incremental project freshness for one portfolio member.",
                        schema(required("portfolioId", "projectId", "state"), strings(
                                "portfolioId", "projectId", "state", "revision", "explanation"))),
                tool(ADD_REFERENCE, "Persist one provenance-preserving cross-project reference observation.",
                        schema(required(
                                        "portfolioId", "sourceProjectId", "sourceType", "sourceId",
                                        "targetProjectId", "targetType", "targetId", "relation", "providerId"),
                                strings(
                                        "portfolioId", "sourceProjectId", "sourceType", "sourceId",
                                        "targetProjectId", "targetType", "targetId", "relation", "providerId",
                                        "sourceLocator", "evidenceId"))),
                tool(OVERVIEW, "Return portfolio members, freshness, reference count and explicit conflicts.",
                        schema(required("portfolioId"), strings("portfolioId"))),
                tool(REFERENCES, "List portfolio-scoped or project-scoped cross-project references.",
                        pagedSchema(required("portfolioId"), strings("portfolioId", "projectId"))),
                tool(TRAVERSE, "Traverse cross-project references with explicit depth/node/link budgets.",
                        traversalSchema()));
    }

    private McpServerFeatures.SyncToolSpecification tool(
            String name,
            String description,
            Map<String, Object> inputSchema) {
        McpSchema.Tool tool = McpSchema.Tool.builder(name, inputSchema).description(description).build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> call(name, request.arguments()))
                .build();
    }

    private McpSchema.CallToolResult call(String toolName, Map<String, Object> rawArguments) {
        try {
            Map<String, Object> arguments = McpArguments.orEmpty(rawArguments);
            try (SqlitePortfolioStore store = new SqlitePortfolioStore(databasePath)) {
                PortfolioRegistryService registry = new PortfolioRegistryService(store);
                PortfolioQueryService query = new PortfolioQueryService(store);
                PortfolioTraversalService traversal = new PortfolioTraversalService(store);
                Object result = switch (toolName) {
                    case CREATE -> registry.create(McpArguments.requiredString(arguments, "name"));
                    case REGISTER_PROJECT -> registry.registerProject(
                            portfolio(arguments),
                            ProjectSpecificationId.parse(McpArguments.requiredString(arguments, "projectId")),
                            McpArguments.requiredString(arguments, "name"),
                            McpArguments.optionalString(arguments, "workspace").map(SourceLocator::file),
                            McpArguments.optionalString(arguments, "repository").map(MorpheusPortfolioMcpTools::locator),
                            providers(McpArguments.optionalString(arguments, "providers")));
                    case MARK_MISSING -> registry.markMissing(
                            portfolio(arguments), ProjectSpecificationId.parse(McpArguments.requiredString(arguments, "projectId")));
                    case OBSERVE_FRESHNESS -> registry.observeFreshness(
                            portfolio(arguments),
                            ProjectSpecificationId.parse(McpArguments.requiredString(arguments, "projectId")),
                            PortfolioFreshnessState.valueOf(McpArguments.requiredString(arguments, "state").toUpperCase(Locale.ROOT)),
                            McpArguments.optionalString(arguments, "revision"),
                            McpArguments.optionalString(arguments, "explanation"));
                    case ADD_REFERENCE -> registry.addReference(
                            portfolio(arguments),
                            entity(arguments, "source"),
                            entity(arguments, "target"),
                            McpArguments.requiredString(arguments, "relation"),
                            new ProviderId(McpArguments.requiredString(arguments, "providerId")),
                            McpArguments.optionalString(arguments, "sourceLocator").map(MorpheusPortfolioMcpTools::locator),
                            McpArguments.optionalString(arguments, "evidenceId").map(EvidenceId::parse));
                    case OVERVIEW -> query.overview(portfolio(arguments));
                    case REFERENCES -> McpArguments.optionalString(arguments, "projectId")
                            .map(ProjectSpecificationId::parse)
                            .map(projectId -> query.projectReferences(
                                    portfolio(arguments), projectId,
                                    McpArguments.optionalInt(arguments, "offset", 0, 0, Integer.MAX_VALUE),
                                    McpArguments.optionalInt(arguments, "limit", 100, 1, PortfolioQueryService.MAX_PAGE_SIZE)))
                            .orElseGet(() -> query.references(
                                    portfolio(arguments),
                                    McpArguments.optionalInt(arguments, "offset", 0, 0, Integer.MAX_VALUE),
                                    McpArguments.optionalInt(arguments, "limit", 100, 1, PortfolioQueryService.MAX_PAGE_SIZE)));
                    case TRAVERSE -> traversal.traverse(
                            portfolio(arguments),
                            new PortfolioEntityRef(
                                    ProjectSpecificationId.parse(McpArguments.requiredString(arguments, "startProjectId")),
                                    McpArguments.requiredString(arguments, "startType"),
                                    DomainIdentity.parse(McpArguments.requiredString(arguments, "startId"))),
                            McpArguments.optionalInt(arguments, "maxDepth", 4, 1, PortfolioTraversalService.MAX_DEPTH),
                            McpArguments.optionalInt(arguments, "maxNodes", 250, 1, PortfolioTraversalService.MAX_NODES),
                            McpArguments.optionalInt(arguments, "maxLinks", 1000, 1, PortfolioTraversalService.MAX_LINKS),
                            PortfolioTraversalDirection.valueOf(
                                    McpArguments.optionalString(arguments, "direction").orElse("BOTH").toUpperCase(Locale.ROOT)));
                    default -> throw new IllegalArgumentException("unknown M23 MCP tool: " + toolName);
                };
                return McpSchema.CallToolResult.builder()
                        .addTextContent(json.toJson(PortfolioPublicViews.project(result)))
                        .isError(false)
                        .build();
            }
        } catch (IllegalArgumentException | KnowledgeStoreException expected) {
            return McpToolFailure.result(expected);
        }
    }

    private static PortfolioId portfolio(Map<String, Object> arguments) {
        return PortfolioId.parse(McpArguments.requiredString(arguments, "portfolioId"));
    }

    private static PortfolioEntityRef entity(Map<String, Object> arguments, String prefix) {
        return new PortfolioEntityRef(
                ProjectSpecificationId.parse(McpArguments.requiredString(arguments, prefix + "ProjectId")),
                McpArguments.requiredString(arguments, prefix + "Type"),
                DomainIdentity.parse(McpArguments.requiredString(arguments, prefix + "Id")));
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
                .flatMap(value -> java.util.Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(ProviderId::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Map<String, Object> traversalSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.putAll(strings(
                "portfolioId", "startProjectId", "startType", "startId", "direction"));
        properties.put("maxDepth", Map.of("type", "integer", "minimum", 1, "maximum", PortfolioTraversalService.MAX_DEPTH));
        properties.put("maxNodes", Map.of("type", "integer", "minimum", 1, "maximum", PortfolioTraversalService.MAX_NODES));
        properties.put("maxLinks", Map.of("type", "integer", "minimum", 1, "maximum", PortfolioTraversalService.MAX_LINKS));
        return schema(required("portfolioId", "startProjectId", "startType", "startId"), properties);
    }

    private static Map<String, Object> pagedSchema(List<String> required, Map<String, Object> strings) {
        Map<String, Object> properties = new LinkedHashMap<>(strings);
        properties.put("offset", Map.of("type", "integer", "minimum", 0));
        properties.put("limit", Map.of("type", "integer", "minimum", 1, "maximum", PortfolioQueryService.MAX_PAGE_SIZE));
        return schema(required, properties);
    }

    private static Map<String, Object> schema(List<String> required, Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("type", "object");
        schema.put("properties", Map.copyOf(properties));
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return Map.copyOf(schema);
    }

    private static Map<String, Object> strings(String... names) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (String name : names) {
            properties.put(name, Map.of("type", "string", "minLength", 1));
        }
        return properties;
    }

    private static List<String> required(String... names) {
        return List.of(names);
    }


}
