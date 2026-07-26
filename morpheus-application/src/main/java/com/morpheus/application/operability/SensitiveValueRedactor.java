package com.morpheus.application.operability;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative redaction for local operational diagnostics. */
public final class SensitiveValueRedactor {
    public static final String REDACTED = "<redacted>";
    public static final String PATH_REDACTED = "<path-redacted>";

    private static final Pattern INLINE_SECRET = Pattern.compile(
            "(?i)(token|password|secret|api[_-]?key|authorization|credential)\\s*[=:]\\s*([^\\s,;&]+)");

    public Map<String, String> redact(Map<String, String> attributes) {
        Objects.requireNonNull(attributes, "attributes");
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        attributes.forEach((key, value) -> result.put(key, redact(key, value)));
        return Collections.unmodifiableMap(result);
    }

    public String redact(String key, String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        String normalizedKey = key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        if (isSecretKey(normalizedKey)) {
            return REDACTED;
        }
        if (isPathKey(normalizedKey) && isAbsolutePath(value)) {
            return PATH_REDACTED;
        }
        String redacted = redactInlineSecrets(value);
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            redacted = redacted.replace(home, "<home>");
            redacted = redacted.replace(home.replace('\\', '/'), "<home>");
        }
        return redacted;
    }

    private boolean isSecretKey(String key) {
        return key.contains("password")
                || key.contains("secret")
                || key.contains("token")
                || key.contains("apikey")
                || key.contains("authorization")
                || key.contains("credential");
    }

    private boolean isPathKey(String key) {
        return key.endsWith("path")
                || key.endsWith("file")
                || key.contains("workspace")
                || key.contains("database")
                || key.endsWith("directory")
                || key.endsWith("root")
                || key.contains("home");
    }

    private boolean isAbsolutePath(String value) {
        try {
            return Path.of(value).isAbsolute();
        } catch (InvalidPathException ignored) {
            return false;
        }
    }

    private String redactInlineSecrets(String value) {
        Matcher matcher = INLINE_SECRET.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1) + "=" + REDACTED));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
