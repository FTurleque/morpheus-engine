package com.morpheus.provider.openspec;

import com.morpheus.application.files.SafeWorkspaceFileResolver;
import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.read.ProviderIngestionBudget;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One read of an OpenSpec workspace charges its budget once per file it reads and checks each directory it enumerates
 * once: the probe, the change roots and the specification files are learned by the orchestrator and handed to the
 * readers, which do not learn them again.
 */
class OpenSpecReadChargesEachFileOnceTest {
    private static final Set<ReadCategory> EVERY_READ_GROUP = EnumSet.of(
            ReadCategory.CURRENT_SPECIFICATIONS,
            ReadCategory.REQUIREMENTS,
            ReadCategory.SCENARIOS,
            ReadCategory.CHANGES,
            ReadCategory.REQUIREMENT_DELTAS,
            ReadCategory.CONSTRAINTS,
            ReadCategory.DESIGN_DECISIONS,
            ReadCategory.IMPLEMENTATION_TASKS);

    private static final String CONFIG = "schema: spec-driven\n";
    private static final String SPECIFICATION = """
            # Specification demo

            ## Requirements

            ### Requirement: Readable
            The system SHALL read it.

            #### Scenario: Read
            - **WHEN** it is read
            - **THEN** it is normalized
            """;
    private static final String PROPOSAL = "# Proposal: Demo\n\n## Intent\n\nExercise the budget.\n";
    private static final String DELTA = """
            # Delta

            ## ADDED Requirements

            ### Requirement: Audit
            The system SHALL audit.
            """;

    @Test
    void aFullReadChargesTheFilesAndBytesOfEachFileItReadsOnce(@TempDir Path workspace) throws Exception {
        write(workspace, "openspec/config.yaml", CONFIG);
        write(workspace, "openspec/specs/demo/spec.md", SPECIFICATION);
        write(workspace, "openspec/changes/add-audit/proposal.md", PROPOSAL);
        write(workspace, "openspec/changes/add-audit/specs/audit/spec.md", DELTA);
        ProviderIngestionBudget.Session budget = budget(workspace, ProviderIngestionBudget.DEFAULT.maxFiles());

        ProviderReadResult result = read(workspace, EVERY_READ_GROUP, budget);

        assertEquals(ReadCategoryStatus.READ, result.report(ReadCategory.REQUIREMENT_DELTAS).orElseThrow().status(),
                result.diagnostics().toString());
        assertEquals(4, budget.fileCount(), "config.yaml, one specification, one proposal, one delta: each once");
        assertEquals(bytes(CONFIG, SPECIFICATION, PROPOSAL, DELTA), budget.aggregateBytes());
        assertEquals(lines(CONFIG, SPECIFICATION, PROPOSAL, DELTA), budget.lineCount());
    }

    @Test
    void aFileBudgetThatHoldsEveryChangeOnceIsEnoughToReadItsChangesAndDeltas(@TempDir Path workspace)
            throws Exception {
        write(workspace, "openspec/config.yaml", CONFIG);
        for (String change : List.of("alpha", "beta", "gamma")) {
            write(workspace, "openspec/changes/" + change + "/proposal.md", PROPOSAL);
        }
        ProviderIngestionBudget.Session budget = budget(workspace, 4);

        ProviderReadResult result = read(
                workspace, EnumSet.of(ReadCategory.CHANGES, ReadCategory.REQUIREMENT_DELTAS), budget);

        assertEquals(ReadCategoryStatus.READ, result.report(ReadCategory.CHANGES).orElseThrow().status(),
                result.diagnostics().toString());
        assertEquals(3, result.report(ReadCategory.CHANGES).orElseThrow().itemCount());
        assertEquals(ReadCategoryStatus.ABSENT,
                result.report(ReadCategory.REQUIREMENT_DELTAS).orElseThrow().status());
        assertEquals(4, budget.fileCount(), "config.yaml and three proposals");
    }

    @Test
    void aFileBudgetOneShortOfTheFilesReadIsStillRefused(@TempDir Path workspace) throws Exception {
        write(workspace, "openspec/config.yaml", CONFIG);
        for (String change : List.of("alpha", "beta", "gamma")) {
            write(workspace, "openspec/changes/" + change + "/proposal.md", PROPOSAL);
        }

        ProviderReadResult result = read(
                workspace,
                EnumSet.of(ReadCategory.CHANGES, ReadCategory.REQUIREMENT_DELTAS),
                budget(workspace, 3));

        assertTrue(result.content().isEmpty());
        assertEquals(ReadCategoryStatus.FAILED, result.report(ReadCategory.CHANGES).orElseThrow().status());
        Diagnostic refusal = result.diagnostics().stream()
                .filter(diagnostic -> diagnostic.code() == DiagnosticCode.INVALID_SOURCE)
                .findFirst()
                .orElseThrow();
        assertEquals("ingestion-budget", refusal.details().get("group"));
        assertTrue(refusal.message().contains("file count exceeds budget for openspec/changes: 4 > 3"),
                refusal.message());
    }

    private static ProviderReadResult read(
            Path workspace, Set<ReadCategory> categories, ProviderIngestionBudget.Session budget) {
        return new OpenSpecSpecificationContentReader().read(
                new ProviderReadRequest(workspace, ProjectSpecificationId.generate(), categories),
                new StableTestIdentityResolver(),
                budget);
    }

    private static ProviderIngestionBudget.Session budget(Path workspace, int maxFiles) throws IOException {
        ProviderIngestionBudget defaults = ProviderIngestionBudget.DEFAULT;
        return new ProviderIngestionBudget(
                defaults.maxDocumentBytes(),
                maxFiles,
                defaults.maxAggregateBytes(),
                defaults.maxLines(),
                defaults.maxBlocks(),
                defaults.maxEntities(),
                defaults.maxEvidenceBytes())
                .open(SafeWorkspaceFileResolver.rootedAt(workspace));
    }

    private static void write(Path workspace, String relative, String content) throws IOException {
        Path file = workspace.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static long bytes(String... contents) {
        long total = 0;
        for (String content : contents) total += content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        return total;
    }

    private static long lines(String... contents) {
        long total = 0;
        for (String content : contents) total += content.lines().count();
        return total;
    }

    private static final class StableTestIdentityResolver implements EntityIdentityResolver {
        private final Map<String, DomainIdentity> identities = new HashMap<>();

        @Override
        public DomainIdentity resolve(com.morpheus.domain.provider.ProviderId providerId, String entityType,
                String externalId) {
            return identities.computeIfAbsent(
                    providerId.value() + "|" + entityType + "|" + externalId, ignored -> DomainIdentity.generate());
        }
    }
}
