package com.morpheus.sdk.provider;

import java.util.Map;
import java.util.Objects;

/** Provider-plugin diagnostic that is safe to expose through CLI, MCP and HTTP. */
public record ProviderPluginDiagnostic(
        Severity severity,
        String code,
        String message,
        Map<String, String> details) {

    public ProviderPluginDiagnostic {
        Objects.requireNonNull(severity, "severity");
        code = requireText(code, "code");
        message = requireText(message, "message");
        details = Map.copyOf(Objects.requireNonNull(details, "details"));
    }

    public static ProviderPluginDiagnostic info(String code, String message, Map<String, String> details) {
        return new ProviderPluginDiagnostic(Severity.INFO, code, message, details);
    }

    public static ProviderPluginDiagnostic warning(String code, String message, Map<String, String> details) {
        return new ProviderPluginDiagnostic(Severity.WARNING, code, message, details);
    }

    public static ProviderPluginDiagnostic error(String code, String message, Map<String, String> details) {
        return new ProviderPluginDiagnostic(Severity.ERROR, code, message, details);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }
}
