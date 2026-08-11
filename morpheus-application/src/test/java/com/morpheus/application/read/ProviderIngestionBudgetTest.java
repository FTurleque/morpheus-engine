package com.morpheus.application.read;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderIngestionBudgetTest {

    @Test
    void acceptsExactBoundariesAndRejectsPlusOne() {
        ProviderIngestionBudget budget = new ProviderIngestionBudget(10, 2, 20, 3, 4, 5);

        assertDoesNotThrow(() -> budget.requireDocumentBytes(10, "doc"));
        assertDoesNotThrow(() -> budget.requireFiles(2, "corpus"));
        assertDoesNotThrow(() -> budget.requireAggregateBytes(20, "corpus"));
        assertDoesNotThrow(() -> budget.requireLines(3, "doc"));
        assertDoesNotThrow(() -> budget.requireEntities(4, "doc"));
        assertDoesNotThrow(() -> budget.requireEvidenceBytes(5, "evidence"));

        assertThrows(IllegalArgumentException.class, () -> budget.requireDocumentBytes(11, "doc"));
        assertThrows(IllegalArgumentException.class, () -> budget.requireFiles(3, "corpus"));
        assertThrows(IllegalArgumentException.class, () -> budget.requireAggregateBytes(21, "corpus"));
        assertThrows(IllegalArgumentException.class, () -> budget.requireLines(4, "doc"));
        assertThrows(IllegalArgumentException.class, () -> budget.requireEntities(5, "doc"));
        assertThrows(IllegalArgumentException.class, () -> budget.requireEvidenceBytes(6, "evidence"));
    }

    @Test
    void rejectsIncoherentConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProviderIngestionBudget(21, 2, 20, 3, 4, 5));
        assertThrows(IllegalArgumentException.class,
                () -> new ProviderIngestionBudget(0, 2, 20, 3, 4, 5));
    }
}
