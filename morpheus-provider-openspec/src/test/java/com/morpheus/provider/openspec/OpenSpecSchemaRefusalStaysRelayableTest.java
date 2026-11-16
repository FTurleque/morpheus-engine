package com.morpheus.provider.openspec;

import com.morpheus.application.ingestion.IncompleteProjectReadException;
import com.morpheus.application.ingestion.ProjectContentIngestion;
import com.morpheus.application.security.ServerLocationDisclosure;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The refusal of a schema repeats the declared name, and the text of a refused read is relayed to a caller outside the
 * machine only under {@link ServerLocationDisclosure#MAX_RELAYED_LENGTH}: past it the boundary replaces the whole text
 * by the name of the failure's type. The longest name the message repeats is measured here on the real text of a read
 * whose every category failed, for each refusal reason.
 */
class OpenSpecSchemaRefusalStaysRelayableTest {
    private static final String LONGEST_NAMED = "a".repeat(OpenSpecSpecificationProvider.MAX_NAMED_SCHEMA_LENGTH);

    @Test
    void theRefusedReadOfTheLongestNamedSchemaIsRelayableForEveryReason(@TempDir Path root) throws IOException {
        List<Optional<String>> definitions = List.of(
                Optional.empty(),
                Optional.of("name: any\n"),
                Optional.of("derives: " + "b".repeat(200) + "\n"));
        for (int index = 0; index < definitions.size(); index++) {
            Path workspace = workspace(root.resolve("reason-" + index), LONGEST_NAMED, definitions.get(index));

            IncompleteProjectReadException refused = refusedRead(workspace);

            Diagnostic diagnostic = onlyError(refused);
            assertTrue(diagnostic.message().contains("'" + LONGEST_NAMED + "'"), diagnostic.message());
            assertTrue(refused.getMessage().contains(diagnostic.message()), refused.getMessage());
            assertTrue(ServerLocationDisclosure.isSafeToRelay(refused.getMessage()),
                    refused.getMessage().length() + " characters: " + refused.getMessage());
        }
    }

    @Test
    void aNameLongerThanTheBoundIsNotRepeatedAndStaysInTheDetails(@TempDir Path root) throws IOException {
        String tooLong = LONGEST_NAMED + "a";
        Path workspace = workspace(root.resolve("long"), tooLong, Optional.of("name: any\n"));

        IncompleteProjectReadException refused = refusedRead(workspace);

        Diagnostic diagnostic = onlyError(refused);
        assertEquals("The declared OpenSpec schema is not supported: openspec/schemas/<schema>/schema.yaml must declare "
                + "'derives: spec-driven'.", diagnostic.message());
        assertEquals(tooLong, diagnostic.details().get("schema"));
        assertTrue(ServerLocationDisclosure.isSafeToRelay(refused.getMessage()), refused.getMessage());
    }

    @Test
    void aNameThatIsNotASchemaNameIsNotRepeatedAndTheRefusedReadIsRelayable(@TempDir Path root) throws IOException {
        Path workspace = workspace(root.resolve("invalid"), "../../etc/" + "c".repeat(300), Optional.empty());

        IncompleteProjectReadException refused = refusedRead(workspace);

        Diagnostic diagnostic = onlyError(refused);
        assertEquals("invalid-schema-name", diagnostic.details().get("reason"));
        assertFalse(diagnostic.message().contains("etc"), diagnostic.message());
        assertTrue(ServerLocationDisclosure.isSafeToRelay(refused.getMessage()), refused.getMessage());
    }

    private static Diagnostic onlyError(IncompleteProjectReadException refused) {
        assertEquals(1, refused.providerErrors().size(), refused.getMessage());
        Diagnostic diagnostic = refused.providerErrors().getFirst();
        assertEquals(DiagnosticCode.UNSUPPORTED_PROVIDER_SCHEMA, diagnostic.code());
        return diagnostic;
    }

    private static IncompleteProjectReadException refusedRead(Path workspace) {
        return assertThrows(IncompleteProjectReadException.class,
                () -> new ProjectContentIngestion(new OpenSpecSpecificationContentReader()).read(
                        workspace,
                        ProjectSpecificationId.generate(),
                        (providerId, entityType, externalId) -> DomainIdentity.generate()));
    }

    private static Path workspace(Path workspace, String schema, Optional<String> definition) throws IOException {
        Files.createDirectories(workspace.resolve("openspec"));
        Files.writeString(workspace.resolve("openspec/config.yaml"), "schema: " + schema + "\n");
        if (definition.isPresent()) {
            Path directory = Files.createDirectories(workspace.resolve("openspec/schemas").resolve(schema));
            Files.writeString(directory.resolve("schema.yaml"), definition.orElseThrow());
        }
        return workspace;
    }
}
