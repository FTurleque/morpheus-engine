package com.morpheus.mcp;

import com.morpheus.application.policy.PolicyBudgets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusPolicyMcpToolsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesCompleteM25IntentSetWithStrictSchemas() {
        var specifications = new MorpheusPolicyMcpTools(temporaryDirectory.resolve("morpheus.db")).specifications();
        assertEquals(12, specifications.size());
        Set<String> names = specifications.stream().map(item -> item.tool().name()).collect(Collectors.toSet());
        assertEquals(Set.of(
                MorpheusPolicyMcpTools.CREATE,
                MorpheusPolicyMcpTools.LIST,
                MorpheusPolicyMcpTools.GET,
                MorpheusPolicyMcpTools.VERSIONS,
                MorpheusPolicyMcpTools.UPDATE,
                MorpheusPolicyMcpTools.ACTIVATE,
                MorpheusPolicyMcpTools.DEACTIVATE,
                MorpheusPolicyMcpTools.PUT_OVERRIDE,
                MorpheusPolicyMcpTools.LIST_OVERRIDES,
                MorpheusPolicyMcpTools.EVALUATE,
                MorpheusPolicyMcpTools.DRY_RUN,
                MorpheusPolicyMcpTools.AUDIT), names);

        for (var specification : specifications) {
            Map<String, Object> schema = specification.tool().inputSchema();
            assertEquals(false, schema.get("additionalProperties"), specification.tool().name());
            assertTrue(schema.containsKey("required"), specification.tool().name());
            assertTrue(schema.containsKey("properties"), specification.tool().name());
        }
    }

    @Test
    void createSchemaPublishesRuleBudgetAndNoExecutablePolicyFields() {
        var create = new MorpheusPolicyMcpTools(temporaryDirectory.resolve("morpheus.db")).specifications().stream()
                .filter(item -> item.tool().name().equals(MorpheusPolicyMcpTools.CREATE))
                .findFirst().orElseThrow().tool().inputSchema();
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) create.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> rules = (Map<String, Object>) properties.get("rules");
        assertEquals(PolicyBudgets.MAX_RULES_PER_PACK, rules.get("maxItems"));

        Set<String> propertyNames = propertyNames(create);
        assertTrue(propertyNames.contains("description"));
        assertFalse(propertyNames.contains("sql"));
        assertFalse(propertyNames.contains("script"));
        assertFalse(propertyNames.contains("classname"));
    }

    @Test
    void serverCatalogAcceptsAllM25ToolsWithoutNameOrSchemaCollision() {
        var server = MorpheusMcpServer.build(temporaryDirectory.resolve("morpheus.db"));
        try {
            assertTrue(server != null);
        } finally {
            server.close();
        }
    }

    private Set<String> propertyNames(Map<String, Object> schema) {
        Set<String> result = new HashSet<>();
        Object rawProperties = schema.get("properties");
        if (rawProperties instanceof Map<?, ?> properties) {
            for (Map.Entry<?, ?> entry : properties.entrySet()) {
                if (entry.getKey() instanceof String name) {
                    result.add(name.toLowerCase(Locale.ROOT));
                }
                if (entry.getValue() instanceof Map<?, ?> nested) {
                    result.addAll(propertyNames(stringKeyMap(nested)));
                }
            }
        }
        Object rawItems = schema.get("items");
        if (rawItems instanceof Map<?, ?> items) {
            result.addAll(propertyNames(stringKeyMap(items)));
        }
        return Set.copyOf(result);
    }

    private Map<String, Object> stringKeyMap(Map<?, ?> raw) {
        return raw.entrySet().stream()
                .filter(entry -> entry.getKey() instanceof String)
                .collect(Collectors.toMap(entry -> (String) entry.getKey(), entry -> entry.getValue()));
    }
}
