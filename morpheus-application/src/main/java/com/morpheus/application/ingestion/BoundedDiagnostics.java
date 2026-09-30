package com.morpheus.application.ingestion;

import com.morpheus.application.security.ServerLocationDisclosure;
import com.morpheus.domain.diagnostic.Diagnostic;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

/**
 * The read diagnostics of one publication, as a surface relays them: the diagnostics themselves, in the vocabulary of
 * {@link Diagnostic}, most severe first and otherwise in the order they were produced, at most {@link #MAX_ITEMS} of
 * them. {@code truncated} says when some were left out and {@code truncationReason} names the bound; the total stays
 * the count every sync surface already reports.
 *
 * <p>A count told the operator that something was skipped, never what: the name of a requirement left out of a delta
 * lives in {@code details}. {@link #local} relays each diagnostic as it was produced, for the CLI, where an operator
 * fixes the files. {@link #remote} is an allowlisted projection for HTTP, which a remote caller reaches: only the
 * detail keys of {@link #REMOTE_DETAIL_KEYS}, and only values {@link ServerLocationDisclosure#isSafeToRelay} accepts; a
 * message it refuses is replaced by the code, a source it refuses is dropped. A key added by a reader later reaches a
 * remote caller only once someone has considered whether it should.</p>
 */
public record BoundedDiagnostics(List<Diagnostic> items, boolean truncated, Optional<String> truncationReason) {
    /** At most this many diagnostics are relayed; the rest are counted by the surface's diagnostic count. */
    public static final int MAX_ITEMS = 32;

    /** Detail keys the read diagnostics of a publication carry, and that name no location of the server by design. */
    public static final Set<String> REMOTE_DETAIL_KEYS = Set.of(
            "provider",
            "change",
            "section",
            "requirement",
            "fence",
            "line",
            "category",
            "group",
            "exception",
            "schema",
            "supportedSchema");

    public BoundedDiagnostics {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(truncationReason, "truncationReason");
        if (items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("at most " + MAX_ITEMS + " diagnostics are relayed");
        }
        if (truncated != truncationReason.isPresent()) {
            throw new IllegalArgumentException("truncated must be true exactly when a truncationReason is present");
        }
    }

    public static BoundedDiagnostics local(List<Diagnostic> diagnostics) {
        return bounded(diagnostics, UnaryOperator.identity());
    }

    public static BoundedDiagnostics remote(List<Diagnostic> diagnostics) {
        return bounded(diagnostics, BoundedDiagnostics::remoteSafe);
    }

    private static BoundedDiagnostics bounded(List<Diagnostic> diagnostics, UnaryOperator<Diagnostic> projection) {
        List<Diagnostic> ordered = Objects.requireNonNull(diagnostics, "diagnostics").stream()
                .sorted(Comparator.comparing(Diagnostic::severity).reversed())
                .toList();
        boolean truncated = ordered.size() > MAX_ITEMS;
        return new BoundedDiagnostics(
                ordered.stream().limit(MAX_ITEMS).map(projection).toList(),
                truncated,
                truncated ? Optional.of("DIAGNOSTIC_LIMIT_REACHED:" + MAX_ITEMS) : Optional.empty());
    }

    private static Diagnostic remoteSafe(Diagnostic diagnostic) {
        Map<String, String> details = new TreeMap<>();
        diagnostic.details().forEach((key, value) -> {
            if (REMOTE_DETAIL_KEYS.contains(key) && ServerLocationDisclosure.isSafeToRelay(value)) {
                details.put(key, value);
            }
        });
        return new Diagnostic(
                diagnostic.code(),
                diagnostic.severity(),
                ServerLocationDisclosure.isSafeToRelay(diagnostic.message())
                        ? diagnostic.message()
                        : diagnostic.code().name(),
                details,
                diagnostic.source().filter(ServerLocationDisclosure::isSafeToRelay));
    }
}
