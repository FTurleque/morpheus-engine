package com.morpheus.mcp;

import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecification;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.integration.mcp.BoundedStdioServerTransportProvider;
import com.morpheus.store.sqlite.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteTraceabilityStore;
import com.morpheus.store.sqlite.SqliteVersionedRequirementStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins, through the server's real wiring, that the guidance for an oversized response comes from this layer and
 * reaches the client. The transport cannot supply it: it is shared with the MINOS and NEXUS clients and knows no
 * catalog (ADR-0106, amendment of 23/09/2026).
 */
class MorpheusMcpServerOversizedResponseTest {

    @TempDir
    Path tempDirectory;

    @Test
    void theGuidanceIsGenericBoundedAndNamesNoTool() {
        String guidance = MorpheusMcpServer.OVERSIZED_RESPONSE_GUIDANCE;

        assertTrue(guidance.contains("offset") && guidance.contains("limit"), guidance);
        assertTrue(guidance.contains("smaller limit"), guidance);
        assertTrue(guidance.length() <= BoundedStdioServerTransportProvider.MAX_GUIDANCE_CHARS, guidance);
        for (MorpheusMcpToolCatalog.ToolDefinition tool : new MorpheusMcpToolCatalog().tools()) {
            assertFalse(guidance.contains(tool.name()), () -> "the guidance must not name " + tool.name());
        }
    }

    @Test
    void aSpecificationPastTheFrameIsAnsweredWithTheServersGuidanceAndNoFragment() {
        Path database = tempDirectory.resolve("oversized.db");
        String marker = "overflowing-description-";
        ProjectSpecificationId projectId = publishOneSpecification(
                database, marker + "x".repeat(BoundedStdioServerTransportProvider.DEFAULT_MAX_FRAME_BYTES));
        String input = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"oversized-test","version":"1.0"}}}
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_current_specification","arguments":{"projectId":"%s","limit":1}}}
                """.formatted(projectId);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        int exitCode = MorpheusMcpServer.run(
                database, new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), output);

        assertEquals(MorpheusMcpServer.EXIT_END_OF_INPUT, exitCode, "an oversized response is not a failure");
        String answer = output.toString(StandardCharsets.UTF_8).lines()
                .filter(line -> line.contains("\"id\":2"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the tool call must be answered: " + output));
        assertTrue(answer.contains(BoundedStdioServerTransportProvider.RESPONSE_TOO_LARGE), answer);
        assertTrue(answer.contains(MorpheusMcpServer.OVERSIZED_RESPONSE_GUIDANCE), answer);
        assertFalse(answer.contains(marker), () -> "the error must not leak the overflowing content: " + answer);
    }

    private static ProjectSpecificationId publishOneSpecification(Path database, String description) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SourceLocator source = SourceLocator.file("openspec/specs/large/spec.md");
        Evidence evidence = new Evidence(EvidenceId.generate(), source, Optional.empty(), Optional.empty());
        Provenance provenance = new Provenance(
                new ProviderId("oversized-test"), Optional.of("1.0"), source,
                Optional.of("fixture"), Optional.empty(), evidence.id());
        NormalizedProjectContent content = new NormalizedProjectContent(
                new ProjectSpecification(projectId, "Oversized fixture", SourceLocator.file("workspace")),
                List.of(new Specification(
                        SpecificationId.generate(), projectId, "large", "Large", Optional.of(description), provenance)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(evidence),
                List.of());
        try (SqliteSpecificationKnowledgeStore snapshots = new SqliteSpecificationKnowledgeStore(database);
             SqliteVersionedRequirementStore requirements = new SqliteVersionedRequirementStore(database);
             SqliteSnapshotBusinessContentStore businessContent = new SqliteSnapshotBusinessContentStore(database);
             SqliteTraceabilityStore traceability = new SqliteTraceabilityStore(database)) {
            new ProjectSnapshotImportService(snapshots, requirements, businessContent, traceability)
                    .publishFull(content, Optional.of("oversized-test"), Instant.parse("2026-09-23T10:00:00Z"));
        }
        return projectId;
    }
}
