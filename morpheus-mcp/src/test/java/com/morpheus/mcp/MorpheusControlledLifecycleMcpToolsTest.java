package com.morpheus.mcp;

import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationResultState;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityResolver;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.domain.provider.ProviderId;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * <p>The service <em>returns</em> its refusals as a result state instead of throwing them, so they never crossed
 * the handler's {@code catch} and were answered as a success whose body said {@code CONFLICT}. Each is asserted
 * here on both halves of the contract: the result is an error, and the body still carries the state, the reason
 * and no audit record.</p>
 */
class MorpheusControlledLifecycleMcpToolsTest {

    private static final Pattern STATE = Pattern.compile("\"state\":\"([A-Z_]+)\"");

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

        assertTrue(result.isError(), () -> "a refusal to mutate must read as a refusal: " + body);
        assertTrue(body.contains("\"state\":\"REQUIRES_CONFIRMATION\""), () -> body);
        assertTrue(body.contains("explicit confirmation"), () -> "the body must keep the reason: " + body);
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

        assertTrue(result.isError(), () -> "a stale writer must read as a refusal: " + body);
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
        assertTrue(body.contains("\"state\":\"APPLIED\""), () -> body);
        assertFalse(body.contains("\"audit\":null"), () -> "an applied mutation carries its audit record: " + body);
        assertTrue(body.contains("\"reason\""), () -> "the service must have produced a decision: " + body);
    }

    /** The same command retried with the same idempotency key is a success: the change is where the caller wanted it. */
    @Test
    void aRetriedCommandIsAnAlreadyAppliedSuccess() {
        Path database = database("retried.db");
        Map<String, Object> arguments = new LinkedHashMap<>(validArguments(McpToolCall.publish(database)));

        McpSchema.CallToolResult first = apply(database, arguments);
        McpSchema.CallToolResult retried = apply(database, arguments);
        String body = McpToolCall.text(retried);

        assertFalse(first.isError(), () -> McpToolCall.text(first));
        assertFalse(retried.isError(), () -> "an idempotent retry is not a refusal: " + body);
        assertTrue(body.contains("\"state\":\"ALREADY_APPLIED\""), () -> body);
    }

    /** The other CONFLICT: not a stale revision, but an idempotency key reused for a different command. */
    @Test
    void anIdempotencyKeyReusedForADifferentCommandIsRefusedAsAConflict() {
        Path database = database("reused-key.db");
        Map<String, Object> arguments = new LinkedHashMap<>(validArguments(McpToolCall.publish(database)));
        assertFalse(apply(database, arguments).isError());
        arguments.put("targetState", "COMPLETED");

        McpSchema.CallToolResult result = apply(database, arguments);
        String body = McpToolCall.text(result);

        assertTrue(result.isError(), () -> "a reused idempotency key must read as a refusal: " + body);
        assertTrue(body.contains("\"state\":\"CONFLICT\""), () -> body);
        assertTrue(body.contains("Idempotency key was already used"), () -> body);
        assertTrue(body.contains("\"audit\":null"), () -> body);
    }

    /** A transition the lifecycle rules forbid is refused by the service, not thrown: DRAFT cannot jump to COMPLETED. */
    @Test
    void aTransitionTheLifecycleRulesForbidIsRefusedAsRejected() {
        Path database = database("rejected.db");
        Map<String, Object> arguments = new LinkedHashMap<>(validArguments(McpToolCall.publish(database)));
        arguments.put("targetState", "COMPLETED");

        McpSchema.CallToolResult result = apply(database, arguments);
        String body = McpToolCall.text(result);

        assertTrue(result.isError(), () -> "a rejected transition must read as a refusal: " + body);
        assertTrue(body.contains("\"state\":\"REJECTED\""), () -> body);
        assertTrue(body.contains("not eligible for mutation"), () -> body);
        assertTrue(body.contains("\"audit\":null"), () -> body);
    }

    /**
     * The partition is decided once, on the enum, and this proves the handler consumes it for every state: adding a
     * state to the taxonomy fails here until a scenario reaches it through the tool, instead of defaulting to
     * whichever side a copy of the rule happened to assume.
     */
    @Test
    void everyResultStateIsReachedThroughTheToolAndReadsAccordingToTheEnum() {
        Path database = database("every-state.db");
        Map<String, Object> applied = new LinkedHashMap<>(validArguments(McpToolCall.publish(database)));
        Map<ChangeLifecycleMutationResultState, McpSchema.CallToolResult> observed =
                new EnumMap<>(ChangeLifecycleMutationResultState.class);

        observe(observed, apply(database, applied));
        observe(observed, apply(database, applied));
        observe(observed, apply(database, with(applied, "targetState", "COMPLETED")));
        observe(observed, apply(database, with(applied, "idempotencyKey", "stale-key", "expectedRevision", 42)));
        observe(observed, apply(database, with(applied, "idempotencyKey", "unconfirmed-key", "confirmed", false)));
        observe(observed, apply(database, with(
                applied, "idempotencyKey", "forbidden-key", "expectedRevision", 1, "targetState", "COMPLETED")));
        observe(observed, McpToolCall.call(
                specifications(database, projectId -> ChangeWriteCapabilityObservation.denied("no writer")),
                MorpheusControlledLifecycleMcpTools.APPLY_TOOL,
                with(applied, "idempotencyKey", "denied-key")));

        assertEquals(EnumSet.allOf(ChangeLifecycleMutationResultState.class), observed.keySet(),
                "every state of the taxonomy must be reached through the tool");
        observed.forEach((state, result) -> assertEquals(!state.successful(), result.isError(),
                () -> state + " must be an error exactly when it is not successful: " + McpToolCall.text(result)));
    }

    private static Map<String, Object> with(Map<String, Object> base, Object... overrides) {
        Map<String, Object> copy = new LinkedHashMap<>(base);
        for (int index = 0; index < overrides.length; index += 2) {
            copy.put((String) overrides[index], overrides[index + 1]);
        }
        return copy;
    }

    private static void observe(
            Map<ChangeLifecycleMutationResultState, McpSchema.CallToolResult> observed,
            McpSchema.CallToolResult result) {
        String body = McpToolCall.text(result);
        Matcher state = STATE.matcher(body);
        assertTrue(state.find(), () -> "the answer must carry a mutation state: " + body);
        observed.putIfAbsent(ChangeLifecycleMutationResultState.valueOf(state.group(1)), result);
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

        assertTrue(result.isError(), () -> "a denied write must read as a refusal: " + body);
        assertTrue(body.contains("\"state\":\"NOT_AUTHORIZED\""), () -> body);
        assertTrue(body.contains("No WRITE_CHANGE provider capability resolver"),
                () -> "the reason must survive: " + body);
        assertTrue(body.contains("\"audit\":null"), () -> body);
    }

    /**
     * The wiring a client actually gets: {@code MorpheusMcpServer.run} with no writer configured passes
     * {@code deniedWrites()}, so every attempt is answered {@code NOT_AUTHORIZED}. Driven over the real transport
     * and read as the client reads it -- the {@code isError} member of the JSON-RPC result -- because a handler
     * test cannot see what the SDK does to the result on its way out.
     */
    @Test
    void theDefaultWiringRefusesEveryWriteAsAnError() throws Exception {
        Path database = database("default-wiring.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);
        String arguments = new CanonicalJsonSerializer().toJson(validArguments(project));
        String input = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"refusal-test","version":"1.0"}}}
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"%s","arguments":%s}}
                """.formatted(MorpheusControlledLifecycleMcpTools.APPLY_TOOL, arguments);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        int exitCode = MorpheusMcpServer.run(
                database, new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), output);

        assertEquals(MorpheusMcpServer.EXIT_END_OF_INPUT, exitCode);
        String answer = output.toString(StandardCharsets.UTF_8).lines()
                .filter(line -> line.contains("\"id\":2"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the tool call must be answered: " + output));
        Map<?, ?> result = (Map<?, ?>) McpJsonDefaults.getMapper().readValue(answer, Map.class).get("result");
        assertEquals(Boolean.TRUE, result.get("isError"),
                () -> "a default-wired write must read as a refusal: " + answer);
        assertTrue(answer.contains("NOT_AUTHORIZED"), answer);
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
