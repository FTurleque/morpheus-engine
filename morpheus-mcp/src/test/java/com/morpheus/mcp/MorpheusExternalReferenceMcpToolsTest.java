package com.morpheus.mcp;

import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
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
 * The M12 external-reference tools, which had no dedicated test and measured 0 of 19 branches.
 *
 * <p>Both tools read two identifiers and refuse everything that cannot be turned into one. That refusal path
 * is the whole behaviour a caller sees when it gets an argument wrong, and it had never been executed.</p>
 */
class MorpheusExternalReferenceMcpToolsTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void everyRequiredArgumentIsRefusedByNameWhenAbsent() {
        Path database = database("missing.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        assertRefusal(database, MorpheusExternalReferenceMcpTools.LIST_TOOL,
                Map.of("ownerId", project.requirementId()),
                "projectId is required and must be a non-blank string");
        assertRefusal(database, MorpheusExternalReferenceMcpTools.LIST_TOOL,
                Map.of("projectId", project.projectId()),
                "ownerId is required and must be a non-blank string");
        assertRefusal(database, MorpheusExternalReferenceMcpTools.RESOLVE_TOOL,
                Map.of("projectId", project.projectId()),
                "referenceId is required and must be a non-blank string");
    }

    @Test
    void aBlankIdentifierIsRefusedAsMalformedRatherThanAsAbsent() {
        Path database = database("blank.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        assertRefusal(database, MorpheusExternalReferenceMcpTools.LIST_TOOL,
                map("projectId", project.projectId(), "ownerId", "  "),
                "ownerId must be a non-blank string");
    }

    @Test
    void anIdentifierOfTheWrongTypeIsRefusedWithoutReachingTheStore() {
        Path database = database("wrong-type.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        assertRefusal(database, MorpheusExternalReferenceMcpTools.LIST_TOOL,
                map("projectId", project.projectId(), "ownerId", 7),
                "ownerId must be a non-blank string");
    }

    /** A well-formed identifier that names nothing is a different failure from a malformed one. */
    @Test
    void anIdentifierThatParsesButNamesNothingIsRefusedByTheStore() {
        Path database = database("absent-project.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        assertRefusal(database, MorpheusExternalReferenceMcpTools.LIST_TOOL,
                map("projectId", McpToolCall.ABSENT_PROJECT_ID, "ownerId", project.requirementId()),
                "project not found: " + McpToolCall.ABSENT_PROJECT_ID);
        assertRefusal(database, MorpheusExternalReferenceMcpTools.RESOLVE_TOOL,
                map("projectId", McpToolCall.ABSENT_PROJECT_ID, "referenceId", McpToolCall.ABSENT_REFERENCE_ID),
                "project not found: " + McpToolCall.ABSENT_PROJECT_ID);
    }

    /** An identifier that is not a UUID at all fails while parsing, not while querying. */
    @Test
    void anIdentifierThatIsNotAnIdentifierFailsWithAMessageOfItsOwn() {
        Path database = database("not-an-id.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        McpSchema.CallToolResult result = call(database, MorpheusExternalReferenceMcpTools.LIST_TOOL,
                map("projectId", project.projectId(), "ownerId", "not-a-uuid"));

        assertTrue(result.isError());
        assertFalse(McpToolCall.text(result).isBlank(), "a parse failure must still say something");
        assertFalse(McpToolCall.text(result).startsWith("ownerId is required"),
                () -> "a present-but-unparseable id must not report as absent: " + McpToolCall.text(result));
    }

    /**
     * The passing case. Without it every assertion above would still hold for a handler that refused
     * unconditionally.
     */
    @Test
    void aListOnAPublishedProjectAnswersWithItsOwnEmptyCollection() {
        Path database = database("published.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        McpSchema.CallToolResult result = call(database, MorpheusExternalReferenceMcpTools.LIST_TOOL,
                map("projectId", project.projectId(), "ownerId", project.requirementId()));
        String body = McpToolCall.text(result);

        assertFalse(result.isError(), () -> body);
        assertTrue(body.contains(project.projectId()), () -> body);
        assertTrue(body.contains("\"items\":[]"), () -> body);
    }

    /**
     * The resolve path past the project guard: the project exists, so the refusal has to come from the
     * reference itself rather than from the project lookup that shadows it.
     */
    @Test
    void resolvingAnUnknownReferenceInAPublishedProjectFailsOnTheReferenceNotTheProject() {
        Path database = database("published-resolve.db");
        McpToolCall.PublishedProject project = McpToolCall.publish(database);

        McpSchema.CallToolResult result = call(database, MorpheusExternalReferenceMcpTools.RESOLVE_TOOL,
                map("projectId", project.projectId(), "referenceId", McpToolCall.ABSENT_REFERENCE_ID));
        String body = McpToolCall.text(result);

        assertTrue(result.isError(), () -> body);
        assertFalse(body.contains("project not found"),
                () -> "the project exists; the refusal must name the reference: " + body);
    }

    private void assertRefusal(Path database, String tool, Map<String, Object> arguments, String expected) {
        McpSchema.CallToolResult result = call(database, tool, arguments);
        assertTrue(result.isError(), () -> tool + " must refuse: " + McpToolCall.text(result));
        assertEquals(expected, McpToolCall.text(result));
    }

    private McpSchema.CallToolResult call(Path database, String tool, Map<String, Object> arguments) {
        List<McpServerFeatures.SyncToolSpecification> specifications =
                new MorpheusExternalReferenceMcpTools(database, new ExternalReferenceResolverRegistry(List.of()))
                        .specifications();
        return McpToolCall.call(specifications, tool, arguments);
    }

    private static Map<String, Object> map(String firstKey, Object firstValue, String secondKey, Object secondValue) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put(firstKey, firstValue);
        arguments.put(secondKey, secondValue);
        return arguments;
    }

    private Path database(String name) {
        return temporaryDirectory.resolve(name).toAbsolutePath().normalize();
    }
}
