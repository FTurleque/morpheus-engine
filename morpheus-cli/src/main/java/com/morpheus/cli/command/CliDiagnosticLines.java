package com.morpheus.cli.command;

import com.morpheus.application.ingestion.BoundedDiagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * The text lines of the diagnostics of a project read, published or refused: one {@code diagnostic=} line per relayed
 * diagnostic with its severity, code, source, sorted details and message -- a skipped requirement, a refused schema
 * and its reason are in the details -- then {@code truncationReason=} when some were left out.
 */
final class CliDiagnosticLines {
    private CliDiagnosticLines() {
    }

    static List<String> of(BoundedDiagnostics diagnostics) {
        List<String> lines = new ArrayList<>();
        diagnostics.items().forEach(diagnostic -> lines.add("diagnostic=" + diagnostic.severity()
                + " " + diagnostic.code()
                + " " + diagnostic.source().orElse("-")
                + " " + new TreeMap<>(diagnostic.details())
                + " " + diagnostic.message()));
        diagnostics.truncationReason().ifPresent(reason -> lines.add("truncationReason=" + reason));
        return lines;
    }
}
