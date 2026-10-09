package com.morpheus.mcp;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds, from the schema a tool publishes, the smallest arguments that schema accepts, so a test can reach every
 * handler past its argument checks without a hand-written list somebody has to extend for the next tool.
 *
 * <p>An identifier parses but names nothing ({@link McpToolCall#ABSENT_PROJECT_ID}): the handler has to open the
 * store to find out, which is the point.</p>
 */
// A published schema is untyped JSON: every node read here is a Map<String, Object> or a List by construction.
@SuppressWarnings("unchecked")
final class McpSchemaArguments {
    private McpSchemaArguments() {
    }

    static Map<String, Object> valid(Map<String, Object> schema) {
        return requiredArguments((Map<String, Object>) schema.get("properties"), (List<String>) schema.get("required"));
    }

    static String text(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(content -> ((McpSchema.TextContent) content).text())
                .reduce("", String::concat);
    }

    private static Map<String, Object> requiredArguments(Map<String, Object> properties, List<String> required) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        if (properties == null || required == null) {
            return arguments;
        }
        for (String name : required) {
            arguments.put(name, value(name, (Map<String, Object>) properties.get(name)));
        }
        return arguments;
    }

    private static Object value(String name, Map<String, Object> property) {
        if (property.get("enum") instanceof List<?> values && !values.isEmpty()) {
            return values.getFirst();
        }
        Object type = property.get("type");
        if (type instanceof List<?> types) {
            type = types.stream().map(String::valueOf).filter(candidate -> !"null".equals(candidate))
                    .findFirst().orElse("string");
        }
        return switch (String.valueOf(type)) {
            case "integer" -> property.get("minimum") instanceof Number minimum ? minimum.longValue() : 1L;
            case "number" -> property.get("minimum") instanceof Number minimum ? minimum.doubleValue() : 1.0d;
            case "boolean" -> Boolean.FALSE;
            case "array" -> array(name, property);
            case "object" -> requiredArguments(
                    (Map<String, Object>) property.get("properties"), (List<String>) property.get("required"));
            default -> string(name, property);
        };
    }

    private static List<Object> array(String name, Map<String, Object> property) {
        int minimum = property.get("minItems") instanceof Number count ? count.intValue() : 0;
        List<Object> items = new ArrayList<>();
        for (int index = 0; index < minimum; index++) {
            items.add(value(name, (Map<String, Object>) property.get("items")));
        }
        return items;
    }

    private static String string(String name, Map<String, Object> property) {
        String value = name.endsWith("Id") || name.endsWith("Ids") ? McpToolCall.ABSENT_PROJECT_ID : "fixture";
        int minimum = property.get("minLength") instanceof Number length ? length.intValue() : 0;
        return value.length() >= minimum ? value : value + "x".repeat(minimum - value.length());
    }
}
