package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticsApiServiceArchitectureTest {

    @Test
    void qualityDiagnosticsStayExtractedFromApiFacade() throws Exception {
        String facade = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusApiService"));
        String diagnostics = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusDiagnosticsApiService"));

        assertTrue(facade.contains("private final MorpheusDiagnosticsApiService diagnosticsService;"));
        assertTrue(facade.contains("return diagnosticsService.changeStatus(projectIdValue, changeIdValue);"));
        assertTrue(facade.contains("return diagnosticsService.blockingConditions(projectIdValue, changeIdValue);"));
        assertTrue(facade.contains("return diagnosticsService.diagnostics(projectIdValue);"));
        assertFalse(facade.contains("new QualityReportService("));
        assertFalse(facade.contains("new ChangeCompletenessService("));
        assertFalse(facade.contains("private ChangeCompletenessAssessment completeness("));
        assertFalse(facade.contains("private Object lifecycleFacts("));
        assertFalse(facade.contains("private Object finding(QualityFinding"));

        assertTrue(diagnostics.contains("final class MorpheusDiagnosticsApiService"));
        assertTrue(diagnostics.contains("new QualityReportService("));
        assertTrue(diagnostics.contains("new ChangeCompletenessService("));
        assertTrue(diagnostics.contains("private ChangeCompletenessAssessment completeness("));
        assertTrue(diagnostics.contains("private Object lifecycleFacts("));
        assertTrue(diagnostics.contains("private Object finding(QualityFinding"));
        assertFalse(diagnostics.contains("RequirementQueryService"));
        assertFalse(diagnostics.contains("LocalSourceInventoryScanner"));
        assertFalse(diagnostics.contains("MorpheusHttpResponseWriter"));
        assertFalse(diagnostics.contains("MorpheusRemote"));
    }
}
