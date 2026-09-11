package com.morpheus.mcp;

import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.context.TechnicalContextProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The M13 augmented-context tools, which had no dedicated test and measured 0 of 49 branches.
 *
 * <p>These tools take the widest argument surface in the package -- two identifiers, an external project name,
 * a token budget, a set of requested sources, a constraints map and a flag -- and every one of those readers
 * was unexercised. The collection readers matter most: they are the ones a caller is most likely to send in
 * the wrong shape, and the ones whose refusal has to say which argument was wrong.</p>
 */
class MorpheusAugmentedContextMcpToolsTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void everyRequiredArgumentIsRefusedByNameWhenAbsent() {
        Path database = database("missing.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        assertRefusal(database, MorpheusAugmentedContextMcpTools.REQUIREMENT_TOOL,
                without(project, "requirementId", "projectId"),
                "projectId is required and must be a non-blank string");
        assertRefusal(database, MorpheusAugmentedContextMcpTools.REQUIREMENT_TOOL,
                without(project, "requirementId", "requirementId"),
                "requirementId is required and must be a non-blank string");
        assertRefusal(database, MorpheusAugmentedContextMcpTools.REQUIREMENT_TOOL,
                without(project, "requirementId", "nexusProject"),
                "nexusProject is required and must be a non-blank string");
        assertRefusal(database, MorpheusAugmentedContextMcpTools.CHANGE_TOOL,
                without(project, "changeId", "changeId"),
                "changeId is required and must be a non-blank string");
    }

    @Test
    void aBlankRequiredStringIsRefusedAsMalformedRatherThanAsAbsent() {
        Path database = database("blank.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        Map<String, Object> arguments = valid(project, "requirementId");
        arguments.put("nexusProject", " ");

        assertRefusal(database, MorpheusAugmentedContextMcpTools.REQUIREMENT_TOOL, arguments,
                "nexusProject must be a non-blank string");
    }

    @Test
    void aTokenBudgetThatIsNotAWholeNumberIsRefused() {
        Path database = database("budget.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        Map<String, Object> arguments = valid(project, "requirementId");
        arguments.put("tokenBudget", 12.5);

        assertRefusal(database, MorpheusAugmentedContextMcpTools.REQUIREMENT_TOOL, arguments,
                "tokenBudget must be an integer when present");
    }

    @Test
    void aRequestedSourcesArrayOfTheWrongShapeIsRefusedByName() {
        Path database = database("sources.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        Map<String, Object> notAnArray = valid(project, "requirementId");
        notAnArray.put("requestedSources", "CODE");
        assertRefusal(database, MorpheusAugmentedContextMcpTools.REQUIREMENT_TOOL, notAnArray,
                "requestedSources must be an array of strings");

        Map<String, Object> blankItem = valid(project, "requirementId");
        blankItem.put("requestedSources", List.of("  "));
        assertRefusal(database, MorpheusAugmentedContextMcpTools.REQUIREMENT_TOOL, blankItem,
                "requestedSources must contain non-blank strings");
    }

    @Test
    void aConstraintsObjectOfTheWrongShapeIsRefusedByName() {
        Path database = database("constraints.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        Map<String, Object> notAnObject = valid(project, "requirementId");
        notAnObject.put("constraints", List.of("language"));
        assertRefusal(database, MorpheusAugmentedContextMcpTools.REQUIREMENT_TOOL, notAnObject,
                "constraints must be an object of string values");

        Map<String, Object> blankValue = valid(project, "requirementId");
        blankValue.put("constraints", Map.of("language", " "));
        assertRefusal(database, MorpheusAugmentedContextMcpTools.REQUIREMENT_TOOL, blankValue,
                "constraints must contain non-blank string keys and values");
    }

    @Test
    void anExplainFlagOfTheWrongTypeIsRefusedAsMalformed() {
        Path database = database("explain.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        Map<String, Object> arguments = valid(project, "requirementId");
        arguments.put("explain", "yes");

        assertRefusal(database, MorpheusAugmentedContextMcpTools.REQUIREMENT_TOOL, arguments,
                "explain must be a boolean when present");
    }

    @Test
    void anIdentifierThatParsesButNamesNothingIsRefusedByTheStore() {
        Path database = database("absent-project.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        Map<String, Object> arguments = valid(project, "requirementId");
        arguments.put("projectId", McpToolCall.ABSENT_PROJECT_ID);

        assertRefusal(database, MorpheusAugmentedContextMcpTools.REQUIREMENT_TOOL, arguments,
                "project not found: " + McpToolCall.ABSENT_PROJECT_ID);
    }

    /**
     * The passing case. The technical-context provider is deliberately the disabled one: MORPHEUS supplies the
     * intent and reports the external engine as unconfigured rather than failing the call.
     */
    @Test
    void aRequirementOnAPublishedProjectAnswersWithMorpheusIntentAndADisabledProvider() {
        Path database = database("published.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        McpSchema.CallToolResult result = call(database,
                MorpheusAugmentedContextMcpTools.REQUIREMENT_TOOL, valid(project, "requirementId"));
        String body = McpToolCall.text(result);

        assertFalse(result.isError(), () -> body);
        assertTrue(body.contains("NEXUS"), () -> "the unconfigured provider must be named: " + body);
    }

    private void assertRefusal(Path database, String tool, Map<String, Object> arguments, String expected) {
        McpSchema.CallToolResult result = call(database, tool, arguments);
        assertTrue(result.isError(), () -> tool + " must refuse: " + McpToolCall.text(result));
        assertEquals(expected, McpToolCall.text(result));
    }

    private McpSchema.CallToolResult call(Path database, String tool, Map<String, Object> arguments) {
        return McpToolCall.call(
                new MorpheusAugmentedContextMcpTools(database, disabledProvider()).specifications(),
                tool, arguments);
    }

    private static TechnicalContextProvider disabledProvider() {
        return new DisabledTechnicalContextProvider("NEXUS", "NEXUS integration is not configured");
    }

    private static Map<String, Object> valid(McpToolCall.PublishedProject project, String subjectIdName) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("projectId", project.projectId());
        arguments.put(subjectIdName,
                "requirementId".equals(subjectIdName) ? project.requirementId() : project.changeId());
        arguments.put("nexusProject", "morpheus-engine");
        return arguments;
    }

    private static Map<String, Object> without(
            McpToolCall.PublishedProject project, String subjectIdName, String removed) {
        Map<String, Object> arguments = valid(project, subjectIdName);
        arguments.remove(removed);
        return arguments;
    }

    private Path database(String name) {
        return temporaryDirectory.resolve(name).toAbsolutePath().normalize();
    }
}
