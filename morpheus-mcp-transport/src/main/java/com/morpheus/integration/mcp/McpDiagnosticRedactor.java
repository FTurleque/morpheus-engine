package com.morpheus.integration.mcp;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative redaction for diagnostics emitted by external MCP peers. */
final class McpDiagnosticRedactor {
    private static final String REDACTED = "<redacted>";
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
        String redacted = replace(AUTHORIZATION, diagnostic, true);
        redacted = replace(AUTH_SCHEME, redacted, true);
        return replace(SECRET, redacted, false);
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
