package com.morpheus.mcp;

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
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.store.sqlite.SqliteQueryRuntime;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** M24 MCP tools; transport schemas delegate all semantics to the shared application query services. */
final class MorpheusQueryMcpTools {
    static final String EXECUTE_QUERY = "execute_query";
    static final String CREATE_SAVED_VIEW = "create_saved_view";
    static final String LIST_SAVED_VIEWS = "list_saved_views";
    static final String GET_SAVED_VIEW = "get_saved_view";
    static final String LIST_SAVED_VIEW_VERSIONS = "list_saved_view_versions";
    static final String UPDATE_SAVED_VIEW = "update_saved_view";
    static final String ARCHIVE_SAVED_VIEW = "archive_saved_view";
    static final String EXECUTE_SAVED_VIEW = "execute_saved_view";
    static final String EXPORT_QUERY = "export_query";
    static final String EXPORT_SAVED_VIEW = "export_saved_view";

    private final Path databasePath;
    private final QueryDslParser parser = new QueryDslParser();
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    MorpheusQueryMcpTools(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    List<McpServerFeatures.SyncToolSpecification> specifications() {
        return List.of(
                tool(EXECUTE_QUERY, "Execute one bounded provider-neutral MORPHEUS query.", querySchema(false)),
                tool(CREATE_SAVED_VIEW, "Persist a versioned query definition, never materialized results.", querySchema(true)),
                tool(LIST_SAVED_VIEWS, "List active saved views for one explicit project or portfolio scope.", scopeSchema()),
                tool(GET_SAVED_VIEW, "Read one saved view by stable identity.", idSchema(false)),
                tool(LIST_SAVED_VIEW_VERSIONS, "List immutable saved-view revisions in ascending order.", idSchema(false)),
                tool(UPDATE_SAVED_VIEW, "CAS-update one saved-view definition using expectedRevision.", updateSchema()),
                tool(ARCHIVE_SAVED_VIEW, "CAS-archive one saved view without deleting revision history.", idSchema(true)),
                tool(EXECUTE_SAVED_VIEW, "Execute the current stored query definition of one active saved view.", idSchema(false)),
                tool(EXPORT_QUERY, "Export the complete bounded query view as canonical JSON, CSV or Markdown.", exportQuerySchema()),
                tool(EXPORT_SAVED_VIEW, "Export one active saved view as canonical JSON, CSV or Markdown.", exportSavedViewSchema()));
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
            try (SqliteQueryRuntime runtime = SqliteQueryRuntime.open(databasePath)) {
                Object result = switch (toolName) {
                    case EXECUTE_QUERY -> QueryPublicViews.result(runtime.queries().execute(query(arguments, scope(arguments))));
                    case CREATE_SAVED_VIEW -> QueryPublicViews.savedView(runtime.views().create(
                            McpArguments.requiredString(arguments, "name"), query(arguments, scope(arguments))));
                    case LIST_SAVED_VIEWS -> QueryPublicViews.savedViews(runtime.views().list(scope(arguments)));
                    case GET_SAVED_VIEW -> QueryPublicViews.savedView(runtime.views().get(id(arguments)));
                    case LIST_SAVED_VIEW_VERSIONS -> QueryPublicViews.savedVersions(runtime.views().versions(id(arguments)));
                    case UPDATE_SAVED_VIEW -> {
                        SavedViewId id = id(arguments);
                        var current = runtime.views().get(id);
                        yield QueryPublicViews.savedView(runtime.views().update(
                                id,
                                McpArguments.requiredInteger(arguments, "expectedRevision", 1, Long.MAX_VALUE),
                                McpArguments.requiredString(arguments, "name"),
                                query(arguments, current.query().scope())));
                    }
                    case ARCHIVE_SAVED_VIEW -> QueryPublicViews.savedView(runtime.views().archive(
                            id(arguments), McpArguments.requiredInteger(arguments, "expectedRevision", 1, Long.MAX_VALUE)));
                    case EXECUTE_SAVED_VIEW -> QueryPublicViews.result(runtime.views().execute(id(arguments)));
                    case EXPORT_QUERY -> runtime.exports().export(
                            query(arguments, scope(arguments)), format(arguments)).content();
                    case EXPORT_SAVED_VIEW -> {
                        var view = runtime.views().get(id(arguments));
                        if (view.status() != SavedViewStatus.ACTIVE) {
                            throw new IllegalStateException("saved view is archived: " + view.id());
                        }
                        yield runtime.exports().export(view.query(), format(arguments)).content();
                    }
                    default -> throw new IllegalArgumentException("unknown M24 MCP tool: " + toolName);
                };
                String output = result instanceof String text ? text : json.toJson(result);
                return McpSchema.CallToolResult.builder()
                        .addTextContent(output)
                        .isError(false)
                        .build();
            }
        } catch (IllegalArgumentException | IllegalStateException | KnowledgeStoreException expected) {
            return McpToolFailure.result(expected);
        }
    }

