package com.morpheus.mcp;

import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityResolver;
import com.morpheus.domain.provider.ProviderId;
import io.modelcontextprotocol.server.McpServerFeatures;
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
 * The only MORPHEUS MCP write tool, and the one whose guards had never been executed.
 *
 * <p>{@code apply_change_lifecycle_transition} demands {@code expectedRevision}, {@code idempotencyKey} and
 * {@code confirmed}, and until ADR-0102 not one of the three was exercised: the class measured 0 of 22
 * branches. A guard nobody calls is a guard nobody knows still works, and this is the guard standing between a
 * model-facing tool and a persisted lifecycle mutation.</p>
 *
 * <p>Two of these refusals are not errors. A mutation refused for want of confirmation, or for a stale
 * revision, is a successful answer that reports a refusal state -- so those assert on the state MORPHEUS
 * returns, and on the fact that no audit record accompanies it.</p>
 */
class MorpheusControlledLifecycleMcpToolsTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void aMissingRequiredArgumentNamesItselfAndSaysWhatWasExpected() {
        Path database = database("missing-argument.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        for (String required : List.of(
                "projectId", "changeId", "idempotencyKey", "expectedRevision", "targetState", "actor", "confirmed")) {
            Map<String, Object> arguments = new LinkedHashMap<>(validArguments(project));
            arguments.remove(required);

            McpSchema.CallToolResult result = apply(database, arguments);

            assertTrue(result.isError(), () -> required + " must be refused when absent");
            assertTrue(McpToolCall.text(result).startsWith(required + " is required and must be "),
                    () -> "refusal must name " + required + " and the expectation: " + McpToolCall.text(result));
        }
    }

    @Test
    void anArgumentOfTheWrongTypeIsRefusedAsMalformedRatherThanAsAbsent() {
        Path database = database("wrong-type.db");
        Map<String, Object> arguments = new LinkedHashMap<>(validArguments(McpToolCall.publish(database)));
        arguments.put("confirmed", "true");

        McpSchema.CallToolResult result = apply(database, arguments);

        assertTrue(result.isError());
        assertEquals("confirmed must be a boolean", McpToolCall.text(result));
    }

    @Test
    void aBlankRequiredStringIsRefusedAsMalformedRatherThanAsAbsent() {
        Path database = database("blank-actor.db");
        Map<String, Object> arguments = new LinkedHashMap<>(validArguments(McpToolCall.publish(database)));
        arguments.put("actor", "   ");

        McpSchema.CallToolResult result = apply(database, arguments);

        assertTrue(result.isError());
        assertEquals("actor must be a non-blank string", McpToolCall.text(result));
    }

    @Test
    void anOptionalArgumentThatIsPresentButUnusableIsQualifiedAsSuch() {
        Path database = database("blank-optional.db");
        Map<String, Object> arguments = new LinkedHashMap<>(validArguments(McpToolCall.publish(database)));
        arguments.put("mutationId", "");

        McpSchema.CallToolResult result = apply(database, arguments);

        assertTrue(result.isError());
        assertEquals("mutationId must be a non-blank string when present", McpToolCall.text(result));
    }

    @Test
    void aNegativeExpectedRevisionIsRefusedByTheHandlerAndNotJustByTheSchema() {
        Path database = database("negative-revision.db");
        Map<String, Object> arguments = new LinkedHashMap<>(validArguments(McpToolCall.publish(database)));
        arguments.put("expectedRevision", -1);

        McpSchema.CallToolResult result = apply(database, arguments);

        assertTrue(result.isError());
        assertEquals("expectedRevision must be non-negative", McpToolCall.text(result));
    }

    @Test
    void anUnknownLifecycleStateIsRefusedAndNamesTheValueItRejected() {
        Path database = database("unknown-state.db");
        Map<String, Object> arguments = new LinkedHashMap<>(validArguments(McpToolCall.publish(database)));
        arguments.put("targetState", "TELEPORTED");

        McpSchema.CallToolResult result = apply(database, arguments);

        assertTrue(result.isError());
        assertEquals("targetState is not a valid MORPHEUS lifecycle state: TELEPORTED", McpToolCall.text(result));
    }

    @Test
    void anUnknownAbandonmentReasonIsRefusedAndNamesTheValueItRejected() {
        Path database = database("unknown-reason.db");
        Map<String, Object> arguments = new LinkedHashMap<>(validArguments(McpToolCall.publish(database)));
        arguments.put("targetState", "ABANDONED");
        arguments.put("abandonmentReason", "BOREDOM");

        McpSchema.CallToolResult result = apply(database, arguments);

        assertTrue(result.isError());
        assertEquals("abandonmentReason is invalid: BOREDOM", McpToolCall.text(result));
    }

    @Test
    void anUnknownProjectIsRefusedBeforeAnyMutationIsAttempted() {
        Path database = database("absent-project.db");
        Map<String, Object> arguments = new LinkedHashMap<>(validArguments(McpToolCall.publish(database)));
        arguments.put("projectId", McpToolCall.ABSENT_PROJECT_ID);

        McpSchema.CallToolResult result = apply(database, arguments);

        assertTrue(result.isError());
        assertEquals("project not found: " + McpToolCall.ABSENT_PROJECT_ID, McpToolCall.text(result));
    }

    /**
     * Confirmation is the guard that separates a model proposing a mutation from a model applying one.
     */
    @Test
    void anUnconfirmedMutationIsRefusedWithoutWritingAnAuditRecord() {
        Path database = database("unconfirmed.db");
        Map<String, Object> arguments = new LinkedHashMap<>(validArguments(McpToolCall.publish(database)));
        arguments.put("confirmed", false);

        McpSchema.CallToolResult result = apply(database, arguments);
        String body = McpToolCall.text(result);

        assertFalse(result.isError(), () -> "a refusal to mutate is a successful answer: " + body);
        assertTrue(body.contains("\"state\":\"REQUIRES_CONFIRMATION\""), () -> body);
        assertTrue(body.contains("\"audit\":null"), () -> "an unconfirmed mutation must leave no audit record: " + body);
    }

    /**
     * The compare-and-set guard: a writer holding a revision that is no longer current must not win.
     */
    @Test
    void aStaleExpectedRevisionIsRefusedAsAConflictWithoutApplying() {
        Path database = database("stale-revision.db");
        Map<String, Object> arguments = new LinkedHashMap<>(validArguments(McpToolCall.publish(database)));
        arguments.put("confirmed", true);
        arguments.put("expectedRevision", 42);

        McpSchema.CallToolResult result = apply(database, arguments);
        String body = McpToolCall.text(result);

        assertFalse(result.isError(), () -> body);
        assertTrue(body.contains("\"state\":\"CONFLICT\""), () -> body);
        assertTrue(body.contains("does not match current revision"), () -> body);
        assertTrue(body.contains("\"audit\":null"), () -> "a stale writer must leave no audit record: " + body);
    }

    /**
     * Without this case the suite could not tell a handler that refuses everything from one that reads its
     * arguments: every assertion above would still pass.
     */
    @Test
    void anAcceptedCommandReachesTheMutationServiceAndAnswersWithItsDecision() {
        Path database = database("accepted.db");
        Map<String, Object> arguments = new LinkedHashMap<>(validArguments(McpToolCall.publish(database)));
        arguments.put("confirmed", true);
        arguments.put("expectedRevision", 0);

        McpSchema.CallToolResult result = apply(database, arguments);
        String body = McpToolCall.text(result);

        assertFalse(result.isError(), () -> body);
        assertFalse(body.contains("\"state\":\"REQUIRES_CONFIRMATION\""), () -> body);
        assertFalse(body.contains("\"state\":\"CONFLICT\""), () -> body);
        assertFalse(body.contains("\"state\":\"NOT_AUTHORIZED\""), () -> body);
        assertTrue(body.contains("\"reason\""), () -> "the service must have produced a decision: " + body);
    }

    /** A denied WRITE_CHANGE capability is reported as such rather than silently ignored. */
    @Test
    void aDeniedWriteCapabilityStopsTheMutationAndSaysWhy() {
        Path database = database("denied.db");
        Map<String, Object> arguments = new LinkedHashMap<>(validArguments(McpToolCall.publish(database)));
        arguments.put("confirmed", true);

        McpSchema.CallToolResult result = McpToolCall.call(
                specifications(database, projectId -> ChangeWriteCapabilityObservation.denied(
                        "No WRITE_CHANGE provider capability resolver is configured for this MCP server")),
                MorpheusControlledLifecycleMcpTools.APPLY_TOOL,
                arguments);
        String body = McpToolCall.text(result);

        assertFalse(result.isError(), () -> body);
        assertTrue(body.contains("\"state\":\"NOT_AUTHORIZED\""), () -> body);
    }

    private Map<String, Object> validArguments(McpToolCall.PublishedProject project) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("projectId", project.projectId());
        arguments.put("changeId", project.changeId());
        arguments.put("idempotencyKey", "idempotency-" + project.changeId());
        arguments.put("expectedRevision", 0);
        arguments.put("targetState", "PROPOSED");
        arguments.put("actor", "contract-test");
        arguments.put("confirmed", true);
        return arguments;
    }

    private McpSchema.CallToolResult apply(Path database, Map<String, Object> arguments) {
        return McpToolCall.call(
                specifications(database, allowedWrites()),
                MorpheusControlledLifecycleMcpTools.APPLY_TOOL,
                arguments);
    }

    private List<McpServerFeatures.SyncToolSpecification> specifications(
            Path database, ChangeWriteCapabilityResolver writeCapability) {
        return new MorpheusControlledLifecycleMcpTools(database, writeCapability).specifications();
    }

    private static ChangeWriteCapabilityResolver allowedWrites() {
        return projectId -> ChangeWriteCapabilityObservation.allowed(
                new ProviderId("mcp-failure-contract"), "explicitly granted for this contract test");
    }

    private Path database(String name) {
        return temporaryDirectory.resolve(name).toAbsolutePath().normalize();
    }
}
