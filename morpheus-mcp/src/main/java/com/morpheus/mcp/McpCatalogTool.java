package com.morpheus.mcp;

import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.project.ProjectSpecificationId;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.file.Path;
import java.util.Map;

/**
 * The frame of the tools {@link MorpheusMcpToolCatalog} defines: the published definition, one runtime per call, the
 * answer in canonical JSON, and a refused argument or an absent entity as a tool failure.
 *
 * <p>A capability class declares the call handler of each of its tools itself, so the handler stays the class's own;
 * the handler hands the call to {@link #answer}.</p>
 */
final class McpCatalogTool {
    private static final CanonicalJsonSerializer JSON = new CanonicalJsonSerializer();
    private static final MorpheusMcpToolCatalog CATALOG = new MorpheusMcpToolCatalog();

    private McpCatalogTool() {
    }

    /** What one tool answers, given the runtime of its call and its arguments. */
    @FunctionalInterface
    interface Answer {
        Object answer(MorpheusMcpRuntime runtime, Map<String, Object> arguments);
    }

    /** The published definition of {@code name}: its description and its input schema. */
    static McpSchema.Tool definition(String name) {
        MorpheusMcpToolCatalog.ToolDefinition definition = CATALOG.require(name);
        return McpSchema.Tool.builder(definition.name(), definition.inputSchema())
                .description(definition.description())
                .build();
    }

    /** One call: a text answer, or the refusal of an argument or of an absent entity. */
    static McpSchema.CallToolResult answer(
            Path databasePath, OperationalRecorder recorder, Map<String, Object> arguments, Answer answer) {
        try (MorpheusMcpRuntime runtime = new MorpheusMcpRuntime(databasePath, recorder)) {
            Object result = answer.answer(runtime, McpArguments.orEmpty(arguments));
            return McpSchema.CallToolResult.builder()
                    .addTextContent(result instanceof String serialized ? serialized : JSON.toJson(result))
                    .isError(false)
                    .build();
        } catch (IllegalArgumentException | KnowledgeStoreException expected) {
            return McpToolFailure.result(expected);
        }
    }

    static ProjectSpecificationId projectId(Map<String, Object> arguments) {
        return ProjectSpecificationId.parse(McpArguments.requiredString(arguments, "projectId"));
    }

    /** {@code offset} up to one million and {@code limit} up to the catalogue's maximum, as the schemas declare. */
    static PageRequest page(Map<String, Object> arguments) {
        return new PageRequest(
                McpArguments.optionalInt(arguments, "offset", 0, 0, 1_000_000),
                McpArguments.optionalInt(arguments, "limit", MorpheusMcpToolCatalog.DEFAULT_LIMIT, 1, MorpheusMcpToolCatalog.MAX_LIMIT));
    }

    static KnowledgeStoreException notFound(String message) {
        return new KnowledgeStoreException(message);
    }

    static String json(Object value) {
        return JSON.toJson(value);
    }
}
