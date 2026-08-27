package com.morpheus.integration.mcp;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative redaction for diagnostics emitted by external MCP peers. */
final class McpDiagnosticRedactor {
    private static final String REDACTED = "<redacted>";
    private static final Pattern JSON_OR_NAMED_SECRET = Pattern.compile(
            "(?i)([\\\"']?(authorization|token|password|secret|api[_-]?key|credential)[\\\"']?\\s*[=:]\\s*[\\\"']?)([^\\\"'\\s,;&}\\]]+)");
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(authorization\\s*[=:]\\s*)(?:[a-z][a-z0-9+._-]*\\s+)?([^\\s,;&]+)");
    private static final Pattern AUTH_SCHEME = Pattern.compile(
            "(?i)\\b(bearer|basic)\\s+([^\\s,;&]+)");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(token|password|secret|api[_-]?key|credential)\\s*[=:]\\s*([^\\s,;&]+)");

    private McpDiagnosticRedactor() {
    }

    static String redact(String diagnostic) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        String redacted = replaceJsonOrNamedSecrets(diagnostic);
        redacted = replace(AUTHORIZATION, redacted, true);
        redacted = replace(AUTH_SCHEME, redacted, true);
        return replace(SECRET, redacted, false);
    }

    static String describe(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        String type = failure.getClass().getSimpleName();
        String message = failure.getMessage();
        if (message == null || message.isBlank()) return type;
        return type + ": " + redact(message);
    }

    private static String replaceJsonOrNamedSecrets(String value) {
        Matcher matcher = JSON_OR_NAMED_SECRET.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + REDACTED));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String replace(Pattern pattern, String value, boolean preserveSeparator) {
        Matcher matcher = pattern.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String prefix;
            if (pattern == AUTH_SCHEME) {
                prefix = matcher.group(1) + " ";
            } else if (preserveSeparator) {
                prefix = matcher.group(1);
            } else {
                prefix = matcher.group(1) + "=";
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(prefix + REDACTED));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
