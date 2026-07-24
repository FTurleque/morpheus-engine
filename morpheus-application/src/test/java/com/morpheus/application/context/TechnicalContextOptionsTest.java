package com.morpheus.application.context;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechnicalContextOptionsTest {
    @Test
    void defaultsKeepExternalProjectExplicitAndUseNexusCompatibleBudget() {
        TechnicalContextOptions options = TechnicalContextOptions.defaults("nexus-project");
        assertEquals("nexus-project", options.externalProject());
        assertEquals(2000, options.tokenBudget());
        assertTrue(options.requestedSources().isEmpty());
        assertTrue(options.constraints().isEmpty());
    }

    @Test
    void acceptsOnlyControlledSourceTaxonomyAndCallerConstraints() {
        TechnicalContextOptions options = new TechnicalContextOptions(
                "project-id",
                4096,
                Set.of("FILE", "SYMBOL", "TEST", "DOCUMENTATION", "INSTRUCTION", "SKILL", "GIT"),
                Map.of("language", "java"),
                true);
        assertEquals(7, options.requestedSources().size());
        assertEquals(Map.of("language", "java"), options.constraints());
        assertTrue(options.explain());
    }

    @Test
    void rejectsImplicitProjectUnsupportedSourcesAndInvalidBudget() {
        assertThrows(IllegalArgumentException.class,
                () -> new TechnicalContextOptions(" ", 2000, Set.of(), Map.of(), false));
        assertThrows(IllegalArgumentException.class,
                () -> new TechnicalContextOptions("project", 0, Set.of(), Map.of(), false));
        assertThrows(IllegalArgumentException.class,
                () -> new TechnicalContextOptions("project", 2000, Set.of("UNKNOWN"), Map.of(), false));
    }
}
