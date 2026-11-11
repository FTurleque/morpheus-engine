package com.morpheus.domain.provider;

import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderProbeResultTest {
    private static final ProviderId PROVIDER = new ProviderId("openspec");
    private static final SourceLocator SOURCE = SourceLocator.file("openspec");
    private static final Diagnostic WARNING =
            Diagnostic.warning(DiagnosticCode.UNRECOGNIZED_SECTION, "skipped", Map.of());

    @Test
    void aSupportedResultKeepsItsComponentsAndATrimmedVersion() {
        ProviderCapabilitySet capabilities = ProviderCapabilitySet.of(ProviderCapability.READ_REQUIREMENTS);
        ProviderProbeResult result = new ProviderProbeResult(PROVIDER, " 1.2 ", ProviderProbeStatus.SUPPORTED,
                Optional.of("spec-driven"), Optional.of("1"), Optional.of(SOURCE), capabilities, true, List.of());

        assertTrue(result.supported());
        assertEquals(PROVIDER, result.providerId());
        assertEquals("1.2", result.providerVersion());
        assertEquals(Optional.of("spec-driven"), result.schema());
        assertEquals(Optional.of("1"), result.formatVersion());
        assertEquals(Optional.of(SOURCE), result.sourceLocator());
        assertEquals(capabilities, result.capabilities());
        assertTrue(result.remote());
    }

    @Test
    void onlyASupportedStatusIsSupported() {
        for (ProviderProbeStatus status : List.of(
                ProviderProbeStatus.UNSUPPORTED, ProviderProbeStatus.AMBIGUOUS, ProviderProbeStatus.INVALID)) {
            assertFalse(result(status, Optional.of(SOURCE), List.of()).supported(), status::name);
        }
    }

    @Test
    void aSourceIsRecognizedWhenLocatedInvalidOrDiagnosed() {
        assertTrue(result(ProviderProbeStatus.UNSUPPORTED, Optional.of(SOURCE), List.of()).recognizedSource());
        assertTrue(result(ProviderProbeStatus.INVALID, Optional.empty(), List.of()).recognizedSource());
        assertTrue(result(ProviderProbeStatus.UNSUPPORTED, Optional.empty(), List.of(WARNING)).recognizedSource());
    }

    @Test
    void aSourceIsNotRecognizedWithoutLocatorInvalidityOrDiagnostic() {
        assertFalse(result(ProviderProbeStatus.UNSUPPORTED, Optional.empty(), List.of()).recognizedSource());
        assertFalse(result(ProviderProbeStatus.AMBIGUOUS, Optional.empty(), List.of()).recognizedSource());
    }

    @Test
    void theDiagnosticsAreACopyTheCallerCannotChangeAfterwards() {
        List<Diagnostic> diagnostics = new ArrayList<>();
        ProviderProbeResult result = result(ProviderProbeStatus.UNSUPPORTED, Optional.empty(), diagnostics);
        diagnostics.add(WARNING);

        assertEquals(List.of(), result.diagnostics());
        assertFalse(result.recognizedSource());
    }

    @Test
    void anAbsentComponentIsRefusedByName() {
        ProviderCapabilitySet none = ProviderCapabilitySet.of();
        assertEquals("providerId", assertThrows(NullPointerException.class,
                () -> new ProviderProbeResult(null, "1", ProviderProbeStatus.SUPPORTED, Optional.empty(),
                        Optional.empty(), Optional.empty(), none, false, List.of())).getMessage());
        assertEquals("providerVersion", assertThrows(NullPointerException.class,
                () -> new ProviderProbeResult(PROVIDER, null, ProviderProbeStatus.SUPPORTED, Optional.empty(),
                        Optional.empty(), Optional.empty(), none, false, List.of())).getMessage());
        assertEquals("status", assertThrows(NullPointerException.class,
                () -> new ProviderProbeResult(PROVIDER, "1", null, Optional.empty(),
                        Optional.empty(), Optional.empty(), none, false, List.of())).getMessage());
        assertEquals("schema", assertThrows(NullPointerException.class,
                () -> new ProviderProbeResult(PROVIDER, "1", ProviderProbeStatus.SUPPORTED, null,
                        Optional.empty(), Optional.empty(), none, false, List.of())).getMessage());
        assertEquals("formatVersion", assertThrows(NullPointerException.class,
                () -> new ProviderProbeResult(PROVIDER, "1", ProviderProbeStatus.SUPPORTED, Optional.empty(),
                        null, Optional.empty(), none, false, List.of())).getMessage());
        assertEquals("sourceLocator", assertThrows(NullPointerException.class,
                () -> new ProviderProbeResult(PROVIDER, "1", ProviderProbeStatus.SUPPORTED, Optional.empty(),
                        Optional.empty(), null, none, false, List.of())).getMessage());
        assertEquals("capabilities", assertThrows(NullPointerException.class,
                () -> new ProviderProbeResult(PROVIDER, "1", ProviderProbeStatus.SUPPORTED, Optional.empty(),
                        Optional.empty(), Optional.empty(), null, false, List.of())).getMessage());
        assertEquals("diagnostics", assertThrows(NullPointerException.class,
                () -> new ProviderProbeResult(PROVIDER, "1", ProviderProbeStatus.SUPPORTED, Optional.empty(),
                        Optional.empty(), Optional.empty(), none, false, null)).getMessage());
    }

    private static ProviderProbeResult result(
            ProviderProbeStatus status, Optional<SourceLocator> source, List<Diagnostic> diagnostics) {
        return new ProviderProbeResult(PROVIDER, "1", status, Optional.empty(), Optional.empty(), source,
                ProviderCapabilitySet.of(), false, diagnostics);
    }
}
