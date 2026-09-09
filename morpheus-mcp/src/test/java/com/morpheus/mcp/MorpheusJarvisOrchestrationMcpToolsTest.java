package com.morpheus.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The M14 orchestration tools, which had no dedicated test and measured 0 of 33 branches.
 *
 * <p>These are the tools an orchestrator calls to decide whether a transition is allowed, and their whole
 * contract is that MORPHEUS never infers a lifecycle state it was not told. The guards enforcing that -- a
 * lifecycleState supplied without its abandonment reason, an abandonment reason supplied without a state --
 * had never been executed.</p>
 */
class MorpheusJarvisOrchestrationMcpToolsTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void everyRequiredArgumentIsRefusedByNameWhenAbsent() {
        Path database = database("missing.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        assertRefusal(database, MorpheusJarvisOrchestrationMcpTools.STATE_TOOL,
                Map.of("changeId", project.changeId()),
                "projectId is required and must be a non-blank string");
        assertRefusal(database, MorpheusJarvisOrchestrationMcpTools.STATE_TOOL,
                Map.of("projectId", project.projectId()),
                "changeId is required and must be a non-blank string");
        assertRefusal(database, MorpheusJarvisOrchestrationMcpTools.TRANSITION_TOOL,
                arguments(project, Map.of("targetState", "PROPOSED")),
                "fromState is required and must be a non-blank string");
        assertRefusal(database, MorpheusJarvisOrchestrationMcpTools.TRANSITION_TOOL,
                arguments(project, Map.of("fromState", "DRAFT")),
                "targetState is required and must be a non-blank string");
    }

    @Test
    void anUnknownLifecycleStateNamesTheArgumentAndTheValueItRejected() {
        Path database = database("unknown-state.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        assertRefusal(database, MorpheusJarvisOrchestrationMcpTools.TRANSITION_TOOL,
                arguments(project, Map.of("fromState", "SIDEWAYS", "targetState", "PROPOSED")),
                "fromState is not a valid MORPHEUS lifecycle state: SIDEWAYS");
        assertRefusal(database, MorpheusJarvisOrchestrationMcpTools.TRANSITION_TOOL,
                arguments(project, Map.of("fromState", "DRAFT", "targetState", "SIDEWAYS")),
                "targetState is not a valid MORPHEUS lifecycle state: SIDEWAYS");
    }

    @Test
    void anUnknownAbandonmentReasonNamesTheArgumentAndTheValueItRejected() {
        Path database = database("unknown-reason.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        assertRefusal(database, MorpheusJarvisOrchestrationMcpTools.TRANSITION_TOOL,
                arguments(project, Map.of(
                        "fromState", "DRAFT", "targetState", "ABANDONED", "abandonmentReason", "BOREDOM")),
                "abandonmentReason is not a valid MORPHEUS abandonment reason: BOREDOM");
    }

    /** ABANDONED is the one state that carries a reason, and the pairing is enforced in both directions. */
    @Test
    void anAbandonmentReasonIsRefusedWhenItsStateDoesNotCarryOne() {
        Path database = database("reason-pairing.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        assertRefusal(database, MorpheusJarvisOrchestrationMcpTools.TRANSITION_TOOL,
                arguments(project, Map.of(
                        "fromState", "DRAFT", "targetState", "PROPOSED", "fromAbandonmentReason", "OBSOLETE")),
                "fromAbandonmentReason is only valid when fromState=ABANDONED");
        assertRefusal(database, MorpheusJarvisOrchestrationMcpTools.TRANSITION_TOOL,
                arguments(project, Map.of("fromState", "ABANDONED", "targetState", "PROPOSED")),
                "fromAbandonmentReason is required when fromState=ABANDONED");
        assertRefusal(database, MorpheusJarvisOrchestrationMcpTools.STATE_TOOL,
                arguments(project, Map.of("abandonmentReason", "OBSOLETE")),
                "abandonmentReason requires lifecycleState=ABANDONED");
    }

    @Test
    void anOptionalFlagOfTheWrongTypeIsRefusedAsMalformed() {
        Path database = database("wrong-type.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        assertRefusal(database, MorpheusJarvisOrchestrationMcpTools.TRANSITION_TOOL,
                arguments(project, Map.of(
                        "fromState", "DRAFT", "targetState", "PROPOSED", "allowBackwardTransitions", "yes")),
                "allowBackwardTransitions must be a boolean when present");
    }

    @Test
    void anIdentifierThatParsesButNamesNothingIsRefusedByTheStore() {
        Path database = database("absent-project.db");
        McpToolCall.publish(database);

        assertRefusal(database, MorpheusJarvisOrchestrationMcpTools.STATE_TOOL,
                Map.of("projectId", McpToolCall.ABSENT_PROJECT_ID, "changeId", McpToolCall.ABSENT_CHANGE_ID),
                "project not found: " + McpToolCall.ABSENT_PROJECT_ID);
    }

    /**
     * The passing case, and the contract these tools exist to keep: with no lifecycleState supplied, MORPHEUS
     * reports the state as unavailable rather than guessing one.
     */
    @Test
    void aStateQueryOnAPublishedChangeReportsAnUnobservedLifecycleAsUnavailable() {
        Path database = database("published.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        McpSchema.CallToolResult result = call(database, MorpheusJarvisOrchestrationMcpTools.STATE_TOOL,
                arguments(project, Map.of()));
        String body = McpToolCall.text(result);

        assertFalse(result.isError(), () -> body);
        assertTrue(body.contains("UNAVAILABLE"), () -> "lifecycle must never be inferred: " + body);
    }

    @Test
    void aTransitionEvaluationOnAPublishedChangeAnswersWithADecision() {
        Path database = database("evaluated.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        McpSchema.CallToolResult result = call(database, MorpheusJarvisOrchestrationMcpTools.TRANSITION_TOOL,
                arguments(project, Map.of("fromState", "DRAFT", "targetState", "PROPOSED")));
        String body = McpToolCall.text(result);

        assertFalse(result.isError(), () -> body);
        assertTrue(body.contains("\"reason\""), () -> "an evaluation must state its reason: " + body);
    }

    private void assertRefusal(Path database, String tool, Map<String, Object> arguments, String expected) {
        McpSchema.CallToolResult result = call(database, tool, arguments);
        assertTrue(result.isError(), () -> tool + " must refuse: " + McpToolCall.text(result));
        assertEquals(expected, McpToolCall.text(result));
    }

    private McpSchema.CallToolResult call(Path database, String tool, Map<String, Object> arguments) {
        return McpToolCall.call(
                new MorpheusJarvisOrchestrationMcpTools(database).specifications(), tool, arguments);
    }

    private static Map<String, Object> arguments(McpToolCall.PublishedProject project, Map<String, Object> extra) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("projectId", project.projectId());
        arguments.put("changeId", project.changeId());
        arguments.putAll(extra);
        return arguments;
    }

    private Path database(String name) {
        return temporaryDirectory.resolve(name).toAbsolutePath().normalize();
    }
}
