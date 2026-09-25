package com.morpheus.mcp;

import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.context.TechnicalContextBundle;
import com.morpheus.application.context.TechnicalContextItem;
import com.morpheus.application.context.TechnicalContextObservation;
import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.context.TechnicalContextRequest;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.security.ServerLocationDisclosure;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
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

    /**
     * The tools answer a model, not the operator, and carry the same status object the HTTP routes project. The
     * property is asserted on every decoded string value of the result rather than on a field name.
     */
    @Test
    void noToolResultNamesWhereTheServerKeepsNexus() {
        for (boolean available : new boolean[] {true, false}) {
            Path database = database("nex1-" + available + ".db");
            McpToolCall.PublishedProject project = McpToolCall.publish(database);
            TechnicalContextProvider provider = launchedNexus(available);
            List<McpServerFeatures.SyncToolSpecification> tools =
                    new MorpheusAugmentedContextMcpTools(database, provider).specifications();

            for (McpSchema.CallToolResult result : List.of(
                    McpToolCall.call(tools, MorpheusAugmentedContextMcpTools.REQUIREMENT_TOOL,
                            valid(project, "requirementId")),
                    McpToolCall.call(tools, MorpheusAugmentedContextMcpTools.CHANGE_TOOL,
                            valid(project, "changeId")))) {
                String body = McpToolCall.text(result);
                assertFalse(result.isError(), () -> body);
                for (String value : jsonStrings(body)) {
                    assertFalse(ServerLocationDisclosure.namesAServerLocation(value),
                            () -> "tool result named a server location: " + value);
                }
                assertTrue(body.contains("\"jarPathConfigured\":\"true\""), body);
                assertTrue(body.contains("\"homeDirectoryConfigured\":\"true\""), body);
                assertTrue(body.contains("\"javaCommandConfigured\":\"true\""), body);
                if (available) {
                    assertTrue(body.contains("\"projectId\":\"nexus-project-id\""), body);
                    assertTrue(body.contains("\"projectName\":\"morpheus-engine\""), body);
                    assertTrue(body.contains("\"estimatedTokens\":\"222\""), body);
                } else {
                    assertTrue(body.contains("\"state\":\"UNAVAILABLE\""), body);
                }
            }
        }
    }

    private static TechnicalContextProvider launchedNexus(boolean available) {
        Map<String, String> settings = Map.of(
                "javaCommand", "/usr/lib/jvm/temurin-21/bin/java",
                "jar", "tools/nexus/nexus-server.jar",
                "home", "/home/alice/.nexus",
                "timeoutSeconds", "30");
        return new TechnicalContextProvider() {
            @Override
            public String system() {
                return "NEXUS";
            }

            @Override
            public ExternalIntegrationStatus status() {
                return new ExternalIntegrationStatus(
                        "NEXUS", "AVAILABLE", true, "NEXUS MCP integration is available", settings);
            }

            @Override
            public TechnicalContextObservation build(TechnicalContextRequest request) {
                Map<String, String> details = new LinkedHashMap<>(settings);
                if (!available) {
                    return TechnicalContextObservation.unavailable(new ExternalIntegrationStatus(
                            "NEXUS", "UNAVAILABLE", true,
                            "NEXUS integration is unavailable: Cannot run program \"/usr/lib/jvm/temurin-21/bin/java\"",
                            details));
                }
                details.put("projectId", "nexus-project-id");
                details.put("projectName", "morpheus-engine");
                details.put("estimatedTokens", "222");
                TechnicalContextBundle bundle = new TechnicalContextBundle(
                        "nexus-project-id", request.options().externalProject(), request.query(), false, 1,
                        request.options().tokenBudget(), 222,
                        List.of(new TechnicalContextItem(
                                "SYMBOL", "src/main/java/SessionService.java", "SessionService", 11, 21,
                                "class SessionService {}", 0.5, Map.of(), List.of(), 222, false)),
                        List.of(), Map.of("engine", "NEXUS"));
                return TechnicalContextObservation.available(new ExternalIntegrationStatus(
                        "NEXUS", "AVAILABLE", true, "NEXUS technical context built successfully", details), bundle);
            }
        };
    }

    /** Every string literal of a JSON document, decoded, so a check sees the value and not its escaped form. */
    private static List<String> jsonStrings(String json) {
        List<String> values = new ArrayList<>();
        int index = 0;
        while (index < json.length()) {
            if (json.charAt(index++) != '"') {
                continue;
            }
            StringBuilder value = new StringBuilder();
            while (index < json.length() && json.charAt(index) != '"') {
                char current = json.charAt(index++);
                if (current != '\\') {
                    value.append(current);
                    continue;
                }
                char escaped = json.charAt(index++);
                switch (escaped) {
                    case 'n' -> value.append('\n');
                    case 't' -> value.append('\t');
                    case 'r' -> value.append('\r');
                    case 'u' -> {
                        value.append((char) Integer.parseInt(json.substring(index, index + 4), 16));
                        index += 4;
                    }
                    default -> value.append(escaped);
                }
            }
            index++;
            values.add(value.toString());
        }
        return values;
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
