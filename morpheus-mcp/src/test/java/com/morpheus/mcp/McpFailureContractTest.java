package com.morpheus.mcp;

import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.util.ToolInputValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The failure contract, asserted over the whole catalogue rather than one tool at a time (ADR-0102).
 *
 * <p>Twelve tool classes each carried their own reading of an argument and their own mapping of a failure.
 * They had diverged into five sentences for one fault and five sets of caught exceptions, and nothing noticed
 * because the suite tested schemas. Per-tool tests fix the tools that exist today; this one fixes the
 * thirteenth, by deriving what it expects from the schema each tool publishes instead of from a list somebody
 * has to remember to extend.</p>
 */
class McpFailureContractTest {
    /**
     * The canonical refusal, spelled out rather than derived from the message under test: a name the schema
     * declares required, followed by one of the expectations McpArguments knows how to state.
     */
    private static final Pattern CANONICAL_MISSING = Pattern.compile(
            "^([^ ]+) is required and must be "
                    + "(a non-blank string|a string|an integer|an integer between -?[0-9]+ and -?[0-9]+"
                    + "|a boolean|a finite number)$");

    @TempDir
    Path temporaryDirectory;

    /**
     * A handler that lets an exception escape is not answering: the transport turns it into a protocol error
     * rather than a tool result the caller can act on.
     */
    @Test
    void everyPublishedToolAnswersARefusalInsteadOfThrowing() {
        for (McpServerFeatures.SyncToolSpecification specification : specifications()) {
            String name = specification.tool().name();
            McpSchema.CallToolResult result = assertDoesNotThrow(
                    () -> McpToolCall.call(List.of(specification), name, Map.of()),
                    () -> name + " let an exception escape its handler");
            assertNotNull(result, () -> name + " answered nothing");
        }
    }

    /**
     * The canonical message, proved against the schema rather than against a hardcoded argument name: the
     * refusal must name one of the arguments the tool itself declares required, and say what was expected.
     */
    @Test
    void aMissingRequiredArgumentIsRefusedInTheCanonicalFormat() {
        List<String> checked = new ArrayList<>();
        for (McpServerFeatures.SyncToolSpecification specification : specifications()) {
            List<String> required = McpToolCall.requiredNames(specification.tool());
            if (required.isEmpty()) {
                continue;
            }
            String name = specification.tool().name();
            McpSchema.CallToolResult result = McpToolCall.call(List.of(specification), name, Map.of());
            String message = McpToolCall.text(result);

            assertTrue(result.isError(), () -> name + " accepted a call with no arguments at all: " + message);

            Matcher canonical = CANONICAL_MISSING.matcher(message);
            assertTrue(canonical.matches(),
                    () -> name + " refused outside the ADR-0102 format \"<name> is required and must be "
                            + "<expectation>\": " + message);
            assertTrue(required.contains(canonical.group(1)),
                    () -> name + " blamed an argument it does not declare required " + required + ": " + message);
            checked.add(name);
        }
        assertFalse(checked.isEmpty(), "no published tool declares a required argument, which cannot be right");
    }

    /**
     * Every tool publishes {@code additionalProperties: false}. Only one used to enforce it in its handler;
     * ADR-0102 removed that copy because the SDK refuses an unknown argument before dispatch. This is the test
     * that makes the promise true for all of them rather than for none.
     */
    @Test
    void anUnknownArgumentIsRefusedBeforeAnyHandlerSeesIt() {
        JsonSchemaValidator validator = McpJsonDefaults.getSchemaValidator();

        for (McpServerFeatures.SyncToolSpecification specification : specifications()) {
            McpSchema.Tool tool = specification.tool();
            Map<String, Object> accepted = McpToolCall.validRequiredArguments(tool);

            assertNull(validate(tool, accepted, validator),
                    () -> tool.name() + " refused arguments that satisfy its own schema: " + accepted);

            Map<String, Object> withUnknown = new LinkedHashMap<>(accepted);
            withUnknown.put("argumentTheSchemaDoesNotDeclare", "value");
            assertNotNull(validate(tool, withUnknown, validator),
                    () -> tool.name() + " accepted an argument absent from the schema it publishes");
        }
    }

    /**
     * The previous test only holds while the server asks for validation. Nothing in a tool class says so, so
     * the flag that arms it is pinned here: removing it would silently un-enforce twelve published schemas.
     */
    @Test
    void theServerArmsSchemaValidationForEveryToolItServes() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/morpheus/mcp/MorpheusMcpServer.java"));

        assertTrue(source.contains(".validateToolInputs(true)"),
                "MorpheusMcpServer must arm SDK input validation: it is what enforces additionalProperties:false "
                        + "for every tool, and ADR-0102 removed the one in-handler copy on that basis");
    }

    /** Every tool declares the strict schema the enforcement above relies on. */
    @Test
    void everyPublishedToolDeclaresAStrictSchema() {
        for (McpServerFeatures.SyncToolSpecification specification : specifications()) {
            McpSchema.Tool tool = specification.tool();
            assertNotNull(tool.inputSchema(), () -> tool.name() + " publishes no input schema");
            assertFalse((Boolean) tool.inputSchema().getOrDefault("additionalProperties", Boolean.TRUE),
                    () -> tool.name() + " does not publish additionalProperties: false");
        }
    }

    private static McpSchema.CallToolResult validate(
            McpSchema.Tool tool, Map<String, Object> arguments, JsonSchemaValidator validator) {
        return ToolInputValidator.validate(tool, arguments, true, validator);
    }

    private List<McpServerFeatures.SyncToolSpecification> specifications() {
        return MorpheusMcpServer.toolSpecifications(
                database(),
                new ExternalReferenceResolverRegistry(List.of()),
                MorpheusMcpServer.unconfiguredTechnicalContext(),
                MorpheusMcpServer.deniedWriteCapability());
    }

    private Path database() {
        return temporaryDirectory.resolve("failure-contract.db").toAbsolutePath().normalize();
    }
}
