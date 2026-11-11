package com.morpheus.domain.diagnostic;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiagnosticTest {

    @Test
    void anErrorCarriesItsCodeMessageAndDetailsWithoutASource() {
        Diagnostic diagnostic = Diagnostic.error(DiagnosticCode.INVALID_SOURCE, "bad", Map.of("path", "a.md"));

        assertEquals(DiagnosticCode.INVALID_SOURCE, diagnostic.code());
        assertEquals(DiagnosticSeverity.ERROR, diagnostic.severity());
        assertEquals("bad", diagnostic.message());
        assertEquals(Map.of("path", "a.md"), diagnostic.details());
        assertEquals(Optional.empty(), diagnostic.source());
    }

    @Test
    void aWarningIsOfWarningSeverity() {
        Diagnostic diagnostic = Diagnostic.warning(DiagnosticCode.UNRECOGNIZED_SECTION, "skipped", Map.of());

        assertEquals(DiagnosticSeverity.WARNING, diagnostic.severity());
        assertEquals(DiagnosticCode.UNRECOGNIZED_SECTION, diagnostic.code());
    }

    @Test
    void theDetailsAreACopyTheCallerCannotChangeAfterwards() {
        Map<String, String> details = new HashMap<>(Map.of("k", "v"));
        Diagnostic diagnostic = new Diagnostic(DiagnosticCode.PARTIAL_INGESTION, DiagnosticSeverity.INFO, "m",
                details, Optional.of("file:a.md"));
        details.put("other", "x");

        assertEquals(Map.of("k", "v"), diagnostic.details());
        assertEquals(Optional.of("file:a.md"), diagnostic.source());
    }

    @Test
    void anAbsentComponentIsRefusedByName() {
        assertEquals("code", assertThrows(NullPointerException.class,
                () -> new Diagnostic(null, DiagnosticSeverity.FATAL, "m", Map.of(), Optional.empty())).getMessage());
        assertEquals("severity", assertThrows(NullPointerException.class,
                () -> new Diagnostic(DiagnosticCode.NO_PROVIDER_FOUND, null, "m", Map.of(), Optional.empty()))
                .getMessage());
        assertEquals("message", assertThrows(NullPointerException.class,
                () -> Diagnostic.error(DiagnosticCode.NO_PROVIDER_FOUND, null, Map.of())).getMessage());
        assertEquals("details", assertThrows(NullPointerException.class,
                () -> Diagnostic.warning(DiagnosticCode.NO_PROVIDER_FOUND, "m", null)).getMessage());
        assertEquals("source", assertThrows(NullPointerException.class,
                () -> new Diagnostic(DiagnosticCode.NO_PROVIDER_FOUND, DiagnosticSeverity.FATAL, "m", Map.of(),
                        null)).getMessage());
    }
}
