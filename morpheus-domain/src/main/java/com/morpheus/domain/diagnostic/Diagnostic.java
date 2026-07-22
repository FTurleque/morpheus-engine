package com.morpheus.domain.diagnostic;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Machine-readable diagnostic with a human message and optional source locator.
 * Consumers must rely on {@code code}, {@code severity} and {@code details}, not the message text.
 */
public record Diagnostic(
        DiagnosticCode code,
        DiagnosticSeverity severity,
        String message,
        Map<String, String> details,
        Optional<String> source) {

    public Diagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        details = Map.copyOf(Objects.requireNonNull(details, "details"));
        source = Objects.requireNonNull(source, "source");
    }

    public static Diagnostic error(DiagnosticCode code, String message, Map<String, String> details) {
        return new Diagnostic(code, DiagnosticSeverity.ERROR, message, details, Optional.empty());
    }

    public static Diagnostic warning(DiagnosticCode code, String message, Map<String, String> details) {
        return new Diagnostic(code, DiagnosticSeverity.WARNING, message, details, Optional.empty());
    }
}
