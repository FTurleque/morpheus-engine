package com.morpheus.application.ingestion;

import com.morpheus.application.security.ServerLocationDisclosure;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedDiagnosticsTest {
    private static final String SOURCE = "openspec/changes/unclosed/specs/auth-session/spec.md";

    @Test
    void theNameOfASkippedRequirementIsRelayedOnBothScales() {
        Diagnostic skipped = skipped("Keep the audit trail");

        for (BoundedDiagnostics relayed : List.of(
                BoundedDiagnostics.local(List.of(skipped)),
                BoundedDiagnostics.remote(List.of(skipped)))) {
            Diagnostic item = relayed.items().getFirst();
            assertEquals(DiagnosticCode.PARTIAL_INGESTION, item.code());
            assertEquals("Keep the audit trail", item.details().get("requirement"));
            assertEquals("11", item.details().get("line"));
            assertEquals(SOURCE, item.source().orElseThrow());
            assertFalse(relayed.truncated());
            assertTrue(relayed.truncationReason().isEmpty());
        }
    }

    @Test
    void theMostSevereComeFirstAndTheOrderIsOtherwiseKept() {
        Diagnostic info = new Diagnostic(DiagnosticCode.OPTIONAL_CAPABILITY_UNAVAILABLE, DiagnosticSeverity.INFO,
                "info", Map.of(), Optional.empty());
        Diagnostic first = skipped("First");
        Diagnostic second = skipped("Second");

        List<Diagnostic> items = BoundedDiagnostics.local(List.of(info, first, second)).items();

        assertEquals(List.of("First", "Second"), items.subList(0, 2).stream()
                .map(item -> item.details().get("requirement"))
                .toList());
        assertEquals(DiagnosticSeverity.INFO, items.get(2).severity());
    }

    @Test
    void beyondTheBoundTheListIsTruncatedAndSaysWhy() {
        List<Diagnostic> many = new ArrayList<>();
        for (int index = 0; index <= BoundedDiagnostics.MAX_ITEMS; index++) {
            many.add(skipped("Requirement " + index));
        }

        for (BoundedDiagnostics relayed : List.of(BoundedDiagnostics.local(many), BoundedDiagnostics.remote(many))) {
            assertEquals(BoundedDiagnostics.MAX_ITEMS, relayed.items().size());
            assertTrue(relayed.truncated());
            assertEquals(Optional.of("DIAGNOSTIC_LIMIT_REACHED:" + BoundedDiagnostics.MAX_ITEMS),
                    relayed.truncationReason());
            assertEquals("Requirement 0", relayed.items().getFirst().details().get("requirement"));
        }
        assertFalse(BoundedDiagnostics.local(many.subList(0, BoundedDiagnostics.MAX_ITEMS)).truncated());
    }

    @Test
    void theRemoteProjectionRelaysOnlyAllowlistedKeysAndValuesThatNameNoServerLocation() {
        Diagnostic leaking = new Diagnostic(
                DiagnosticCode.INVALID_SOURCE,
                DiagnosticSeverity.WARNING,
                "cannot read /srv/morpheus/workspace/openspec/spec.md",
                Map.of(
                        "provider", "openspec",
                        "requirement", "C:\\Users\\operator\\secret",
                        "directory", "relative/looking/value"),
                Optional.of("/srv/morpheus/workspace/openspec/spec.md"));

        Diagnostic remote = BoundedDiagnostics.remote(List.of(leaking)).items().getFirst();
        assertEquals("INVALID_SOURCE", remote.message());
        assertEquals(Map.of("provider", "openspec"), remote.details());
        assertTrue(remote.source().isEmpty());

        Diagnostic local = BoundedDiagnostics.local(List.of(leaking)).items().getFirst();
        assertEquals(leaking, local);
    }

    @Test
    void everyValueTheRemoteProjectionKeepsIsSafeToRelay() {
        Diagnostic unclosed = new Diagnostic(
                DiagnosticCode.UNCLOSED_CODE_FENCE,
                DiagnosticSeverity.WARNING,
                "OpenSpec delta file opens a code fence that is never closed; the rest of the file is fenced",
                Map.of("provider", "openspec", "change", "unclosed", "fence", "```", "line", "7"),
                Optional.of(SOURCE));

        Diagnostic remote = BoundedDiagnostics.remote(List.of(unclosed)).items().getFirst();

        assertEquals(unclosed, remote);
        assertTrue(ServerLocationDisclosure.isSafeToRelay(remote.message()));
        remote.details().forEach((key, value) -> assertTrue(ServerLocationDisclosure.isSafeToRelay(value), key));
    }

    @Test
    void aTruncationReasonIsPresentExactlyWhenTheListIsTruncated() {
        IllegalArgumentException silent = assertThrows(IllegalArgumentException.class,
                () -> new BoundedDiagnostics(List.of(), true, Optional.empty()));
        assertTrue(silent.getMessage().contains("truncationReason"), silent.getMessage());
        IllegalArgumentException unexplained = assertThrows(IllegalArgumentException.class,
                () -> new BoundedDiagnostics(List.of(), false, Optional.of("DIAGNOSTIC_LIMIT_REACHED:32")));
        assertTrue(unexplained.getMessage().contains("truncationReason"), unexplained.getMessage());
    }

    private static Diagnostic skipped(String requirement) {
        return new Diagnostic(
                DiagnosticCode.PARTIAL_INGESTION,
                DiagnosticSeverity.WARNING,
                "OpenSpec requirement follows a code fence that is never closed and was not normalized",
                Map.of("provider", "openspec", "change", "unclosed", "requirement", requirement, "line", "11"),
                Optional.of(SOURCE));
    }
}
