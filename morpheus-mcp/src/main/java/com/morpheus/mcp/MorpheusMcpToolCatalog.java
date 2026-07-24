package com.morpheus.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Exact M10 read-only MCP tool catalog with strict JSON Schema inputs. */
public final class MorpheusMcpToolCatalog {
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;
    public static final int DEFAULT_DEPTH = 2;
    public static final int MAX_DEPTH = 20;
    public static final long DEFAULT_MAX_AGE_MINUTES = 60L;
    public static final long MAX_MAX_AGE_MINUTES = 525_600L;

    private final List<ToolDefinition> tools = List.of(
            tool("get_current_specification", "Return the ACTIVE specification snapshot summary for one project.",
                    schema(required("projectId"), props("projectId", stringId()))),
            tool("find_requirements", "Find CURRENT requirements in the ACTIVE snapshot using deterministic lexical search.",
                    schema(required("projectId"), props(
                            "projectId", stringId(),
                            "query", stringValue(),
                            "offset", integer(0, 1_000_000),
                            "limit", integer(1, MAX_LIMIT)))),
            tool("get_change", "Return one normalized change proposal from the ACTIVE snapshot.",
                    schema(required("projectId", "changeId"), props("projectId", stringId(), "changeId", stringId()))),
            tool("list_changes", "List normalized change proposals from the ACTIVE snapshot.",
                    schema(required("projectId"), props(
                            "projectId", stringId(),
                            "offset", integer(0, 1_000_000),
                            "limit", integer(1, MAX_LIMIT)))),
            tool("get_constraints", "List constraints explicitly attached to one change.",
                    schema(required("projectId", "changeId"), props(
                            "projectId", stringId(), "changeId", stringId(),
                            "offset", integer(0, 1_000_000), "limit", integer(1, MAX_LIMIT)))),
            tool("get_acceptance_criteria", "Return explicit acceptance-criterion capability status for one change; never converts scenarios into criteria.",
                    schema(required("projectId", "changeId"), props("projectId", stringId(), "changeId", stringId()))),
            tool("get_design_decisions", "List design decisions explicitly attached to one change.",
                    schema(required("projectId", "changeId"), props(
                            "projectId", stringId(), "changeId", stringId(),
                            "offset", integer(0, 1_000_000), "limit", integer(1, MAX_LIMIT)))),
            tool("get_implementation_tasks", "List implementation tasks explicitly attached to one change.",
                    schema(required("projectId", "changeId"), props(
                            "projectId", stringId(), "changeId", stringId(),
                            "offset", integer(0, 1_000_000), "limit", integer(1, MAX_LIMIT)))),
            tool("trace_requirement", "Return bounded deterministic traceability context for one CURRENT requirement.",
                    schema(required("projectId", "requirementId"), props(
                            "projectId", stringId(), "requirementId", stringId(),
                            "depth", integer(1, MAX_DEPTH)))),
            tool("get_change_context", "Return bounded deterministic context and traceability for one change.",
                    schema(required("projectId", "changeId"), props(
                            "projectId", stringId(), "changeId", stringId(),
                            "depth", integer(1, MAX_DEPTH)))),
            tool("get_specification_context", "Return bounded CURRENT requirements and associated scenarios/changes for one specification.",
                    schema(required("projectId", "specificationId"), props(
                            "projectId", stringId(), "specificationId", stringId(),
                            "offset", integer(0, 1_000_000), "limit", integer(1, MAX_LIMIT)))),
            tool("get_change_status", "Return observable lifecycle facts without inferring a lifecycle state that is not persisted.",
                    schema(required("projectId", "changeId"), props("projectId", stringId(), "changeId", stringId()))),
            tool("get_blocking_conditions", "Return deterministic completeness findings and observable blocker facts for one change.",
                    schema(required("projectId", "changeId"), props("projectId", stringId(), "changeId", stringId()))),
            tool("get_sync_status", "Return persisted synchronization freshness for one project.",
                    schema(required("projectId"), props(
                            "projectId", stringId(),
                            "maxAgeMinutes", integer(1, MAX_MAX_AGE_MINUTES))))
    );

    public List<ToolDefinition> tools() {
        return tools;
    }

    public ToolDefinition require(String name) {
        Objects.requireNonNull(name, "name");
        return tools.stream()
                .filter(tool -> tool.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown MCP tool: " + name));
    }

    private static ToolDefinition tool(String name, String description, Map<String, Object> inputSchema) {
        return new ToolDefinition(name, description, inputSchema);
    }

    private static Map<String, Object> schema(List<String> required, Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return Map.copyOf(schema);
    }

    private static List<String> required(String... names) {
        return List.of(names);
    }

    private static Map<String, Object> props(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("property entries must be name/schema pairs");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], entries[index + 1]);
        }
        return Map.copyOf(result);
    }

    private static Map<String, Object> stringId() {
        return Map.of("type", "string", "minLength", 1);
    }

    private static Map<String, Object> stringValue() {
        return Map.of("type", "string");
    }

    private static Map<String, Object> integer(long minimum, long maximum) {
        return Map.of("type", "integer", "minimum", minimum, "maximum", maximum);
    }

    public record ToolDefinition(String name, String description, Map<String, Object> inputSchema) {
        public ToolDefinition {
            name = Objects.requireNonNull(name, "name");
            description = Objects.requireNonNull(description, "description");
            inputSchema = Map.copyOf(Objects.requireNonNull(inputSchema, "inputSchema"));
        }
    }
}
