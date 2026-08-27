package com.morpheus.application.read;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.morpheus.application.files.SafeWorkspaceFileResolver;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderIngestionBudgetTest {

    @Test
    void acceptsExactBoundariesAndRejectsPlusOne() {
        ProviderIngestionBudget budget = new ProviderIngestionBudget(10, 2, 20, 3, 4, 5, 6);

        assertDoesNotThrow(() -> budget.requireDocumentBytes(10, "doc"));
        assertDoesNotThrow(() -> budget.requireFiles(2, "corpus"));
        assertDoesNotThrow(() -> budget.requireAggregateBytes(20, "corpus"));
        assertDoesNotThrow(() -> budget.requireLines(3, "doc"));
        assertDoesNotThrow(() -> budget.requireBlocks(4, "doc"));
        assertDoesNotThrow(() -> budget.requireEntities(5, "doc"));
        assertDoesNotThrow(() -> budget.requireEvidenceBytes(6, "evidence"));

        assertThrows(IllegalArgumentException.class, () -> budget.requireDocumentBytes(11, "doc"));
        assertThrows(IllegalArgumentException.class, () -> budget.requireFiles(3, "corpus"));
        assertThrows(IllegalArgumentException.class, () -> budget.requireAggregateBytes(21, "corpus"));
        assertThrows(IllegalArgumentException.class, () -> budget.requireLines(4, "doc"));
        assertThrows(IllegalArgumentException.class, () -> budget.requireBlocks(5, "doc"));
        assertThrows(IllegalArgumentException.class, () -> budget.requireEntities(6, "doc"));
        assertThrows(IllegalArgumentException.class, () -> budget.requireEvidenceBytes(7, "evidence"));
    }

    @Test
    void rejectsIncoherentConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProviderIngestionBudget(21, 2, 20, 3, 4, 5, 6));
        assertThrows(IllegalArgumentException.class,
                () -> new ProviderIngestionBudget(0, 2, 20, 3, 4, 5, 6));
    }

    @Test
    void sessionRejectsManySmallFilesAndDoesNotAdvanceAfterFailure(@TempDir Path workspace) throws Exception {
        Files.writeString(workspace.resolve("a.md"), "12345");
        Files.writeString(workspace.resolve("b.md"), "67890");
        Files.writeString(workspace.resolve("c.md"), "x");
        ProviderIngestionBudget budget = new ProviderIngestionBudget(5, 2, 10, 3, 3, 3, 10);
        var session = budget.open(SafeWorkspaceFileResolver.rootedAt(workspace));

        assertEquals("12345", session.readDocument(Path.of("a.md")));
        assertEquals("67890", session.readDocument(Path.of("b.md")));
        assertThrows(ProviderIngestionLimitException.class, () -> session.readDocument(Path.of("c.md")));
        assertEquals(2, session.fileCount());
        assertEquals(10, session.aggregateBytes());
    }

    @Test
    void sessionRejectsAggregateBytesAcrossManySmallFiles(@TempDir Path workspace) throws Exception {
        Files.writeString(workspace.resolve("a.md"), "12345");
        Files.writeString(workspace.resolve("b.md"), "67890");
        Files.writeString(workspace.resolve("c.md"), "x");
        ProviderIngestionBudget budget = new ProviderIngestionBudget(5, 3, 10, 3, 3, 3, 10);
        var session = budget.open(SafeWorkspaceFileResolver.rootedAt(workspace));

        session.readDocument(Path.of("a.md"));
        session.readDocument(Path.of("b.md"));

        assertThrows(ProviderIngestionLimitException.class, () -> session.readDocument(Path.of("c.md")));
        assertEquals(2, session.fileCount());
        assertEquals(10, session.aggregateBytes());
    }

    @Test
    void sessionRejectsOversizedDocumentWithoutAdvancingCounters(@TempDir Path workspace) throws Exception {
        Files.writeString(workspace.resolve("large.md"), "123456");
        ProviderIngestionBudget budget = new ProviderIngestionBudget(5, 1, 10, 1, 1, 1, 5);
        var session = budget.open(SafeWorkspaceFileResolver.rootedAt(workspace));

        assertThrows(
                ProviderIngestionLimitException.class,
                () -> session.readDocument(Path.of("large.md")));
        assertEquals(0, session.fileCount());
        assertEquals(0, session.aggregateBytes());
    }

    @Test
    void sessionRejectsCumulativeEvidenceFilesAndDoesNotAdvanceAfterFailure(@TempDir Path workspace) throws Exception {
        Files.writeString(workspace.resolve("a.txt"), "1234");
        Files.writeString(workspace.resolve("b.txt"), "5678");
        ProviderIngestionBudget budget = new ProviderIngestionBudget(10, 3, 30, 10, 10, 10, 6);
        var session = budget.open(SafeWorkspaceFileResolver.rootedAt(workspace));

        assertEquals("1234", session.readEvidence(Path.of("a.txt")));
        assertThrows(ProviderIngestionLimitException.class, () -> session.readEvidence(Path.of("b.txt")));

        assertEquals(1, session.fileCount());
        assertEquals(4, session.aggregateBytes());
        assertEquals(4, session.evidenceBytes());
    }

    @Test
    void sessionRejectsCumulativeInlineEvidenceFragmentsWithoutAdvancingCounter(@TempDir Path workspace) throws Exception {
        ProviderIngestionBudget budget = new ProviderIngestionBudget(10, 3, 30, 10, 10, 10, 6);
        var session = budget.open(SafeWorkspaceFileResolver.rootedAt(workspace));

        session.addEvidenceFragment("1234", "blocks.md");
        assertThrows(
                ProviderIngestionLimitException.class,
                () -> session.addEvidenceFragment("567", "blocks.md"));

        assertEquals(4, session.evidenceBytes());
    }
}