    private QueryDefinition query(Map<String, Object> arguments, QueryScope scope) {
        return parser.parse(
                scope,
                McpArguments.requiredString(arguments, "entity"),
                McpArguments.optionalString(arguments, "filter").orElse(null),
                McpArguments.optionalString(arguments, "sort").orElse(null),
                McpArguments.optionalString(arguments, "fields").orElse(null),
                McpArguments.optionalInt(arguments, "offset", 0, 0, Integer.MAX_VALUE),
                McpArguments.optionalInt(arguments, "limit", 100, 1, QueryBudgets.MAX_PAGE_SIZE));
    }

    private QueryScope scope(Map<String, Object> arguments) {
        String kind = McpArguments.requiredString(arguments, "scopeKind").toUpperCase(Locale.ROOT);
        String id = McpArguments.requiredString(arguments, "scopeId");
        return switch (kind) {
            case "PROJECT" -> new ProjectQueryScope(ProjectSpecificationId.parse(id));
            case "PORTFOLIO" -> new PortfolioQueryScope(PortfolioId.parse(id));
            default -> throw new IllegalArgumentException("scopeKind must be PROJECT or PORTFOLIO");
        };
    }

    private SavedViewId id(Map<String, Object> arguments) {
        return SavedViewId.parse(McpArguments.requiredString(arguments, "id"));
    }

    private QueryExportFormat format(Map<String, Object> arguments) {
        try {
            return QueryExportFormat.valueOf(McpArguments.requiredString(arguments, "format").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("format must be JSON, CSV or MARKDOWN");
        }
    }

    private static Map<String, Object> querySchema(boolean nameRequired) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.putAll(scopeProperties());
        properties.putAll(queryProperties());
        if (nameRequired) {
            properties.put("name", Map.of("type", "string", "minLength", 1, "maxLength", QueryBudgets.MAX_SAVED_VIEW_NAME));
        }
        List<String> required = nameRequired
                ? List.of("name", "scopeKind", "scopeId", "entity")
                : List.of("scopeKind", "scopeId", "entity");
        return schema(required, properties);
    }

    private static Map<String, Object> updateSchema() {
        Map<String, Object> properties = new LinkedHashMap<>(queryProperties());
        properties.put("id", Map.of("type", "string", "minLength", 1));
        properties.put("expectedRevision", Map.of("type", "integer", "minimum", 1));
        properties.put("name", Map.of("type", "string", "minLength", 1, "maxLength", QueryBudgets.MAX_SAVED_VIEW_NAME));
        return schema(List.of("id", "expectedRevision", "name", "entity"), properties);
    }

    private static Map<String, Object> scopeSchema() {
        return schema(List.of("scopeKind", "scopeId"), scopeProperties());
    }

    private static Map<String, Object> idSchema(boolean revision) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Map.of("type", "string", "minLength", 1));
        if (revision) {
            properties.put("expectedRevision", Map.of("type", "integer", "minimum", 1));
        }
        return schema(revision ? List.of("id", "expectedRevision") : List.of("id"), properties);
    }

    private static Map<String, Object> exportQuerySchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.putAll(scopeProperties());
        properties.putAll(queryProperties());
        properties.put("format", formatProperty());
        return schema(List.of("scopeKind", "scopeId", "entity", "format"), properties);
    }

    private static Map<String, Object> exportSavedViewSchema() {
        return schema(
                List.of("id", "format"),
                Map.of("id", Map.of("type", "string", "minLength", 1), "format", formatProperty()));
    }

    private static Map<String, Object> queryProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("entity", Map.of("type", "string", "minLength", 1));
        properties.put("filter", Map.of("type", "string", "maxLength", QueryBudgets.MAX_ENCODED_EXPRESSION_BYTES));
        properties.put("sort", Map.of("type", "string", "minLength", 1));
        properties.put("fields", Map.of("type", "string", "minLength", 1));
        properties.put("offset", Map.of("type", "integer", "minimum", 0));
        properties.put("limit", Map.of("type", "integer", "minimum", 1, "maximum", QueryBudgets.MAX_PAGE_SIZE));
        return properties;
    }

    private static Map<String, Object> scopeProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("scopeKind", Map.of("type", "string", "enum", List.of("PROJECT", "PORTFOLIO")));
        properties.put("scopeId", Map.of("type", "string", "minLength", 1));
        return properties;
    }

    private static Map<String, Object> formatProperty() {
        return Map.of("type", "string", "enum", List.of("JSON", "CSV", "MARKDOWN"));
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



}
